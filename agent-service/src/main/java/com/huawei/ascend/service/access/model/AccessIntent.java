package com.huawei.ascend.service.access.model;

import java.util.Objects;

public record AccessIntent(
        AccessOperation operation,
        String tenantId,
        String userId,
        String agentId,
        String sessionId,
        String query,
        String idempotencyKey,
        Object payload) {

    public AccessIntent {
        Objects.requireNonNull(operation, "operation");
        tenantId = requireNonBlank(tenantId, "tenantId");
        userId = requireNonBlank(userId, "userId");
        agentId = requireNonBlank(agentId, "agentId");
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}

