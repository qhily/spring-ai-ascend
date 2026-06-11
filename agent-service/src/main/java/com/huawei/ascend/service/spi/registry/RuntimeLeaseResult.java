package com.huawei.ascend.service.spi.registry;

import java.time.Instant;

public record RuntimeLeaseResult(
        RuntimeInstanceId runtimeInstanceId,
        RuntimeState state,
        Instant expiresAt) {
}
