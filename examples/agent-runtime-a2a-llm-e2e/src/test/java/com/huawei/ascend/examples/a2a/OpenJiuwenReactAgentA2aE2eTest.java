package com.huawei.ascend.examples.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.huawei.ascend.client.A2aEvents;
import com.huawei.ascend.client.A2aResponse;
import com.huawei.ascend.client.AscendA2aClient;
import com.huawei.ascend.client.SendSpec;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@Tag("e2e")
@ResourceLock("real-llm")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = OpenJiuwenA2aAgentRuntimeApplication.class,
        properties = "sample.a2a.agent=openjiuwen")
class OpenJiuwenReactAgentA2aE2eTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(45);
    private static final String AGENT_ID = OpenJiuwenReactAgentConfiguration.AGENT_ID;

    @LocalServerPort
    private int port;

    @Test
    void a2aClientCanStreamOpenJiuwenReactAgentThroughAgentRuntimeOnly() throws Exception {
        try (AscendA2aClient client = AscendA2aClient.builder()
                .baseUrl("http://localhost:" + port)
                .timeout(TIMEOUT)
                .build()) {
            AgentCard agentCard = client.agentCard();
            assertThat(agentCard.name()).isEqualTo(AGENT_ID);
            assertThat(agentCard.description()).contains("openJiuwen ReAct agent");
            assertThat(agentCard.capabilities().streaming()).isTrue();
            assertThat(agentCard.supportedInterfaces())
                    .extracting(AgentInterface::protocolBinding)
                    .contains(TransportProtocol.JSONRPC.asString());

            assumeTrue(hasText(System.getenv("SAA_SAMPLE_LLM_API_KEY")),
                    "SAA_SAMPLE_LLM_API_KEY not set; skipping real openJiuwen ReAct agent E2E sample");

            String sessionId = "session-" + UUID.randomUUID();
            A2aResponse response = client.streamText(
                    SendSpec.of(AGENT_ID, sessionId, "sample-user", "ping"));

            assertThat(response.events()).isNotEmpty();
            assertThat(response.events())
                    .anySatisfy(event -> assertThat(A2aEvents.isTerminal(event)).isTrue());
            assertThat(normalizeAnswer(response.text())).isEqualTo("pong");
        }
    }

    private static String normalizeAnswer(String answer) {
        return answer.strip()
                .toLowerCase(Locale.ROOT)
                .replaceFirst("[\\p{Punct}\\s]+$", "");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
