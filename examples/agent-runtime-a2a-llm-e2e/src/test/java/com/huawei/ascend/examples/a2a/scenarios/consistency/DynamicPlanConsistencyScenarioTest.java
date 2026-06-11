package com.huawei.ascend.examples.a2a.scenarios.consistency;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.bus.memory.MemoryEntry;
import com.huawei.ascend.bus.memory.SessionMemoryStore;
import com.huawei.ascend.client.A2aResponse;
import com.huawei.ascend.client.AscendA2aClient;
import com.huawei.ascend.client.SendSpec;
import com.huawei.ascend.runtime.app.LocalA2aRuntimeHost;
import com.huawei.ascend.runtime.app.RunningRuntime;
import com.huawei.ascend.runtime.app.RuntimeApp;
import com.huawei.ascend.runtime.run.Run;
import com.huawei.ascend.runtime.run.RunRepository;
import com.huawei.ascend.runtime.run.RunStatus;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * Scenario S5 — trade-order orchestration: the dynamic-planning
 * transactional-consistency harness. A {@link ScriptedPlanHandler} executes a
 * finance-flavoured order plan whose steps are scripted to fail once, suspend
 * for approval, or revise the remaining plan mid-run; every committed effect
 * lands in an {@link EffectLedger}. Each test drives the plan END TO END
 * through the real wire — booted {@code RuntimeApp}, called via
 * {@code springai-ascend-client} — and asserts the platform's consistency
 * invariants as ledger algebra plus run-DFA history:
 *
 * <ul>
 *   <li><b>I1 idempotent replay</b> — re-sending the same messageId re-executes
 *       nothing and replays the recorded task;</li>
 *   <li><b>I2 failure atomicity + retry</b> — a failed step commits nothing,
 *       the run ends FAILED, and the retry commits exactly the steps the first
 *       attempt had not checkpointed;</li>
 *   <li><b>I3 cancel barrier</b> — cancelling a run suspended on
 *       input-required leaves no effect past the suspension point and lands
 *       the run in CANCELLED;</li>
 *   <li><b>I4 revision coherence</b> — effects of the abandoned plan branch
 *       never appear; session memory equals the committed sequence;</li>
 *   <li><b>I5 concurrent duplicate</b> — two simultaneous sends of one
 *       messageId execute the plan exactly once.</li>
 * </ul>
 *
 * <p>Deterministic and DB-free: scripted handler, in-memory tiers, no LLM; all
 * waits are deadline-bounded polls on observable state, never bare sleeps.
 *
 * <p>{@code @Isolated}: Spring Boot's logging re-initialization resets the
 * JVM-global logback LoggerContext, whose listener list is not thread-safe —
 * booting concurrently with other context-starting tests intermittently
 * crashes in LoggerContext.addListener.
 */
@Isolated
class DynamicPlanConsistencyScenarioTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Duration RUN_DEADLINE = Duration.ofSeconds(10);

    /**
     * No JWT ingress in this boot, so the runtime attributes every request to
     * the configured default tenant — this example module's application.yaml
     * pins agent-runtime.access.a2a.default-tenant-id to sample-tenant.
     */
    private static final String TENANT = "sample-tenant";
    private static final String AGENT = "trade-orchestrator";
    private static final String USER = "trader-1";
    private static final String ORDER = "buy 100 ACME at market";

    @Test
    void i1IdempotentReplayReExecutesNothing() throws Exception {
        EffectLedger ledger = new EffectLedger();
        ScriptedPlanHandler handler = new ScriptedPlanHandler(AGENT, List.of(
                PlanStep.ok("s1", "reserve-funds"),
                PlanStep.ok("s2", "place-order"),
                PlanStep.ok("s3", "confirm-execution")), ledger);

        try (RunningRuntime runtime = boot(handler);
                AscendA2aClient client = newClient(runtime.port())) {
            RunRepository runs = runtime.component(RunRepository.class);

            A2aResponse first = client.sendText(spec("session-i1", "msg-i1"));
            String taskId = taskIdOf(first);
            Run run = awaitRunStatus(runs, taskId, RunStatus.SUCCEEDED);
            assertThat(run.attemptId()).isEqualTo(1);
            assertThat(ledger.stepIds()).containsExactly("s1", "s2", "s3");
            int committedEffects = ledger.count();

            A2aResponse replayed = client.sendText(spec("session-i1", "msg-i1"));

            // The replay returned the recorded task and re-executed NOTHING.
            assertThat(taskIdOf(replayed)).isEqualTo(taskId);
            assertThat(ledger.count()).isEqualTo(committedEffects);
            assertThat(runs.findByTenantAndTask(TENANT, taskId)).hasSize(1);
        }
    }

    @Test
    void i2FailedStepCommitsNothingAndRetrySkipsCheckpointedSteps() throws Exception {
        EffectLedger ledger = new EffectLedger();
        ScriptedPlanHandler handler = new ScriptedPlanHandler(AGENT, List.of(
                PlanStep.ok("s1", "reserve-funds"),
                PlanStep.failOnce("s2", "place-order"),
                PlanStep.ok("s3", "confirm-execution")), ledger);

        try (RunningRuntime runtime = boot(handler);
                AscendA2aClient client = newClient(runtime.port())) {
            RunRepository runs = runtime.component(RunRepository.class);

            A2aResponse first = client.streamText(spec("session-i2", "msg-i2-first"));
            assertThat(sawState(first, TaskState.TASK_STATE_FAILED)).isTrue();
            String firstTask = taskIdOf(first);
            Run failed = awaitRunStatus(runs, firstTask, RunStatus.FAILED);
            assertThat(failed.attemptId()).isEqualTo(1);
            // Failure atomicity: s2 threw before any of its effects committed.
            assertThat(ledger.stepIds()).containsExactly("s1");
            assertThat(ledger.byStep("s1")).singleElement()
                    .satisfies(record -> assertThat(record.runId())
                            .isEqualTo(failed.id().toString()));

            A2aResponse retry = client.streamText(spec("session-i2", "msg-i2-retry"));
            String retryTask = taskIdOf(retry);
            assertThat(retryTask).isNotEqualTo(firstTask);
            Run succeeded = awaitRunStatus(runs, retryTask, RunStatus.SUCCEEDED);

            // The retry committed exactly what attempt 1 had not: the s1
            // checkpoint was honored, s2 and s3 committed exactly once each.
            assertThat(ledger.stepIds()).containsExactly("s1", "s2", "s3");
            assertThat(ledger.byStep("s1")).hasSize(1);
            assertThat(ledger.byStep("s2")).singleElement().satisfies(record -> {
                assertThat(record.attemptId()).isEqualTo(2);
                assertThat(record.runId()).isEqualTo(succeeded.id().toString());
            });
            assertThat(ledger.byStep("s3")).hasSize(1);
            assertThat(ledger.byAttempt(1)).hasSize(1);
            assertThat(ledger.byAttempt(2)).hasSize(2);

            // Session memory equals the committed sequence, newest first — no
            // phantom turn from the failed step execution.
            SessionMemoryStore memory = runtime.component(SessionMemoryStore.class);
            assertThat(memory.window(TENANT, "session-i2", 10))
                    .extracting(MemoryEntry::text)
                    .containsExactly("s3", "s2", "s1");

            // Run-DFA shape on today's wire path: each A2A send opens a new
            // task, and the executor tracks every execution as a fresh Run —
            // so the wire retry is a new Run (attempt 1 of the retry task) and
            // the first Run stays FAILED; the FAILED→RUNNING attempt bump of
            // the run DFA is not reachable from the wire.
            assertThat(succeeded.attemptId()).isEqualTo(1);
            assertThat(runs.findByTenantAndTask(TENANT, firstTask))
                    .extracting(Run::status).containsExactly(RunStatus.FAILED);
        }
    }

    @Test
    void i3CancelDuringSuspensionCommitsNothingPastTheBarrier() throws Exception {
        EffectLedger ledger = new EffectLedger();
        ScriptedPlanHandler handler = new ScriptedPlanHandler(AGENT, List.of(
                PlanStep.ok("s1", "reserve-funds"),
                PlanStep.suspend("s2", "place-order", "approve the order to proceed"),
                PlanStep.ok("s3", "confirm-execution")), ledger);

        try (RunningRuntime runtime = boot(handler);
                AscendA2aClient client = newClient(runtime.port())) {
            RunRepository runs = runtime.component(RunRepository.class);

            A2aResponse response = client.streamText(spec("session-i3", "msg-i3"));
            assertThat(response.awaitingInput()).isTrue();
            assertThat(response.text()).contains("approve the order");
            String taskId = taskIdOf(response);
            awaitRunStatus(runs, taskId, RunStatus.SUSPENDED);
            assertThat(ledger.stepIds()).containsExactly("s1");

            Task cancelled = client.cancelTask(taskId);

            assertThat(cancelled.status().state()).isEqualTo(TaskState.TASK_STATE_CANCELED);
            awaitRunStatus(runs, taskId, RunStatus.CANCELLED);
            // The cancel barrier held: nothing past the suspension point ever
            // committed — not the suspended step, not the steps behind it.
            assertThat(ledger.stepIds()).containsExactly("s1");
        }
    }

    @Test
    void i4PlanRevisionAbandonsTheOriginalRemainderCoherently() throws Exception {
        EffectLedger ledger = new EffectLedger();
        ScriptedPlanHandler handler = new ScriptedPlanHandler(AGENT, List.of(
                PlanStep.ok("s1", "ingest-order"),
                PlanStep.revise("s2", "replan-routing", List.of(
                        PlanStep.ok("s2b", "route-slice-a"),
                        PlanStep.ok("s2c", "route-slice-b"))),
                PlanStep.ok("s3-original", "single-venue-route")), ledger);

        try (RunningRuntime runtime = boot(handler);
                AscendA2aClient client = newClient(runtime.port())) {
            RunRepository runs = runtime.component(RunRepository.class);

            A2aResponse response = client.streamText(spec("session-i4", "msg-i4"));

            awaitRunStatus(runs, taskIdOf(response), RunStatus.SUCCEEDED);
            // Revision coherence: the revised branch committed, the abandoned
            // original remainder NEVER produced an effect.
            assertThat(ledger.stepIds()).containsExactly("s1", "s2", "s2b", "s2c");
            assertThat(ledger.byStep("s3-original")).isEmpty();

            // Memory window equals the committed turn sequence, newest first.
            SessionMemoryStore memory = runtime.component(SessionMemoryStore.class);
            assertThat(memory.window(TENANT, "session-i4", 10))
                    .extracting(MemoryEntry::text)
                    .containsExactly("s2c", "s2b", "s2", "s1");
        }
    }

    @Test
    void i5ConcurrentDuplicateSendExecutesExactlyOnce() throws Exception {
        EffectLedger ledger = new EffectLedger();
        ScriptedPlanHandler handler = new ScriptedPlanHandler(AGENT, List.of(
                PlanStep.ok("s1", "reserve-funds"),
                PlanStep.ok("s2", "place-order"),
                PlanStep.ok("s3", "confirm-execution")), ledger);

        try (RunningRuntime runtime = boot(handler);
                AscendA2aClient client = newClient(runtime.port())) {
            RunRepository runs = runtime.component(RunRepository.class);
            CyclicBarrier released = new CyclicBarrier(2);
            ExecutorService callers = Executors.newFixedThreadPool(2);
            List<Object> outcomes;
            try {
                Future<Object> caller1 = callers.submit(
                        () -> duplicateSend(client, released));
                Future<Object> caller2 = callers.submit(
                        () -> duplicateSend(client, released));
                outcomes = List.of(
                        caller1.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS),
                        caller2.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS));
            } finally {
                callers.shutdownNow();
            }

            // Exactly ONE execution, whatever the interleaving was.
            assertThat(ledger.stepIds()).containsExactly("s1", "s2", "s3");

            List<A2aResponse> successes = outcomes.stream()
                    .filter(A2aResponse.class::isInstance).map(A2aResponse.class::cast).toList();
            List<RuntimeException> rejections = outcomes.stream()
                    .filter(RuntimeException.class::isInstance)
                    .map(RuntimeException.class::cast).toList();
            assertThat(successes).as("at least one caller owns the executed send").isNotEmpty();

            // Every successful caller observed the SAME task, backed by one run.
            List<String> taskIds = successes.stream()
                    .map(DynamicPlanConsistencyScenarioTest::taskIdOf).distinct().toList();
            assertThat(taskIds).hasSize(1);
            Run run = awaitRunStatus(runs, taskIds.get(0), RunStatus.SUCCEEDED);
            assertThat(run.attemptId()).isEqualTo(1);
            assertThat(runs.findByTenantAndTask(TENANT, taskIds.get(0))).hasSize(1);

            // A losing caller, if any, saw the in-flight duplicate rejection.
            for (RuntimeException rejection : rejections) {
                assertThat(messageChain(rejection)).containsIgnoringCase("duplicate");
            }
        }
    }

    /** Barrier-released duplicate send; returns the response or the rejection it observed. */
    private static Object duplicateSend(AscendA2aClient client, CyclicBarrier released)
            throws Exception {
        released.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        try {
            return client.sendText(spec("session-i5", "msg-i5"));
        } catch (RuntimeException e) {
            return e;
        }
    }

    // ── harness plumbing ──

    private static RunningRuntime boot(ScriptedPlanHandler handler) {
        return RuntimeApp.create(handler).run(LocalA2aRuntimeHost.port(0));
    }

    private static AscendA2aClient newClient(int port) {
        return AscendA2aClient.builder()
                .baseUrl("http://localhost:" + port)
                .timeout(TIMEOUT)
                .build();
    }

    private static SendSpec spec(String sessionId, String messageId) {
        return new SendSpec(AGENT, sessionId, USER, ORDER, messageId, null);
    }

    /**
     * Deadline-bounded poll until the newest Run of the task reaches the
     * expected DFA state; returns that Run. The poll absorbs the gap between
     * the wire response and the executor's best-effort run transition.
     */
    private static Run awaitRunStatus(RunRepository runs, String taskId, RunStatus expected)
            throws InterruptedException {
        long deadline = System.nanoTime() + RUN_DEADLINE.toNanos();
        Run newest = null;
        while (System.nanoTime() < deadline) {
            List<Run> history = runs.findByTenantAndTask(TENANT, taskId);
            newest = history.isEmpty() ? null : history.get(history.size() - 1);
            if (newest != null && newest.status() == expected) {
                return newest;
            }
            Thread.sleep(25);
        }
        throw new AssertionError(
                "run for task " + taskId + " did not reach " + expected + "; newest=" + newest);
    }

    private static String taskIdOf(A2aResponse response) {
        return response.events().stream()
                .map(DynamicPlanConsistencyScenarioTest::taskIdOf)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no task id in events: " + response.events()));
    }

    private static String taskIdOf(StreamingEventKind event) {
        if (event instanceof Task task) {
            return task.id();
        }
        if (event instanceof TaskStatusUpdateEvent statusEvent) {
            return statusEvent.taskId();
        }
        if (event instanceof TaskArtifactUpdateEvent artifactEvent) {
            return artifactEvent.taskId();
        }
        return null;
    }

    private static boolean sawState(A2aResponse response, TaskState state) {
        return response.events().stream().anyMatch(event ->
                (event instanceof TaskStatusUpdateEvent statusEvent
                        && statusEvent.status() != null
                        && statusEvent.status().state() == state)
                || (event instanceof Task task
                        && task.status() != null
                        && task.status().state() == state));
    }

    private static String messageChain(Throwable error) {
        StringBuilder chain = new StringBuilder();
        for (Throwable t = error; t != null; t = t.getCause()) {
            chain.append(t.getMessage()).append(" | ");
        }
        return chain.toString();
    }
}
