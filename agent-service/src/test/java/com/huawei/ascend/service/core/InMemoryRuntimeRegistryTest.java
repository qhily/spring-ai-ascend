package com.huawei.ascend.service.core;

import com.huawei.ascend.service.spi.RuntimeRegistryContractTest;
import com.huawei.ascend.service.spi.registry.RuntimeInstanceId;
import com.huawei.ascend.service.spi.registry.RuntimeLeaseRenewal;
import com.huawei.ascend.service.spi.registry.RuntimeState;
import com.huawei.ascend.service.spi.registry.SlaSnapshot;
import com.huawei.ascend.service.testsupport.MutableClock;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRuntimeRegistryTest extends RuntimeRegistryContractTest<InMemoryRuntimeRegistry> {

    @Override
    protected InMemoryRuntimeRegistry createRegistry(Clock clock) {
        return new InMemoryRuntimeRegistry(clock);
    }

    @Test
    void healthStatsCountReadyAndUnreachableRuntimes() {
        MutableClock clock = new MutableClock(NOW);
        InMemoryRuntimeRegistry registry = createRegistry(clock);
        registry.register(registration("runtime-ready", "agent-weather", Duration.ofSeconds(30)));
        registry.register(registration("runtime-expiring", "agent-travel", Duration.ofSeconds(5)));

        clock.set(NOW.plusSeconds(6));

        InMemoryRuntimeRegistry.HealthStats stats = registry.healthStats();
        assertThat(stats.registeredRuntimeCount()).isEqualTo(2);
        assertThat(stats.readyRuntimeCount()).isEqualTo(1);
        assertThat(stats.unreachableRuntimeCount()).isEqualTo(1);
    }

    @Test
    void healthStatsCountSaturatedRuntimeAsNotReady() {
        InMemoryRuntimeRegistry registry = createRegistry(new MutableClock(NOW));
        registry.register(registration("runtime-1", "agent-weather", Duration.ofSeconds(30)));
        registry.renew(new RuntimeLeaseRenewal(
                RuntimeInstanceId.of("runtime-1"),
                RuntimeState.READY,
                Duration.ofSeconds(30),
                SlaSnapshot.empty(),
                capacity(1, 0, 1, 1.0, 100),
                Map.of("reason", "llm-saturated")));

        assertThat(registry.healthStats().readyRuntimeCount()).isZero();
    }
}
