package com.huawei.ascend.examples.a2a.scenarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.huawei.ascend.bus.memory.BusinessFactEvent;
import com.huawei.ascend.bus.memory.BusinessFactPublisher;
import com.huawei.ascend.bus.memory.MemoryEntry;
import com.huawei.ascend.bus.memory.RecordingBusinessFactPublisher;
import com.huawei.ascend.bus.memory.SessionMemoryStore;
import com.huawei.ascend.client.A2aResponse;
import com.huawei.ascend.client.AscendA2aClient;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * Scenario: wealth-advisor follow-up — single agent + session memory +
 * business-fact emission, end to end over the A2A wire on one booted
 * {@link RuntimeApp} (no LLM, no external infrastructure).
 *
 * <p>Three client turns in ONE session: (1) the customer states a contact
 * preference — the deterministic advisor commits both conversation turns to
 * the platform {@link SessionMemoryStore} AND emits exactly one
 * {@link BusinessFactEvent} through the {@code BusinessFactPublisher}
 * capability; (2) an unrelated market question; (3) "how should you contact
 * me?" — the advisor answers from the memory window, not from the current
 * message (the word "email" appears nowhere in turn 3's request).
 *
 * <p>Proves the ADR-0051 ownership split on the real wire path: working
 * memory (turn window) is platform-held and exactly equals the committed turn
 * sequence; the discovered business fact is emitted toward the C-side, never
 * stored — {@code drain()} observes it exactly once.
 *
 * <p>{@code @Isolated}: Spring Boot's logging re-initialization resets the
 * JVM-global logback LoggerContext, whose listener list is not thread-safe —
 * booting concurrently with other context-starting tests intermittently
 * crashes in LoggerContext.addListener.
 */
@Isolated
class WealthAdvisorMemoryScenarioTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /**
     * No JWT ingress and no X-Tenant-Id header in this boot, so the executor
     * attributes every run to the configured default tenant — this example
     * module's application.yaml pins agent-runtime.access.a2a.default-tenant-id
     * to sample-tenant.
     */
    private static final String TENANT = "sample-tenant";
    private static final String SESSION = "session-wealth-advisor";
    private static final String USER = "customer-301";

    private static final String TURN_1_PREFERENCE = "Please contact me by email going forward.";
    private static final String TURN_2_UNRELATED = "What did the CSI 300 index close at yesterday?";
    private static final String TURN_3_RECALL = "How should you contact me?";

    @Test
    void advisorRecallsPreferenceFromMemoryAndEmitsExactlyOneFact() throws Exception {
        WealthAdvisorHandler advisor = new WealthAdvisorHandler();

        try (RunningRuntime runtime = RuntimeApp.create(advisor).run(LocalA2aRuntimeHost.port(0));
                AscendA2aClient client = newClient(runtime.port())) {

            A2aResponse turn1 = client.streamText(
                    SendSpec.of("wealth-advisor", SESSION, USER, TURN_1_PREFERENCE));
            assertThat(turn1.text()).isEqualTo(WealthAdvisorHandler.PREFERENCE_ACK);

            A2aResponse turn2 = client.streamText(
                    SendSpec.of("wealth-advisor", SESSION, USER, TURN_2_UNRELATED));
            assertThat(turn2.text()).isEqualTo(WealthAdvisorHandler.MARKET_ANSWER);

            A2aResponse turn3 = client.streamText(
                    SendSpec.of("wealth-advisor", SESSION, USER, TURN_3_RECALL));

            // The answer recalls the preference; "email" cannot have come from
            // the current message — turn 3's request never mentions it.
            assertThat(TURN_3_RECALL).doesNotContainIgnoringCase("email");
            assertThat(turn3.text()).contains("email");

            assertMemoryWindowIsExactlyTheCommittedTurns(advisor, turn3.text());
            assertExactlyOnePreferenceFactEmitted(advisor);
        }
    }

    /** The window holds exactly the six committed turn entries, newest first — no phantoms. */
    private static void assertMemoryWindowIsExactlyTheCommittedTurns(
            WealthAdvisorHandler advisor, String recallAnswer) {
        SessionMemoryStore memory = advisor.memorySeen.get();
        assertThat(memory).as("advisor must have received the memory capability").isNotNull();
        assertThat(memory.window(TENANT, SESSION, 50))
                .extracting(MemoryEntry::role, MemoryEntry::text)
                .containsExactly(
                        tuple("assistant", recallAnswer),
                        tuple("user", TURN_3_RECALL),
                        tuple("assistant", WealthAdvisorHandler.MARKET_ANSWER),
                        tuple("user", TURN_2_UNRELATED),
                        tuple("assistant", WealthAdvisorHandler.PREFERENCE_ACK),
                        tuple("user", TURN_1_PREFERENCE));
    }

    /** Exactly one preference fact reached the emission seam, correctly attributed. */
    private static void assertExactlyOnePreferenceFactEmitted(WealthAdvisorHandler advisor) {
        BusinessFactPublisher publisher = advisor.factsSeen.get();
        assertThat(publisher)
                .as("advisor must have received the business-fact capability")
                .isInstanceOf(RecordingBusinessFactPublisher.class);
        List<BusinessFactEvent> facts = ((RecordingBusinessFactPublisher) publisher).drain();
        assertThat(facts).hasSize(1);
        BusinessFactEvent fact = facts.get(0);
        assertThat(fact.tenantId()).isEqualTo(TENANT);
        assertThat(fact.sessionId()).isEqualTo(SESSION);
        assertThat(fact.factType()).isEqualTo("customer-preference");
        assertThat(fact.placeholdersPreserved()).isFalse();
        assertThat(fact.payload()).containsEntry("channel", "email");
    }

    private static AscendA2aClient newClient(int port) {
        return AscendA2aClient.builder()
                .baseUrl("http://localhost:" + port)
                .timeout(TIMEOUT)
                .build();
    }

    /**
     * Deterministic scripted advisor: classifies each turn by its text. A
     * stated contact preference is committed to session memory (tagged with a
     * structured attribute) and emitted as a business fact; the later recall
     * question is answered exclusively from the memory window's tagged entry.
     */
    private static final class WealthAdvisorHandler implements AgentRuntimeHandler {

        static final String PREFERENCE_ACK = "Noted: all future updates will reach you by your preferred channel.";
        static final String MARKET_ANSWER = "The CSI 300 index closed at 4025.61 points yesterday.";

        static final String FACT_TYPE_ATTRIBUTE = "factType";
        static final String CUSTOMER_PREFERENCE = "customer-preference";
        static final String CHANNEL_ATTRIBUTE = "channel";

        final AtomicReference<SessionMemoryStore> memorySeen = new AtomicReference<>();
        final AtomicReference<BusinessFactPublisher> factsSeen = new AtomicReference<>();

        @Override
        public String agentId() {
            return "wealth-advisor";
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public Stream<?> execute(AgentExecutionContext context) {
            String tenantId = context.getScope().tenantId();
            String sessionId = context.getScope().sessionId();
            SessionMemoryStore memory = context.getSessionMemory().orElseThrow();
            BusinessFactPublisher facts = context.getBusinessFacts().orElseThrow();
            memorySeen.set(memory);
            factsSeen.set(facts);

            String question = Messages.text(context.getMessages().get(0));
            String answer = answer(question, tenantId, sessionId, memory, facts);
            memory.append(tenantId, sessionId, new MemoryEntry("assistant", answer, Instant.now(), Map.of()));
            return Stream.of(answer);
        }

        private String answer(String question, String tenantId, String sessionId,
                SessionMemoryStore memory, BusinessFactPublisher facts) {
            String normalized = question.toLowerCase(Locale.ROOT);
            if (normalized.contains("contact me by email")) {
                // Commit the user turn tagged so the later recall can find it,
                // and emit the discovered preference toward the C-side.
                memory.append(tenantId, sessionId, new MemoryEntry("user", question, Instant.now(),
                        Map.of(FACT_TYPE_ATTRIBUTE, CUSTOMER_PREFERENCE, CHANNEL_ATTRIBUTE, "email")));
                facts.publish(new BusinessFactEvent(tenantId, sessionId, null, CUSTOMER_PREFERENCE,
                        Map.of(CHANNEL_ATTRIBUTE, "email"), false, Instant.now()));
                return PREFERENCE_ACK;
            }
            memory.append(tenantId, sessionId, new MemoryEntry("user", question, Instant.now(), Map.of()));
            if (normalized.contains("how should you contact me")) {
                return recallPreference(tenantId, sessionId, memory);
            }
            return MARKET_ANSWER;
        }

        /** Answers from the committed memory window only — never from the current message. */
        private String recallPreference(String tenantId, String sessionId, SessionMemoryStore memory) {
            for (MemoryEntry entry : memory.window(tenantId, sessionId, 50)) {
                if (CUSTOMER_PREFERENCE.equals(entry.attributes().get(FACT_TYPE_ATTRIBUTE))) {
                    return "Per the preference you stated earlier, we will contact you by "
                            + entry.attributes().get(CHANNEL_ATTRIBUTE) + ".";
                }
            }
            throw new IllegalStateException("no recorded contact preference in session memory");
        }

        @Override
        public StreamAdapter resultAdapter() {
            return raw -> raw.map(o -> AgentExecutionResult.completed(String.valueOf(o)));
        }
    }
}
