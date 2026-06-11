package com.huawei.ascend.service.spi.discovery;

import com.huawei.ascend.service.spi.registry.RuntimeState;
import java.net.URI;

public record AgentCardSummary(
        String tenantId,
        String agentId,
        String name,
        String version,
        URI a2aEndpoint,
        RuntimeState state) {
}
