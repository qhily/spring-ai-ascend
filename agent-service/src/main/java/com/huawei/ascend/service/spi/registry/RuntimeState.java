package com.huawei.ascend.service.spi.registry;

public enum RuntimeState {
    REGISTERING,
    COLD,
    READY,
    AT_CAPACITY,
    DRAINING,
    UNREACHABLE,
    DEREGISTERED
}
