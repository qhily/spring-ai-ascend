package com.huawei.ascend.service.spi;

import com.huawei.ascend.service.spi.discovery.AgentDirectory;
import com.huawei.ascend.service.spi.discovery.RoutingContext;
import com.huawei.ascend.service.spi.registry.RuntimeAgentRegistration;
import com.huawei.ascend.service.spi.registry.RuntimeCapacitySnapshot;
import com.huawei.ascend.service.spi.registry.RuntimeInstanceId;
import com.huawei.ascend.service.spi.registry.RuntimeLeaseRenewal;
import com.huawei.ascend.service.spi.registry.RuntimeRegistry;
import com.huawei.ascend.service.spi.registry.RuntimeState;
import com.huawei.ascend.service.spi.registry.SlaSnapshot;
import com.huawei.ascend.service.testsupport.AgentCards;
import com.huawei.ascend.service.testsupport.MutableClock;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Behavior contract every {@link RuntimeRegistry}+{@link AgentDirectory}
 * implementation must satisfy: lease/TTL state transitions, capacity-derived
 * routability, deterministic route preference, and tenant isolation.
 * Implementation test classes extend this and add their own specifics.
 */
public abstract class RuntimeRegistryContractTest<T extends RuntimeRegistry & AgentDirectory> {

    protected static final Instant NOW = Instant.parse("2026-06-04T10:00:00Z");
    protected static final String TENANT = "tenant-a";

    protected abstract T createRegistry(Clock clock);

    @Test
    void registerMakesAgentDiscoverableAndRoutable() {
        T registry = createRegistry(new MutableClock(NOW));

        registry.register(registration("runtime-1", "agent-weather", Duration.ofSeconds(30)));

        assertThat(registry.getAgentCard("agent-weather", TENANT).name()).isEqualTo("agent-weather");
        assertThat(registry.listAgents(TENANT))
                .extracting("agentId")
                .containsExactly("agent-weather");
        assertThat(registry.resolveRoute("agent-weather", TENANT, RoutingContext.empty()).a2aEndpoint())
                .isEqualTo(URI.create("http://runtime-1.example/a2a"));
    }

    @Test
    void expiredLeaseIsNotRoutable() {
        MutableClock clock = new MutableClock(NOW);
        T registry = createRegistry(clock);
        registry.register(registration("runtime-1", "agent-weather", Duration.ofSeconds(5)));

        clock.set(NOW.plusSeconds(6));

        assertThatThrownBy(() -> registry.resolveRoute("agent-weather", TENANT, RoutingContext.empty()))
                .isInstanceOfSatisfying(AgentRouteNotFoundException.class,
                        ex -> assertThat(ex.code()).isEqualTo(GatewayErrorCode.RUNTIME_UNREACHABLE));

        assertThat(registry.listAgents(TENANT)).isEmpty();
    }

    @Test
    void leaseExpiringExactlyNowIsAlreadyUnreachable() {
        MutableClock clock = new MutableClock(NOW);
        T registry = createRegistry(clock);
        registry.register(registration("runtime-1", "agent-weather", Duration.ofSeconds(5)));

        clock.set(NOW.plusSeconds(5));

        assertThatThrownBy(() -> registry.resolveRoute("agent-weather", TENANT, RoutingContext.empty()))
                .isInstanceOfSatisfying(AgentRouteNotFoundException.class,
                        ex -> assertThat(ex.code()).isEqualTo(GatewayErrorCode.RUNTIME_UNREACHABLE));
    }

    @Test
    void renewalExtendsTheLeasePastTheOriginalExpiry() {
        MutableClock clock = new MutableClock(NOW);
        T registry = createRegistry(clock);
        registry.register(registration("runtime-1", "agent-weather", Duration.ofSeconds(5)));

        clock.set(NOW.plusSeconds(4));
        registry.renew(new RuntimeLeaseRenewal(
                RuntimeInstanceId.of("runtime-1"),
                RuntimeState.READY,
                Duration.ofSeconds(30),
                SlaSnapshot.empty(),
                Map.of()));
        clock.set(NOW.plusSeconds(20));

        assertThat(registry.resolveRoute("agent-weather", TENANT, RoutingContext.empty()).runtimeInstanceId())
                .isEqualTo(RuntimeInstanceId.of("runtime-1"));
    }

    @Test
    void coldAndCapacityStatesAreNotRoutable() {
        T registry = createRegistry(new MutableClock(NOW));
        registry.register(registration("runtime-1", "agent-weather", Duration.ofSeconds(30)));
        registry.renew(new RuntimeLeaseRenewal(
                RuntimeInstanceId.of("runtime-1"),
                RuntimeState.COLD,
                Duration.ofSeconds(30),
                SlaSnapshot.empty(),
                Map.of()));

        assertThatThrownBy(() -> registry.resolveRoute("agent-weather", TENANT, RoutingContext.empty()))
                .isInstanceOfSatisfying(AgentRouteNotFoundException.class,
                        ex -> assertThat(ex.code()).isEqualTo(GatewayErrorCode.RUNTIME_COLD));

        registry.renew(new RuntimeLeaseRenewal(
                RuntimeInstanceId.of("runtime-1"),
                RuntimeState.AT_CAPACITY,
                Duration.ofSeconds(30),
                SlaSnapshot.empty(),
                Map.of()));

        assertThatThrownBy(() -> registry.resolveRoute("agent-weather", TENANT, RoutingContext.empty()))
                .isInstanceOfSatisfying(AgentRouteNotFoundException.class,
                        ex -> assertThat(ex.code()).isEqualTo(GatewayErrorCode.RUNTIME_AT_CAPACITY));
    }

    @Test
    void routePrefersMostRecentlyRenewedReadyRuntime() {
        MutableClock clock = new MutableClock(NOW);
        T registry = createRegistry(clock);
        registry.register(registration("runtime-1", "agent-weather", Duration.ofSeconds(30)));
        registry.register(registration("runtime-2", "agent-weather", Duration.ofSeconds(30)));

        clock.set(NOW.plusSeconds(1));
        registry.renew(new RuntimeLeaseRenewal(
                RuntimeInstanceId.of("runtime-1"),
                RuntimeState.READY,
                Duration.ofSeconds(30),
                SlaSnapshot.empty(),
                Map.of()));

        assertThat(registry.resolveRoute("agent-weather", TENANT, RoutingContext.empty()).runtimeInstanceId())
                .isEqualTo(RuntimeInstanceId.of("runtime-1"));
    }

    @Test
    void readyRuntimeWithFullLlmCapacityIsNotRoutable() {
        T registry = createRegistry(new MutableClock(NOW));
        registry.register(registration("runtime-1", "agent-weather", Duration.ofSeconds(30)));
        registry.renew(new RuntimeLeaseRenewal(
                RuntimeInstanceId.of("runtime-1"),
                RuntimeState.READY,
                Duration.ofSeconds(30),
                SlaSnapshot.empty(),
                capacity(1, 0, 1, 1.0, 100),
                Map.of("reason", "llm-saturated")));

        assertThatThrownBy(() -> registry.resolveRoute("agent-weather", TENANT, RoutingContext.empty()))
                .isInstanceOfSatisfying(AgentRouteNotFoundException.class,
                        ex -> assertThat(ex.code()).isEqualTo(GatewayErrorCode.RUNTIME_AT_CAPACITY));
        assertThat(registry.listAgents(TENANT).getFirst().state()).isEqualTo(RuntimeState.AT_CAPACITY);
    }

    @Test
    void routePrefersLowerPressureReadyRuntime() {
        MutableClock clock = new MutableClock(NOW);
        T registry = createRegistry(clock);
        registry.register(registration("runtime-hot", "agent-weather", Duration.ofSeconds(30)));
        registry.register(registration("runtime-cool", "agent-weather", Duration.ofSeconds(30)));

        registry.renew(new RuntimeLeaseRenewal(
                RuntimeInstanceId.of("runtime-cool"),
                RuntimeState.READY,
                Duration.ofSeconds(30),
                SlaSnapshot.empty(),
                capacity(1, 0, 10, 0.1, 60),
                Map.of()));
        clock.set(NOW.plusSeconds(1));
        registry.renew(new RuntimeLeaseRenewal(
                RuntimeInstanceId.of("runtime-hot"),
                RuntimeState.READY,
                Duration.ofSeconds(30),
                SlaSnapshot.empty(),
                capacity(8, 1, 10, 0.9, 120),
                Map.of()));

        assertThat(registry.resolveRoute("agent-weather", TENANT, RoutingContext.empty()).runtimeInstanceId())
                .isEqualTo(RuntimeInstanceId.of("runtime-cool"));
    }

    @Test
    void deregisterRemovesRuntimeFromRouteView() {
        T registry = createRegistry(new MutableClock(NOW));
        registry.register(registration("runtime-1", "agent-weather", Duration.ofSeconds(30)));

        assertThat(registry.deregister(RuntimeInstanceId.of("runtime-1")).removed()).isTrue();

        assertThatThrownBy(() -> registry.resolveRoute("agent-weather", TENANT, RoutingContext.empty()))
                .isInstanceOf(AgentRouteNotFoundException.class);
    }

    @Test
    void renewIsRejectedForUnknownRuntime() {
        T registry = createRegistry(new MutableClock(NOW));

        assertThatThrownBy(() -> registry.renew(new RuntimeLeaseRenewal(
                RuntimeInstanceId.of("runtime-ghost"),
                RuntimeState.READY,
                Duration.ofSeconds(30),
                SlaSnapshot.empty(),
                Map.of())))
                .isInstanceOfSatisfying(AgentRouteNotFoundException.class,
                        ex -> assertThat(ex.code()).isEqualTo(GatewayErrorCode.RUNTIME_UNREACHABLE));
    }

    @Test
    void queriesNeverLeakAcrossTenants() {
        T registry = createRegistry(new MutableClock(NOW));
        registry.register(registration("runtime-1", "agent-weather", Duration.ofSeconds(30)));

        assertThat(registry.listAgents("tenant-b")).isEmpty();
        assertThatThrownBy(() -> registry.resolveRoute("agent-weather", "tenant-b", RoutingContext.empty()))
                .isInstanceOfSatisfying(AgentRouteNotFoundException.class,
                        ex -> assertThat(ex.code()).isEqualTo(GatewayErrorCode.AGENT_NOT_FOUND));
        assertThatThrownBy(() -> registry.getAgentCard("agent-weather", "tenant-b"))
                .isInstanceOf(AgentRouteNotFoundException.class);
    }

    protected static RuntimeAgentRegistration registration(String runtimeId, String agentId, Duration ttl) {
        return new RuntimeAgentRegistration(
                RuntimeInstanceId.of(runtimeId),
                TENANT,
                agentId,
                AgentCards.agentCard(agentId),
                URI.create("http://" + runtimeId + ".example/a2a"),
                URI.create("http://" + runtimeId + ".example/health"),
                "1.0.0",
                ttl,
                Map.of("zone", "az-a"));
    }

    protected static RuntimeCapacitySnapshot capacity(
            int llmInFlight,
            int llmQueueDepth,
            int llmMaxConcurrency,
            double estimatedLoad,
            long p95FirstTokenMs) {
        return new RuntimeCapacitySnapshot(
                0,
                0,
                0,
                llmInFlight,
                llmQueueDepth,
                llmMaxConcurrency,
                50,
                p95FirstTokenMs,
                p95FirstTokenMs * 2,
                0,
                0,
                estimatedLoad,
                NOW);
    }
}
