package com.huawei.ascend.service.core;

import com.huawei.ascend.service.spi.registry.RuntimeInstanceId;
import com.huawei.ascend.service.spi.routing.RouteCacheKey;
import com.huawei.ascend.service.spi.routing.RouteGrant;
import com.huawei.ascend.service.testsupport.MutableClock;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRouteGrantCacheTest {

    private static final Instant NOW = Instant.parse("2026-06-05T10:00:00Z");

    @Test
    void cacheReturnsUnexpiredGrantAndEvictsExpiredGrant() {
        MutableClock clock = new MutableClock(NOW);
        InMemoryRouteGrantCache cache = new InMemoryRouteGrantCache(clock);
        RouteGrant grant = grant(1, NOW.plusSeconds(5));

        cache.put(grant);

        assertThat(cache.get(key())).contains(grant);

        clock.set(NOW.plusSeconds(6));

        assertThat(cache.get(key())).isEmpty();
        assertThat(cache.size()).isZero();
    }

    @Test
    void grantExpiringExactlyNowIsEvicted() {
        MutableClock clock = new MutableClock(NOW);
        InMemoryRouteGrantCache cache = new InMemoryRouteGrantCache(clock);
        cache.put(grant(1, NOW.plusSeconds(5)));

        clock.set(NOW.plusSeconds(5));

        assertThat(cache.get(key())).isEmpty();
    }

    @Test
    void invalidateRemovesGrantForKey() {
        InMemoryRouteGrantCache cache = new InMemoryRouteGrantCache(Clock.fixed(NOW, ZoneOffset.UTC));
        cache.put(grant(1, NOW.plusSeconds(60)));

        cache.invalidate(key());

        assertThat(cache.get(key())).isEmpty();
        assertThat(cache.size()).isZero();
    }

    @Test
    void invalidateByPolicyVersionRemovesStaleTenantGrants() {
        InMemoryRouteGrantCache cache = new InMemoryRouteGrantCache(Clock.fixed(NOW, ZoneOffset.UTC));
        cache.put(grant(1, NOW.plusSeconds(60)));

        cache.invalidateByPolicyVersion("tenant-a", 1);

        assertThat(cache.get(key())).isEmpty();
    }

    private static RouteCacheKey key() {
        return new RouteCacheKey("tenant-a", "agent-a", "agent-b", "message/stream");
    }

    private static RouteGrant grant(long policyVersion, Instant expiresAt) {
        return new RouteGrant(
                "grant-1",
                "tenant-a",
                "agent-a",
                "agent-b",
                RuntimeInstanceId.of("runtime-b"),
                URI.create("http://runtime-b.example/a2a"),
                Set.of("message/stream"),
                policyVersion,
                NOW,
                expiresAt,
                "signature");
    }
}
