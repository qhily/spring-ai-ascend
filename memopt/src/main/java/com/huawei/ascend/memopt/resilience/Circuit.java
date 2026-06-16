package com.huawei.ascend.memopt.resilience;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * A single fail-open circuit guarding a memory backend. Memory is a resource-heavy
 * side service that must never drag down the agent's main path: after
 * {@code failureThreshold} consecutive failures the circuit opens for
 * {@code openMs}, during which callers short-circuit to a degraded result with no
 * backend round-trip; a success closes it. Thread-safe.
 */
public final class Circuit {

    private final int failureThreshold;
    private final long openMs;
    private final LongSupplier clock;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong openUntil = new AtomicLong();

    public Circuit(int failureThreshold, long openMs, LongSupplier clock) {
        this.failureThreshold = failureThreshold;
        this.openMs = openMs;
        this.clock = clock == null ? System::currentTimeMillis : clock;
    }

    public boolean isOpen() {
        long until = openUntil.get();
        return until != 0 && clock.getAsLong() < until;
    }

    public void onSuccess() {
        consecutiveFailures.set(0);
        openUntil.set(0);
    }

    public void onFailure() {
        if (consecutiveFailures.incrementAndGet() >= failureThreshold) {
            openUntil.set(clock.getAsLong() + openMs);
        }
    }
}
