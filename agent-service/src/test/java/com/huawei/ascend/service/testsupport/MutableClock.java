package com.huawei.ascend.service.testsupport;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** Test clock that can be moved forward to exercise lease and grant expiry edges. */
public final class MutableClock extends Clock {

    private final AtomicReference<Instant> instant;

    public MutableClock(Instant instant) {
        this.instant = new AtomicReference<>(instant);
    }

    public void set(Instant instant) {
        this.instant.set(instant);
    }

    @Override
    public ZoneOffset getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        if (!ZoneOffset.UTC.equals(zone)) {
            throw new IllegalArgumentException("Only UTC is supported in this test clock");
        }
        return this;
    }

    @Override
    public Instant instant() {
        return instant.get();
    }
}
