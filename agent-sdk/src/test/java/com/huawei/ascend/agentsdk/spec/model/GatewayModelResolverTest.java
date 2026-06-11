package com.huawei.ascend.agentsdk.spec.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.huawei.ascend.agentsdk.support.ValidationException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GatewayModelResolverTest {

    @Test
    void resolvesAliasToGatewayPointingModelSpec() {
        GatewayModelResolver resolver = new GatewayModelResolver("http://gateway:8080", "saa-minted-token");

        ModelSpec spec = resolver.resolve("retail-llm", true, Map.of("X-Custom", "v"));

        assertThat(spec.provider()).isEqualTo("openai-compatible");
        assertThat(spec.name()).isEqualTo("retail-llm");
        assertThat(spec.baseUrl()).isEqualTo("http://gateway:8080/v1");
        assertThat(spec.apiKey()).isEqualTo("saa-minted-token");
        assertThat(spec.sslVerify()).isTrue();
        assertThat(spec.headers()).containsEntry("X-Custom", "v");
    }

    @Test
    void acceptsGatewayBaseUrlWithOrWithoutVersionSegment() {
        assertThat(new GatewayModelResolver("http://gateway:8080/v1", "t")
                .resolve("a", true, Map.of()).baseUrl()).isEqualTo("http://gateway:8080/v1");
        assertThat(new GatewayModelResolver("http://gateway:8080/v1/", "t")
                .resolve("a", true, Map.of()).baseUrl()).isEqualTo("http://gateway:8080/v1");
        assertThat(new GatewayModelResolver("http://gateway:8080/", "t")
                .resolve("a", true, Map.of()).baseUrl()).isEqualTo("http://gateway:8080/v1");
    }

    @Test
    void missingGatewayBaseUrlIsRejectedNamingTheEnvVar() {
        assertThatThrownBy(() -> new GatewayModelResolver(null, "t").resolve("a", true, Map.of()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("model.alias")
                .hasMessageContaining("SAA_GATEWAY_BASE_URL");
    }

    @Test
    void missingMintedTokenIsRejectedNamingTheEnvVar() {
        assertThatThrownBy(() -> new GatewayModelResolver("http://gateway:8080", " ").resolve("a", true, Map.of()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("model.alias")
                .hasMessageContaining("SAA_GATEWAY_TOKEN");
    }

    @Test
    void unsetValuesFallBackToTheGatewayEnvVars() {
        Map<String, String> environment = Map.of(
                GatewayModelResolver.GATEWAY_BASE_URL_ENV, "http://env-gateway:9090",
                GatewayModelResolver.GATEWAY_TOKEN_ENV, "env-token");
        GatewayModelResolver resolver =
                GatewayModelResolver.withEnvironmentFallback(null, null, environment::get);

        ModelSpec spec = resolver.resolve("alias-model", true, Map.of());

        assertThat(spec.baseUrl()).isEqualTo("http://env-gateway:9090/v1");
        assertThat(spec.apiKey()).isEqualTo("env-token");
    }

    @Test
    void programmaticValuesWinOverTheEnvVars() {
        Map<String, String> environment = Map.of(
                GatewayModelResolver.GATEWAY_BASE_URL_ENV, "http://env-gateway:9090",
                GatewayModelResolver.GATEWAY_TOKEN_ENV, "env-token");
        GatewayModelResolver resolver = GatewayModelResolver.withEnvironmentFallback(
                "http://builder-gateway:8080", "builder-token", environment::get);

        ModelSpec spec = resolver.resolve("alias-model", true, Map.of());

        assertThat(spec.baseUrl()).isEqualTo("http://builder-gateway:8080/v1");
        assertThat(spec.apiKey()).isEqualTo("builder-token");
    }
}
