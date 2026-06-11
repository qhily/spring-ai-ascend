package com.huawei.ascend.service.spi.discovery;

import java.util.Map;

public record RoutingContext(
        String sessionId,
        String correlationId,
        Map<String, Object> metadata) {

    public RoutingContext {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static RoutingContext empty() {
        return new RoutingContext(null, null, Map.of());
    }
}
