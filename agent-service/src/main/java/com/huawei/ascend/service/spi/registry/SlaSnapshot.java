package com.huawei.ascend.service.spi.registry;

import java.time.Duration;

public record SlaSnapshot(
        Duration serviceRoutingLatency,
        Duration runtimeAdmissionLatency,
        Duration runtimeModelFirstTokenLatency,
        boolean firstTokenSlaBreached) {

    public static SlaSnapshot empty() {
        return new SlaSnapshot(Duration.ZERO, Duration.ZERO, Duration.ZERO, false);
    }
}
