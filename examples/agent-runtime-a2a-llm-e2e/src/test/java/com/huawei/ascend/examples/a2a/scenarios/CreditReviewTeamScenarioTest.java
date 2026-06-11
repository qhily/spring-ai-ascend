package com.huawei.ascend.examples.a2a.scenarios;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.bus.knowledge.CompositeKnowledgeSource;
import com.huawei.ascend.bus.knowledge.InMemoryKnowledgeSource;
import com.huawei.ascend.bus.knowledge.KnowledgeFragment;
import com.huawei.ascend.bus.knowledge.KnowledgeQuery;
import com.huawei.ascend.bus.knowledge.KnowledgeRegistry;
import com.huawei.ascend.bus.messaging.AgentMessage;
import com.huawei.ascend.bus.messaging.AgentMessageBus;
import com.huawei.ascend.client.A2aResponse;
import com.huawei.ascend.client.AscendA2aClient;
import com.huawei.ascend.client.ClientAuth;
import com.huawei.ascend.client.SendSpec;
import com.huawei.ascend.runtime.app.LocalA2aRuntimeHost;
import com.huawei.ascend.runtime.app.RunningRuntime;
import com.huawei.ascend.runtime.app.RuntimeApp;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.a2a.Messages;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * Scenario: credit-review team — three logical agents on ONE booted
 * {@link RuntimeApp}, cooperating over the in-process bus (no LLM, no
 * external infrastructure). The A2A-exposed "planner" consults the tenant's
 * seeded credit policy through the knowledge seam, then drives a pipeline:
 * planner → risk-checker (deterministic debt-to-income rule) → drafter
 * (decision letter) → back to the planner, one bus topic per hop, the
 * planner's correlationId carried unchanged through all three hops.
 *
 * <p>Tenant attribution travels the real wire: the client sends
 * {@code X-Tenant-Id: bank-7}, the ingress publishes it through the
 * call-context state, and every bus message rides that tenant. Decoy messages
 * published on the same topic names under a different tenant are never
 * delivered to the team — topic scoping is (tenant, topic), not topic-only —
 * and their payloads are poisoned (zero debt → APPROVED, "LEAKED" letter) so
 * any isolation failure flips the visible decision instead of passing
 * silently.
 *
 * <p>All waits are deadline-bounded futures — no sleeps: the planner awaits
 * the pipeline's final message inside its execution, so the A2A response
 * returning is the synchronization point for every assertion.
 *
 * <p>{@code @Isolated}: Spring Boot's logging re-initialization resets the
 * JVM-global logback LoggerContext, whose listener list is not thread-safe —
 * booting concurrently with other context-starting tests intermittently
 * crashes in LoggerContext.addListener.
 */
@Isolated
class CreditReviewTeamScenarioTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private static final String TENANT = "bank-7";
    private static final String OTHER_TENANT = "bank-9";
    private static final String SESSION = "session-credit-review";
    private static final String APPLICATION_ID = "X-2041";
    private static final String QUESTION =
            "Please review credit application " + APPLICATION_ID + " for tenant bank-7.";
    private static final String POLICY = "Credit policy: the maximum allowed debt-to-income ratio is 0.40;"
            + " applications above this ratio must be declined.";

    @Test
    void teamDeclinesOverLimitApplicationWithPolicyDecisionAndDraftedLetter() throws Exception {
        InMemoryKnowledgeSource policySource = new InMemoryKnowledgeSource("credit-policy");
        policySource.seed(TENANT, POLICY);
        PlannerHandler planner = new PlannerHandler(policySource);

        try (RunningRuntime runtime = RuntimeApp.create(planner).run(LocalA2aRuntimeHost.port(0));
                AscendA2aClient client = newClient(runtime.port())) {
            A2aResponse response = client.streamText(
                    SendSpec.of("credit-planner", SESSION, "credit-officer", QUESTION));

            // The applicant's 2600/5000 = 0.52 ratio breaches the seeded 0.40
            // policy ceiling, so the policy-driven decision is DECLINED and the
            // final answer carries the drafter's letter.
            assertThat(planner.tenantSeen.get()).isEqualTo(TENANT);
            assertThat(response.text())
                    .contains("DECLINED")
                    .contains("Dear applicant")
                    .contains("0.52")
                    .contains("0.40")
                    .doesNotContain("LEAKED");

            assertCorrelationChainsThroughAllThreeHops(planner);
            assertEachSubscriberSawOnlyItsTenant(planner);
        }
    }

    /** One message per hop, every hop carrying the planner's minted correlationId unchanged. */
    private static void assertCorrelationChainsThroughAllThreeHops(PlannerHandler planner) {
        String correlationId = planner.mintedCorrelationId.get();
        assertThat(correlationId).isNotBlank();

        assertThat(planner.riskCheckerInbox).hasSize(1);
        assertThat(planner.riskCheckerInbox.get(0).fromAgentId()).isEqualTo("credit-planner");
        assertThat(planner.riskCheckerInbox.get(0).correlationId()).isEqualTo(correlationId);

        assertThat(planner.drafterInbox).hasSize(1);
        assertThat(planner.drafterInbox.get(0).fromAgentId()).isEqualTo("risk-checker");
        assertThat(planner.drafterInbox.get(0).correlationId()).isEqualTo(correlationId);

        assertThat(planner.plannerInbox).hasSize(1);
        assertThat(planner.plannerInbox.get(0).fromAgentId()).isEqualTo("drafter");
        assertThat(planner.plannerInbox.get(0).correlationId()).isEqualTo(correlationId);
    }

    /** Despite same-named decoy topics under another tenant, every delivery is bank-7's. */
    private static void assertEachSubscriberSawOnlyItsTenant(PlannerHandler planner) {
        for (List<AgentMessage> inbox
                : List.of(planner.riskCheckerInbox, planner.drafterInbox, planner.plannerInbox)) {
            assertThat(inbox).allSatisfy(message -> {
                assertThat(message.tenantId()).isEqualTo(TENANT);
                assertThat(message.fromAgentId()).isNotEqualTo("intruder");
            });
        }
    }

    private static AscendA2aClient newClient(int port) {
        return AscendA2aClient.builder()
                .baseUrl("http://localhost:" + port)
                // The example boot has no JWT ingress, so the bearer value is
                // inert; the X-Tenant-Id header is what attributes the run.
                .auth(ClientAuth.jwtBearer(() -> "local-test-token", TENANT))
                .timeout(TIMEOUT)
                .build();
    }

    /**
     * The A2A-exposed "credit-planner": looks the policy ceiling up through
     * the knowledge seam, attaches its two teammates as bus subscribers, then
     * starts the pipeline and waits for the drafter's final message.
     */
    private static final class PlannerHandler implements AgentRuntimeHandler {

        static final String RISK_TOPIC = "credit.review.risk-check";
        static final String DRAFT_TOPIC = "credit.review.draft";
        static final String DECISION_TOPIC = "credit.review.decision";

        private static final Duration PIPELINE_DEADLINE = Duration.ofSeconds(10);
        private static final Pattern MAX_RATIO = Pattern.compile("ratio is ([0-9]+\\.[0-9]+)");
        private static final double MONTHLY_DEBT = 2600.0;
        private static final double MONTHLY_INCOME = 5000.0;

        final AtomicReference<String> tenantSeen = new AtomicReference<>();
        final AtomicReference<String> mintedCorrelationId = new AtomicReference<>();
        final List<AgentMessage> riskCheckerInbox = new CopyOnWriteArrayList<>();
        final List<AgentMessage> drafterInbox = new CopyOnWriteArrayList<>();
        final List<AgentMessage> plannerInbox = new CopyOnWriteArrayList<>();

        private final InMemoryKnowledgeSource policySource;
        private final AtomicBoolean teamAttached = new AtomicBoolean();
        private final CompletableFuture<AgentMessage> finalDecision = new CompletableFuture<>();

        PlannerHandler(InMemoryKnowledgeSource policySource) {
            this.policySource = policySource;
        }

        @Override
        public String agentId() {
            return "credit-planner";
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public Stream<?> execute(AgentExecutionContext context) {
            String tenantId = context.getScope().tenantId();
            tenantSeen.set(tenantId);
            AgentMessageBus bus = context.getMessageBus().orElseThrow();
            String question = Messages.text(context.getMessages().get(0));

            double maxDebtToIncome = lookUpPolicyCeiling(context, tenantId, question);
            attachTeam(bus, tenantId);
            publishPoisonedDecoysUnderOtherTenant(bus);

            String correlationId = UUID.randomUUID().toString();
            mintedCorrelationId.set(correlationId);
            bus.publish(new AgentMessage(UUID.randomUUID().toString(), tenantId, RISK_TOPIC,
                    agentId(), correlationId, null,
                    Map.of("applicationId", APPLICATION_ID,
                            "monthlyDebt", MONTHLY_DEBT,
                            "monthlyIncome", MONTHLY_INCOME,
                            "maxDebtToIncome", maxDebtToIncome),
                    Instant.now()));

            AgentMessage decision = awaitFinalDecision();
            String answer = "Credit review decision for application " + APPLICATION_ID + ": "
                    + decision.payload().get("decision") + ". " + decision.payload().get("letter");
            return Stream.of(answer);
        }

        /** The policy ceiling comes from the tenant's knowledge sources, not from code constants. */
        private double lookUpPolicyCeiling(AgentExecutionContext context, String tenantId, String question) {
            KnowledgeRegistry knowledge = context.getKnowledge().orElseThrow();
            if (!knowledge.sources(tenantId).containsKey(policySource.sourceId())) {
                knowledge.register(tenantId, policySource.sourceId(), policySource);
            }
            List<KnowledgeFragment> fragments = new CompositeKnowledgeSource(knowledge)
                    .retrieve(new KnowledgeQuery(tenantId, question, 1, Map.of()));
            if (fragments.isEmpty()) {
                throw new IllegalStateException("no credit policy retrieved for: " + question);
            }
            Matcher matcher = MAX_RATIO.matcher(fragments.get(0).content());
            if (!matcher.find()) {
                throw new IllegalStateException("policy carries no ratio ceiling: "
                        + fragments.get(0).content());
            }
            return Double.parseDouble(matcher.group(1));
        }

        /**
         * The two teammates are what they are on this plane: co-hosted bus
         * subscribers (the A2A ingress is single-handler by design). Both live
         * for the whole runtime; the bus closes them with the context.
         */
        private void attachTeam(AgentMessageBus bus, String tenantId) {
            if (!teamAttached.compareAndSet(false, true)) {
                return;
            }
            bus.subscribe(tenantId, RISK_TOPIC, request -> {
                riskCheckerInbox.add(request);
                bus.publish(riskAssessment(request));
            });
            bus.subscribe(tenantId, DRAFT_TOPIC, assessment -> {
                drafterInbox.add(assessment);
                bus.publish(draftedLetter(assessment));
            });
            bus.subscribe(tenantId, DECISION_TOPIC, decision -> {
                plannerInbox.add(decision);
                finalDecision.complete(decision);
            });
        }

        /** Risk-checker: pure arithmetic on the request payload against the policy ceiling. */
        private static AgentMessage riskAssessment(AgentMessage request) {
            double debt = ((Number) request.payload().get("monthlyDebt")).doubleValue();
            double income = ((Number) request.payload().get("monthlyIncome")).doubleValue();
            double max = ((Number) request.payload().get("maxDebtToIncome")).doubleValue();
            double ratio = debt / income;
            String decision = ratio <= max ? "APPROVED" : "DECLINED";
            return new AgentMessage(UUID.randomUUID().toString(), request.tenantId(), DRAFT_TOPIC,
                    "risk-checker", request.correlationId(), null,
                    Map.of("applicationId", request.payload().get("applicationId"),
                            "decision", decision,
                            "ratio", format(ratio),
                            "maxRatio", format(max)),
                    Instant.now());
        }

        /** Drafter: composes the customer-facing decision letter from the assessment. */
        private static AgentMessage draftedLetter(AgentMessage assessment) {
            Map<String, Object> payload = assessment.payload();
            String letter = "Dear applicant, regarding credit application " + payload.get("applicationId")
                    + ": your application has been " + payload.get("decision")
                    + ". Assessed debt-to-income ratio " + payload.get("ratio")
                    + " against the policy maximum " + payload.get("maxRatio") + ".";
            return new AgentMessage(UUID.randomUUID().toString(), assessment.tenantId(), DECISION_TOPIC,
                    "drafter", assessment.correlationId(), null,
                    Map.of("decision", payload.get("decision"), "letter", letter),
                    Instant.now());
        }

        /**
         * Same topic names, different tenant, poisoned payloads: a zero-debt
         * risk request (would flip the decision to APPROVED) and a "LEAKED"
         * letter. None of it may reach the bank-7 subscribers.
         */
        private void publishPoisonedDecoysUnderOtherTenant(AgentMessageBus bus) {
            String decoyCorrelation = UUID.randomUUID().toString();
            bus.publish(new AgentMessage(UUID.randomUUID().toString(), OTHER_TENANT, RISK_TOPIC,
                    "intruder", decoyCorrelation, null,
                    Map.of("applicationId", APPLICATION_ID,
                            "monthlyDebt", 0.0, "monthlyIncome", MONTHLY_INCOME,
                            "maxDebtToIncome", 1.0),
                    Instant.now()));
            bus.publish(new AgentMessage(UUID.randomUUID().toString(), OTHER_TENANT, DRAFT_TOPIC,
                    "intruder", decoyCorrelation, null,
                    Map.of("applicationId", APPLICATION_ID, "decision", "APPROVED",
                            "ratio", "0.00", "maxRatio", "1.00"),
                    Instant.now()));
            bus.publish(new AgentMessage(UUID.randomUUID().toString(), OTHER_TENANT, DECISION_TOPIC,
                    "intruder", decoyCorrelation, null,
                    Map.of("decision", "APPROVED", "letter", "LEAKED"),
                    Instant.now()));
        }

        private AgentMessage awaitFinalDecision() {
            try {
                return finalDecision.get(PIPELINE_DEADLINE.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted awaiting the team's decision", e);
            } catch (ExecutionException | TimeoutException e) {
                throw new IllegalStateException("the team's decision did not arrive", e);
            }
        }

        private static String format(double value) {
            return String.format(Locale.ROOT, "%.2f", value);
        }

        @Override
        public StreamAdapter resultAdapter() {
            return raw -> raw.map(o -> AgentExecutionResult.completed(String.valueOf(o)));
        }
    }
}
