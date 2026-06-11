package com.huawei.ascend.examples.a2a.scenarios.consistency;

import com.huawei.ascend.bus.memory.MemoryEntry;
import com.huawei.ascend.bus.memory.SessionMemoryStore;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.slf4j.MDC;

/**
 * Deterministic dynamic-planning agent: executes a declared {@link PlanStep}
 * list where any step can be scripted to fail once, suspend for caller input,
 * or revise the remaining plan mid-run (the dynamic part) — no LLM, no
 * randomness, no time dependence.
 *
 * <p>Committing a step means, atomically from the plan's point of view:
 * one {@link EffectLedger} record, one session-memory turn, and the step id
 * added to the checkpoint. The checkpoint rides the agent state via
 * {@link AgentExecutionContext#replaceAgentState} (the platform's checkpoint
 * seam) keyed by {@link AgentExecutionContext#getAgentStateKey()}; the handler
 * also keeps the same map across executions because the A2A executor builds
 * each execution context with no prior agent state — a retry attempt
 * therefore skips already-committed steps instead of re-running them.
 *
 * <p>Failure atomicity: a {@code FAIL_ONCE} step throws BEFORE any of its
 * effects commit, and a {@code SUSPEND} step pauses before its effect — the
 * step needs the caller's answer to do its work — so neither leaves partial
 * state behind.
 */
public final class ScriptedPlanHandler implements AgentRuntimeHandler {

    private final String agentId;
    private final List<PlanStep> plan;
    private final EffectLedger ledger;
    private final Map<String, Set<String>> committedByStateKey = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> attemptsByStateKey = new ConcurrentHashMap<>();
    private final Set<String> firedFailures = ConcurrentHashMap.newKeySet();

    public ScriptedPlanHandler(String agentId, List<PlanStep> plan, EffectLedger ledger) {
        this.agentId = agentId;
        this.plan = List.copyOf(plan);
        this.ledger = ledger;
    }

    @Override
    public String agentId() {
        return agentId;
    }

    @Override
    public boolean isHealthy() {
        return true;
    }

    @Override
    public Stream<?> execute(AgentExecutionContext context) {
        String stateKey = context.getAgentStateKey();
        int attempt = attemptsByStateKey
                .computeIfAbsent(stateKey, key -> new AtomicInteger()).incrementAndGet();
        Set<String> committed = committedByStateKey.computeIfAbsent(stateKey,
                key -> Collections.synchronizedSet(new LinkedHashSet<>()));

        Deque<PlanStep> remaining = new ArrayDeque<>(plan);
        List<String> executed = new ArrayList<>();
        while (!remaining.isEmpty()) {
            PlanStep step = remaining.pollFirst();
            if (committed.contains(step.stepId())) {
                continue; // checkpointed by an earlier attempt — never re-commit
            }
            if (step.kind() == PlanStep.Kind.FAIL_ONCE && firedFailures.add(step.stepId())) {
                throw new IllegalStateException("scripted failure at step " + step.stepId());
            }
            if (step.kind() == PlanStep.Kind.SUSPEND) {
                return Stream.of(AgentExecutionResult.interrupted(step.prompt()));
            }
            commit(context, committed, attempt, step);
            executed.add(step.stepId());
            if (step.kind() == PlanStep.Kind.REVISE) {
                remaining = new ArrayDeque<>(step.revisedRemainder());
            }
        }
        return Stream.of(AgentExecutionResult.completed(
                "plan-complete:" + String.join(",", executed)));
    }

    @Override
    public StreamAdapter resultAdapter() {
        return raw -> raw.map(AgentExecutionResult.class::cast);
    }

    private void commit(AgentExecutionContext context, Set<String> committed,
            int attempt, PlanStep step) {
        ledger.append(new EffectLedger.EffectRecord(
                MDC.get("runId"), attempt, step.stepId(), step.effect(), payloadHash(step)));
        SessionMemoryStore memory = context.getSessionMemory().orElseThrow();
        memory.append(context.getScope().tenantId(), context.getScope().sessionId(),
                new MemoryEntry("step", step.stepId(), Instant.now(),
                        Map.of("effect", step.effect())));
        committed.add(step.stepId());
        List<String> checkpoint;
        synchronized (committed) {
            checkpoint = List.copyOf(committed);
        }
        context.replaceAgentState(Map.of("completedSteps", checkpoint));
    }

    private static String payloadHash(PlanStep step) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    (step.stepId() + "|" + step.effect()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
