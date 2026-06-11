package com.huawei.ascend.service.spi.registry;

public record RuntimeDeregisterResult(
        RuntimeInstanceId runtimeInstanceId,
        RuntimeState state,
        boolean removed) {
}
