package com.huawei.ascend.service.spi.discovery;

import com.huawei.ascend.service.spi.registry.RuntimeCapacitySnapshot;
import com.huawei.ascend.service.spi.registry.RuntimeInstanceId;
import com.huawei.ascend.service.spi.registry.RuntimeState;
import com.huawei.ascend.service.spi.registry.SlaSnapshot;
import java.net.URI;
import java.time.Instant;

public record RuntimeRoute(
        String agentId,
        RuntimeInstanceId runtimeInstanceId,
        URI a2aEndpoint,
        RuntimeState state,
        Instant lastHeartbeatAt,
        SlaSnapshot slaSnapshot,
        RuntimeCapacitySnapshot capacitySnapshot) {
}
