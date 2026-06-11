package com.huawei.ascend.examples.a2a.scenarios;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.agentsdk.factory.AgentHandlerFactory;
import com.huawei.ascend.agentsdk.spec.AgentSpec;
import com.huawei.ascend.agentsdk.spec.tool.HttpExecutionHandle;
import com.huawei.ascend.agentsdk.spec.tool.HttpToolResolver;
import com.huawei.ascend.agentsdk.spec.tool.ToolSpec;
import com.huawei.ascend.agentsdk.spec.tool.WrappableTool;
import com.huawei.ascend.agentsdk.spec.yaml.AgentYamlLoader;
import com.huawei.ascend.client.A2aResponse;
import com.huawei.ascend.client.AscendA2aClient;
import com.huawei.ascend.client.SendSpec;
import com.huawei.ascend.runtime.app.LocalA2aRuntimeHost;
import com.huawei.ascend.runtime.app.RunningRuntime;
import com.huawei.ascend.runtime.app.RuntimeApp;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * FX rate desk — a single YAML-defined agent whose only tool is an
 * {@code http:} ref against the bank's spot-rate service, driven end to end
 * over the real wire: {@code ascend-agent/v1} YAML →
 * {@link AgentHandlerFactory#fromYaml} → booted {@link RuntimeApp} → A2A call
 * through the supported client SDK → HTTP tool call against a test-local stub
 * rate service → the stub's scripted rate in the A2A answer.
 *
 * <p><b>Why {@code executeMode: sdk-proof}:</b> the openJiuwen react execute
 * path ({@code Runner.runAgent}) needs a real LLM to choose tools, which a
 * deterministic test must not depend on. The platform's proof execute mode
 * runs the SAME resolved tool chain (the same {@code HttpToolExecutor} behind
 * the same {@code LocalFunction} closures) without a model, so everything this
 * scenario is about — YAML parsing, tool resolution, real HTTP tool execution,
 * runtime boot, A2A streaming — is exercised for real; only the model's tool
 * CHOICE is bypassed. The model-driven path is covered separately by the
 * real-LLM {@code OpenJiuwenReactAgentA2aE2eTest}.
 *
 * <p>{@code @Isolated}: Spring Boot's logging re-initialization resets the
 * JVM-global logback LoggerContext, whose listener list is not thread-safe —
 * booting concurrently with other context-starting tests intermittently
 * crashes in LoggerContext.addListener.
 */
@Isolated
class FxRateDeskScenarioTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Duration STUB_CALL_DEADLINE = Duration.ofSeconds(10);
    private static final String PAIR = "USD/CNY";
    private static final String RATE = "7.1845";
    private static final String QUESTION = "What is today's USD/CNY spot rate?";

    @Test
    void yamlHttpToolResolvesToTheRateEndpoint() {
        String suffix = ScenarioYamls.uniqueSuffix();
        Path yaml = ScenarioYamls.materialize("fx-rate-desk.yaml", Map.of(
                "@SUFFIX@", suffix,
                "@STUB_BASE_URL@", "http://127.0.0.1:65500"));

        AgentSpec spec = new AgentYamlLoader().load(yaml);

        assertThat(spec.frameworkType()).isEqualTo("openjiuwen");
        assertThat(spec.agentType()).isEqualTo("react");
        assertThat(spec.toolSpecs()).hasSize(1);
        ToolSpec toolSpec = spec.toolSpecs().get(0);
        assertThat(toolSpec.name()).isEqualTo("fxSpotRate" + suffix);
        assertThat(toolSpec.ref().scheme()).isEqualTo("http");

        WrappableTool resolved = (WrappableTool) new HttpToolResolver().resolve(toolSpec);
        HttpExecutionHandle handle = (HttpExecutionHandle) resolved.executionHandle();
        assertThat(handle.url()).isEqualTo(URI.create("http://127.0.0.1:65500/rates"));
        assertThat(handle.method()).isEqualTo("GET");
        assertThat(handle.timeout()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void rateAnswerTravelsFromYamlAgentThroughTheStubToolToTheA2aWire() throws Exception {
        try (StubExchangeRateService stub = StubExchangeRateService.start(PAIR, RATE)) {
            String suffix = ScenarioYamls.uniqueSuffix();
            Path yaml = ScenarioYamls.materialize("fx-rate-desk.yaml", Map.of(
                    "@SUFFIX@", suffix,
                    "@STUB_BASE_URL@", stub.baseUrl()));
            AgentRuntimeHandler handler = AgentHandlerFactory.fromYaml(yaml);
            assertThat(handler.agentId()).isEqualTo("fxRateDesk" + suffix);

            try (RunningRuntime runtime = RuntimeApp.create(handler).run(LocalA2aRuntimeHost.port(0));
                    AscendA2aClient client = newClient(runtime.port())) {
                A2aResponse response = client.streamText(SendSpec.of(
                        "fxRateDesk" + suffix, "session-fx-desk", "sample-user", QUESTION));

                // The answer on the wire carries the stub's scripted rate: the
                // resolved http tool was really executed inside the run.
                assertThat(response.text())
                        .contains("fxSpotRate" + suffix)
                        .contains(RATE);
            }

            // The stub saw exactly one GET on /rates whose inputs carried the question.
            StubExchangeRateService.RecordedCall call = stub.awaitFirstCall(STUB_CALL_DEADLINE);
            assertThat(call.method()).isEqualTo("GET");
            assertThat(call.uri().getPath()).isEqualTo("/rates");
            String query = URLDecoder.decode(call.uri().getRawQuery(), StandardCharsets.UTF_8);
            assertThat(query).startsWith("input=").contains("USD/CNY spot rate");
            assertThat(stub.calls()).hasSize(1);
        }
    }

    private static AscendA2aClient newClient(int port) {
        return AscendA2aClient.builder()
                .baseUrl("http://localhost:" + port)
                .timeout(TIMEOUT)
                .build();
    }
}
