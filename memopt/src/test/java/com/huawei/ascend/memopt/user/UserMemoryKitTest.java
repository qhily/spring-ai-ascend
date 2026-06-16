package com.huawei.ascend.memopt.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Per-user memory: recall/remember/forget, scope isolation, fail-open, circuit, dedupe/cap. */
class UserMemoryKitTest {

    private static final MemoryScope U42 = MemoryScope.ofUser("bank", "u-42");

    @Test
    void remembersAndRecallsByKeyword() {
        UserMemoryKit mem = UserMemoryKit.forUser(new InMemoryUserMemoryStore(), U42);
        mem.remember(List.of(
                new MemoryRecord("client prefers short-term low-risk wealth products", "preference"),
                new MemoryRecord("declined equity funds last quarter", "conclusion")));

        List<MemoryHit> hits = mem.recall("risk wealth preference", 5);
        assertFalse(hits.isEmpty());
        assertTrue(hits.get(0).content().contains("low-risk"), "most relevant fact first");
    }

    @Test
    void memoryIsScopeIsolatedAcrossUsers() {
        InMemoryUserMemoryStore store = new InMemoryUserMemoryStore();
        UserMemoryKit.forUser(store, U42).remember(List.of(MemoryRecord.of("u-42 secret preference")));
        List<MemoryHit> other = UserMemoryKit.forUser(store, MemoryScope.ofUser("bank", "u-99"))
                .recall("preference", 5);
        assertTrue(other.isEmpty(), "another user sees nothing of u-42");
    }

    @Test
    void forgetDeletesScope() {
        InMemoryUserMemoryStore store = new InMemoryUserMemoryStore();
        UserMemoryKit mem = UserMemoryKit.forUser(store, U42);
        mem.remember(List.of(MemoryRecord.of("remember me then forget me")));
        assertFalse(mem.recall("remember", 5).isEmpty());
        mem.forget();
        assertTrue(mem.recall("remember", 5).isEmpty(), "scope wiped");
    }

    @Test
    void recallFailsOpenWhenBackendThrows() {
        UserMemoryStore boom = failing();
        UserMemoryKit mem = UserMemoryKit.forUser(boom, U42);
        assertTrue(mem.recall("anything", 5).isEmpty(), "fail-open: no hits, no throw");
    }

    @Test
    void strictModeSurfacesFailures() {
        UserMemoryKit strict = UserMemoryKit.forUser(failing(), U42,
                new UserMemoryKit.Options(false, 5, 30_000L), () -> 0L);
        assertThrows(RuntimeException.class, () -> strict.recall("x", 5), "failOpen=false surfaces the error");
    }

    @Test
    void circuitOpensAfterConsecutiveFailuresThenShortCircuits() {
        CountingFailingStore boom = new CountingFailingStore();
        AtomicLong now = new AtomicLong(0);
        UserMemoryKit mem = UserMemoryKit.forUser(boom, U42,
                new UserMemoryKit.Options(true, 3, 30_000L), now::get);
        for (int i = 0; i < 3; i++) {
            mem.recall("q", 5); // 3 failures trip the breaker
        }
        int callsAfterTrip = boom.calls;
        mem.recall("q", 5); // circuit open → no backend round-trip
        assertEquals(callsAfterTrip, boom.calls, "open circuit short-circuits without hitting the backend");
    }

    @Test
    void rememberDedupesWithinBatch() {
        InMemoryUserMemoryStore store = new InMemoryUserMemoryStore();
        UserMemoryKit mem = UserMemoryKit.forUser(store, U42);
        mem.remember(List.of(MemoryRecord.of("alpha note"), MemoryRecord.of("alpha note"),
                MemoryRecord.of("bravo note")));
        // discriminating tokens: a duplicate "alpha note" would show as 2 hits for "alpha"
        assertEquals(1, store.search(U42, "alpha", 10).size(), "duplicate not double-stored");
        assertEquals(1, store.search(U42, "bravo", 10).size());
    }

    @Test
    void storeCapsPerUserFootprint() {
        InMemoryUserMemoryStore store = new InMemoryUserMemoryStore(3); // cost cap = 3 facts
        UserMemoryKit mem = UserMemoryKit.forUser(store, U42);
        // distinct single-token facts so keyword search can tell them apart
        List<String> words = List.of("alpha", "bravo", "charlie", "delta", "echo",
                "foxtrot", "golf", "hotel", "india", "juliet");
        for (String w : words) {
            mem.remember(List.of(MemoryRecord.of(w)));
        }
        // cap=3 keeps the newest three (hotel, india, juliet); the rest are evicted
        assertEquals(1, store.search(U42, "juliet", 10).size(), "newest kept");
        assertTrue(store.search(U42, "alpha", 10).isEmpty(), "oldest beyond cap evicted");
    }

    private static UserMemoryStore failing() {
        return new UserMemoryStore() {
            @Override public List<MemoryHit> search(MemoryScope s, String q, int l) {
                throw new IllegalStateException("backend down");
            }
            @Override public void save(MemoryScope s, List<MemoryRecord> r) {
                throw new IllegalStateException("backend down");
            }
            @Override public void forget(MemoryScope s) {
                throw new IllegalStateException("backend down");
            }
        };
    }

    private static final class CountingFailingStore implements UserMemoryStore {
        int calls;
        @Override public List<MemoryHit> search(MemoryScope s, String q, int l) {
            calls++;
            throw new IllegalStateException("down");
        }
        @Override public void save(MemoryScope s, List<MemoryRecord> r) {
            calls++;
            throw new IllegalStateException("down");
        }
        @Override public void forget(MemoryScope s) {
            calls++;
            throw new IllegalStateException("down");
        }
    }
}
