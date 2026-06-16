package com.huawei.ascend.memopt.user;

import com.huawei.ascend.memopt.resilience.Circuit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kit facade for per-user long-term memory (ADR-0162) — the open consumption
 * surface. Bound to one {@link MemoryScope}. Batteries: scope isolation (every
 * call carries the scope), fail-open + circuit breaker (memory never drags down
 * the agent's main path), and cheap distillation (within-batch dedupe; the store
 * caps per-user footprint).
 *
 * <pre>{@code
 * UserMemoryKit mem = UserMemoryKit.forUser(store, MemoryScope.ofUser("bank", "u-42"));
 * mem.remember(List.of(MemoryRecord.of("prefers short-term low-risk wealth")));
 * mem.recall("what does the client prefer", 5);   // empty (not an error) if the engine is down
 * mem.forget();                                    // right-to-be-forgotten
 * }</pre>
 */
public final class UserMemoryKit {

    private static final Logger LOG = LoggerFactory.getLogger("memopt.user");

    /** Fail-open / circuit tunables. */
    public record Options(boolean failOpen, int circuitFailureThreshold, long circuitOpenMs) {
        public static Options defaults() {
            return new Options(true, 5, 30_000L);
        }
    }

    private final UserMemoryStore store;
    private final MemoryScope scope;
    private final Options options;
    private final Circuit circuit;

    private UserMemoryKit(UserMemoryStore store, MemoryScope scope, Options options, LongSupplier clock) {
        this.store = store;
        this.scope = scope;
        this.options = options == null ? Options.defaults() : options;
        this.circuit = new Circuit(this.options.circuitFailureThreshold(), this.options.circuitOpenMs(),
                clock == null ? System::currentTimeMillis : clock);
    }

    public static UserMemoryKit forUser(UserMemoryStore store, MemoryScope scope) {
        return new UserMemoryKit(store, scope, Options.defaults(), System::currentTimeMillis);
    }

    public static UserMemoryKit forUser(UserMemoryStore store, MemoryScope scope, Options options, LongSupplier clock) {
        return new UserMemoryKit(store, scope, options, clock);
    }

    /** Recall relevant facts; fail-open returns no hits (never throws) when the engine is down. */
    public List<MemoryHit> recall(String query, int limit) {
        if (circuit.isOpen()) {
            return degradeList("recall", "circuit open", null);
        }
        try {
            List<MemoryHit> hits = store.search(scope, query, limit);
            circuit.onSuccess();
            return hits;
        } catch (RuntimeException e) {
            circuit.onFailure();
            return degradeList("recall", e.getMessage(), e);
        }
    }

    /** Remember distilled facts (dedup within the batch); fail-open silently skips on engine failure. */
    public void remember(List<MemoryRecord> records) {
        List<MemoryRecord> distilled = dedupe(records);
        if (distilled.isEmpty()) {
            return;
        }
        if (circuit.isOpen()) {
            degradeVoid("remember", "circuit open", null);
            return;
        }
        try {
            store.save(scope, distilled);
            circuit.onSuccess();
        } catch (RuntimeException e) {
            circuit.onFailure();
            degradeVoid("remember", e.getMessage(), e);
        }
    }

    /** Delete all memory for this scope (right-to-be-forgotten). */
    public void forget() {
        try {
            store.forget(scope);
        } catch (RuntimeException e) {
            degradeVoid("forget", e.getMessage(), e);
        }
    }

    private List<MemoryHit> degradeList(String op, String reason, RuntimeException error) {
        if (!options.failOpen()) {
            throw error != null ? error : new IllegalStateException("memopt " + op + " unavailable: " + reason);
        }
        LOG.debug("memopt {} degraded ({}); continuing without memory", op, reason);
        return List.of();
    }

    private void degradeVoid(String op, String reason, RuntimeException error) {
        if (!options.failOpen()) {
            throw error != null ? error : new IllegalStateException("memopt " + op + " unavailable: " + reason);
        }
        LOG.debug("memopt {} degraded ({}); continuing without memory", op, reason);
    }

    private static List<MemoryRecord> dedupe(List<MemoryRecord> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<MemoryRecord> out = new ArrayList<>();
        for (MemoryRecord r : records) {
            if (r != null && !r.content().isBlank() && seen.add(r.content())) {
                out.add(r);
            }
        }
        return out;
    }
}
