package com.huawei.ascend.examples.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;

/**
 * The {@code sample.llm.via-gateway} flip is pure configuration: the nested
 * placeholder chain in application.yaml selects the raw or gateway block for
 * every framework's model settings, and arms the local LLM egress gateway in
 * the same motion. This test resolves the real application.yaml both ways so
 * a broken placeholder chain fails here, not at first boot with the flag on.
 */
class SampleLlmGatewayFlipTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    void defaultPathKeepsDirectUpstreamAndGatewayOff() {
        contextRunner.run(context -> {
            var env = context.getEnvironment();
            assertThat(env.getProperty("sample.llm.path")).isEqualTo("raw");
            assertThat(env.getProperty("sample.openjiuwen.api-base")).isEqualTo("http://localhost:4000/v1");
            assertThat(env.getProperty("sample.openjiuwen.model-name")).isEqualTo("gpt-5.4-mini");
            assertThat(env.getProperty("agent-runtime.llm.gateway.enabled")).isEqualTo("false");
        });
    }

    @Test
    void flipRoutesEveryFrameworkThroughTheGatewayWithTheMintedToken() {
        contextRunner.withPropertyValues("SAA_SAMPLE_LLM_VIA_GATEWAY=true").run(context -> {
            var env = context.getEnvironment();
            assertThat(env.getProperty("sample.llm.path")).isEqualTo("gateway");
            // Both framework configs point at the gateway /v1 surface …
            assertThat(env.getProperty("sample.openjiuwen.api-base")).isEqualTo("http://localhost:8080/v1");
            assertThat(env.getProperty("sample.agentscope.api-base")).isEqualTo("http://localhost:8080/v1");
            // … with the model alias as the model name and the minted token as the credential.
            assertThat(env.getProperty("sample.openjiuwen.model-name")).isEqualTo("sample-llm");
            assertThat(env.getProperty("sample.openjiuwen.api-key")).isEqualTo("saa-sample-minted-token");
            // The same flag arms the local gateway with the alias routing table.
            assertThat(env.getProperty("agent-runtime.llm.gateway.enabled")).isEqualTo("true");
            assertThat(env.getProperty("agent-runtime.llm.gateway.aliases.sample-llm.base-url"))
                    .isEqualTo("http://localhost:4000/v1");
        });
    }
}
