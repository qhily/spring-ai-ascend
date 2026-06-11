package com.huawei.ascend.service.core;

import com.huawei.ascend.service.spi.GatewayErrorCode;
import com.huawei.ascend.service.spi.registry.RuntimeAgentRegistration;
import com.huawei.ascend.service.spi.registry.RuntimeInstanceId;
import com.huawei.ascend.service.spi.discovery.RoutingContext;
import com.huawei.ascend.service.spi.routing.InboundA2aContext;
import com.huawei.ascend.service.spi.routing.RouteGrant;
import com.huawei.ascend.service.spi.routing.RouteGrantRequest;
import com.huawei.ascend.service.testsupport.AgentCards;
import com.huawei.ascend.service.testsupport.MutableClock;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HmacRouteGrantServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-05T10:00:00Z");

    @Test
    void resolveGrantSignsTenantScopedRouteToTargetRuntime() {
        MutableClock clock = new MutableClock(NOW);
        HmacRouteGrantService service = service(clock);

        RouteGrant grant = service.resolveGrant(request(Duration.ofSeconds(30)));

        assertThat(grant.tenantId()).isEqualTo("tenant-a");
        assertThat(grant.sourceAgentId()).isEqualTo("agent-a");
        assertThat(grant.targetAgentId()).isEqualTo("agent-b");
        assertThat(grant.targetRuntimeId()).isEqualTo(RuntimeInstanceId.of("runtime-b"));
        assertThat(grant.a2aEndpoint()).isEqualTo(URI.create("http://runtime-b.example/a2a"));
        assertThat(grant.allowedMethods()).containsExactly("message/stream");
        assertThat(grant.signature()).isNotBlank();

        assertThat(service.validate(grant, inbound("tenant-a", "agent-a", "agent-b", "message/stream")).accepted())
                .isTrue();
    }

    @Test
    void validateRejectsExpiredAndMismatchedGrants() {
        MutableClock clock = new MutableClock(NOW);
        HmacRouteGrantService service = service(clock);
        RouteGrant grant = service.resolveGrant(request(Duration.ofSeconds(5)));

        assertThat(service.validate(grant, inbound("tenant-b", "agent-a", "agent-b", "message/stream")).code())
                .isEqualTo(GatewayErrorCode.TENANT_FORBIDDEN);
        assertThat(service.validate(grant, inbound("tenant-a", "agent-x", "agent-b", "message/stream")).code())
                .isEqualTo(GatewayErrorCode.SOURCE_AGENT_FORBIDDEN);
        assertThat(service.validate(grant, inbound("tenant-a", "agent-a", "agent-x", "message/stream")).code())
                .isEqualTo(GatewayErrorCode.TARGET_AGENT_MISMATCH);
        assertThat(service.validate(grant, inbound("tenant-a", "agent-a", "agent-b", "message/send")).code())
                .isEqualTo(GatewayErrorCode.A2A_METHOD_FORBIDDEN);

        clock.set(NOW.plusSeconds(6));

        assertThat(service.validate(grant, inbound("tenant-a", "agent-a", "agent-b", "message/stream")).code())
                .isEqualTo(GatewayErrorCode.ROUTE_GRANT_EXPIRED);
    }

    @Test
    void grantExpiringExactlyNowIsAlreadyExpired() {
        MutableClock clock = new MutableClock(NOW);
        HmacRouteGrantService service = service(clock);
        RouteGrant grant = service.resolveGrant(request(Duration.ofSeconds(5)));

        clock.set(NOW.plusSeconds(5));

        assertThat(service.validate(grant, inbound("tenant-a", "agent-a", "agent-b", "message/stream")).code())
                .isEqualTo(GatewayErrorCode.ROUTE_GRANT_EXPIRED);
    }

    @Test
    void validateRejectsTamperedGrant() {
        HmacRouteGrantService service = service(new MutableClock(NOW));
        RouteGrant grant = service.resolveGrant(request(Duration.ofSeconds(30)));
        RouteGrant tampered = new RouteGrant(
                grant.grantId(),
                grant.tenantId(),
                grant.sourceAgentId(),
                grant.targetAgentId(),
                grant.targetRuntimeId(),
                URI.create("http://evil.example/a2a"),
                grant.allowedMethods(),
                grant.policyVersion(),
                grant.issuedAt(),
                grant.expiresAt(),
                grant.signature());

        assertThat(service.validate(tampered, inbound("tenant-a", "agent-a", "agent-b", "message/stream")).code())
                .isEqualTo(GatewayErrorCode.ROUTE_GRANT_INVALID);
    }

    private static HmacRouteGrantService service(Clock clock) {
        InMemoryRuntimeRegistry registry = new InMemoryRuntimeRegistry(clock);
        registry.register(new RuntimeAgentRegistration(
                RuntimeInstanceId.of("runtime-b"),
                "tenant-a",
                "agent-b",
                AgentCards.agentCard("agent-b"),
                URI.create("http://runtime-b.example/a2a"),
                URI.create("http://runtime-b.example/health"),
                "1.0.0",
                Duration.ofSeconds(60),
                Map.of()));
        return new HmacRouteGrantService(registry, clock, "test-secret");
    }

    private static RouteGrantRequest request(Duration ttl) {
        return new RouteGrantRequest("tenant-a", "agent-a", "agent-b", "message/stream", RoutingContext.empty(), ttl);
    }

    private static InboundA2aContext inbound(String tenant, String source, String target, String method) {
        return new InboundA2aContext(tenant, source, target, method);
    }
}
