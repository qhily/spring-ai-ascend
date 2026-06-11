package com.huawei.ascend.agentsdk.spec.model;

import com.huawei.ascend.agentsdk.support.ValidationException;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Resolves the alias form of the agent YAML model section against
 * platform-provided LLM gateway settings. The effective spec points the
 * framework's OpenAI-compatible client at the gateway's {@code /v1} surface
 * with the minted scoped token as its credential — raw provider URLs and
 * API keys never enter agent YAML on this path. The token rides the existing
 * {@code apiKey} field, so frameworks need no code change to adopt it.
 */
public final class GatewayModelResolver {

    public static final String GATEWAY_BASE_URL_ENV = "SAA_GATEWAY_BASE_URL";
    public static final String GATEWAY_TOKEN_ENV = "SAA_GATEWAY_TOKEN";

    /** The gateway speaks exactly one dialect; the alias form never overrides it. */
    private static final String GATEWAY_PROVIDER = "openai-compatible";

    private final String gatewayBaseUrl;
    private final String mintedToken;

    public GatewayModelResolver(String gatewayBaseUrl, String mintedToken) {
        this.gatewayBaseUrl = blankToNull(gatewayBaseUrl);
        this.mintedToken = blankToNull(mintedToken);
    }

    /** Programmatically provided values win; unset values fall back to the platform env vars. */
    public static GatewayModelResolver withEnvironmentFallback(String gatewayBaseUrl, String mintedToken) {
        return withEnvironmentFallback(gatewayBaseUrl, mintedToken, System::getenv);
    }

    static GatewayModelResolver withEnvironmentFallback(
            String gatewayBaseUrl, String mintedToken, UnaryOperator<String> environment) {
        return new GatewayModelResolver(
                blankToNull(gatewayBaseUrl) != null ? gatewayBaseUrl : environment.apply(GATEWAY_BASE_URL_ENV),
                blankToNull(mintedToken) != null ? mintedToken : environment.apply(GATEWAY_TOKEN_ENV));
    }

    public ModelSpec resolve(String alias, boolean sslVerify, Map<String, String> headers) {
        if (gatewayBaseUrl == null) {
            throw new ValidationException("model.alias requires the platform gateway base URL: "
                    + "provide it on the AgentHandlerFactory builder or set " + GATEWAY_BASE_URL_ENV);
        }
        if (mintedToken == null) {
            throw new ValidationException("model.alias requires a minted gateway token: "
                    + "provide it on the AgentHandlerFactory builder or set " + GATEWAY_TOKEN_ENV);
        }
        return new ModelSpec(GATEWAY_PROVIDER, alias, versionedBaseUrl(gatewayBaseUrl), mintedToken,
                sslVerify, headers);
    }

    /**
     * OpenAI-compatible clients expect the API root including the version
     * segment; accept the gateway origin with or without it so operators can
     * hand out either shape.
     */
    private static String versionedBaseUrl(String baseUrl) {
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return trimmed.endsWith("/v1") ? trimmed : trimmed + "/v1";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
