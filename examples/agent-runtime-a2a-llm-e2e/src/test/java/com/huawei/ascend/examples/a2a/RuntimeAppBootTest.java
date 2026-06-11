package com.huawei.ascend.examples.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.client.A2aResponse;
import com.huawei.ascend.client.AscendA2aClient;
import com.huawei.ascend.client.SendSpec;
import com.huawei.ascend.runtime.app.LocalA2aRuntimeHost;
import com.huawei.ascend.runtime.app.RunningRuntime;
import com.huawei.ascend.runtime.app.RuntimeApp;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;
import org.a2aproject.sdk.spec.AgentCard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * Real boot of the pure-Java {@link RuntimeApp} entry through {@link LocalA2aRuntimeHost} on an
 * ephemeral port with a trivial handler (no LLM, no mocks): the whole five-layer Spring context
 * wires and the A2A access layer serves — verified through the supported client SDK
 * ({@code springai-ascend-client}, itself built on the A2A SDK's {@code A2ACardResolver} +
 * {@code JSONRPCTransport}/{@code sendMessageStreaming}). Lives in the example module: its
 * classpath is DB-free, so the host boots without external infrastructure. (The execution path
 * itself is covered by the real-LLM {@code OpenJiuwenReactAgentA2aE2eTest}.)
 *
 * <p>{@code @Isolated}: Spring Boot's logging re-initialization resets the JVM-global logback
 * LoggerContext, whose listener list is not thread-safe — booting concurrently with other
 * context-starting tests intermittently crashes in LoggerContext.addListener.
 */
@Isolated
class RuntimeAppBootTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Test
    void runtimeAppBootsAndServesA2aThroughLocalHost() throws Exception {
        try (RunningRuntime runtime = RuntimeApp.create(new StubHandler()).run(LocalA2aRuntimeHost.port(0))) {
            assertThat(runtime.port()).isGreaterThan(0);

            try (AscendA2aClient client = newClient(runtime.port())) {
                AgentCard card = client.agentCard();
                assertThat(card.capabilities().streaming()).isTrue();
            }
        }
    }

    @Test
    void runtimeAppStreamsMessageThroughA2aSdkClient() throws Exception {
        try (RunningRuntime runtime = RuntimeApp.create(new StubHandler()).run(LocalA2aRuntimeHost.port(0));
                AscendA2aClient client = newClient(runtime.port())) {
            A2aResponse response = client.streamText(
                    SendSpec.of("smoke-agent", "session-smoke", "sample-user", "ping"));

            assertThat(response.events()).isNotEmpty();
            assertThat(response.text()).contains("ok");
        }
    }

    private static AscendA2aClient newClient(int port) {
        return AscendA2aClient.builder()
                .baseUrl("http://localhost:" + port)
                .timeout(TIMEOUT)
                .build();
    }

    /** Minimal framework-neutral handler: enough to wire the registry; not exercised by this smoke. */
    private static final class StubHandler implements AgentRuntimeHandler {
        @Override
        public String agentId() {
            return "smoke-agent";
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public Stream<?> execute(AgentExecutionContext context) {
            return Stream.of(Map.of("result_type", "answer", "output", "ok"));
        }

        @Override
        public StreamAdapter resultAdapter() {
            return rawResults -> rawResults.map(raw -> AgentExecutionResult.completed("ok"));
        }
    }
}
