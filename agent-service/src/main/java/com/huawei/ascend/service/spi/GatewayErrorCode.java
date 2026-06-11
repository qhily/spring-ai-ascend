package com.huawei.ascend.service.spi;

/**
 * Error vocabulary shared by the registry, discovery, and routing seams.
 * Codes are stable wire identifiers; edges map them to transport status codes.
 */
public enum GatewayErrorCode {
    BAD_REQUEST,
    AGENT_NOT_FOUND,
    RUNTIME_UNREACHABLE,
    RUNTIME_COLD,
    RUNTIME_AT_CAPACITY,
    RUNTIME_DRAINING,
    GATEWAY_FORWARD_FAILED,
    ROUTE_GRANT_INVALID,
    ROUTE_GRANT_EXPIRED,
    ROUTE_GRANT_REVOKED,
    TENANT_FORBIDDEN,
    SOURCE_AGENT_FORBIDDEN,
    TARGET_AGENT_MISMATCH,
    A2A_METHOD_FORBIDDEN
}
