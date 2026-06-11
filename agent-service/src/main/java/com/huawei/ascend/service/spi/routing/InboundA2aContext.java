package com.huawei.ascend.service.spi.routing;

import java.util.Objects;

public record InboundA2aContext(
        String tenantId,
        String sourceAgentId,
        String targetAgentId,
        String a2aMethod) {

    public InboundA2aContext {
        tenantId = required(tenantId, "tenantId");
        sourceAgentId = required(sourceAgentId, "sourceAgentId");
        targetAgentId = required(targetAgentId, "targetAgentId");
        a2aMethod = required(a2aMethod, "a2aMethod");
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
