package com.huawei.ascend.service.spi;

/**
 * Raised when no routable runtime exists for a tenant-scoped agent lookup;
 * {@link #code()} carries the dominant runtime state so edges can distinguish
 * an unknown agent from a known-but-unroutable one.
 */
public class AgentRouteNotFoundException extends RuntimeException {

    private final GatewayErrorCode code;

    public AgentRouteNotFoundException(String message) {
        this(GatewayErrorCode.AGENT_NOT_FOUND, message);
    }

    public AgentRouteNotFoundException(GatewayErrorCode code, String message) {
        super(message);
        this.code = code == null ? GatewayErrorCode.AGENT_NOT_FOUND : code;
    }

    public GatewayErrorCode code() {
        return code;
    }
}
