package com.huawei.ascend.service.spi.registry;

import java.time.Instant;

public record RuntimeRegistrationResult(
        RuntimeInstanceId runtimeInstanceId,
        String tenantId,
        String agentId,
        RuntimeState state,
        Instant expiresAt) {
}
