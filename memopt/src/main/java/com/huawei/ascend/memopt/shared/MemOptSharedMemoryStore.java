package com.huawei.ascend.memopt.shared;

import com.huawei.ascend.a2a.memory.obs.MemoryObserver;
import com.huawei.ascend.a2a.memory.shared.InMemorySharedMemoryStore;
import com.huawei.ascend.a2a.memory.shared.OwnershipViolationException;
import com.huawei.ascend.a2a.memory.shared.SharedEntry;
import com.huawei.ascend.a2a.memory.shared.SharedMemoryStore;
import java.util.List;
import java.util.Optional;

/**
 * MemOpt as the backend for the a2a-shared-memory kit: it implements the kit's
 * {@link SharedMemoryStore} SPI, so the same A2A agents and {@code SharedMemoryKit}
 * run unchanged on the MemOpt engine instead of the kit's bundled in-process store.
 *
 * <p>This is MemOpt's <b>in-process engine form</b> — it delegates to an internal
 * store and adds the engine's instrumentation seam. The <b>deployment form</b>
 * (ADR-0162, form C) replaces the delegate with the closed Java engine behind a
 * gRPC {@code memopt.v1} contract over mTLS, source never leaving the container;
 * the SPI keeps that swap invisible to agents.
 *
 * <p>Ownership violations propagate (a permission error, not an engine fault) but
 * are reported to the {@link MemoryObserver}; engine faults also propagate so the
 * kit's fail-open / the collaboration's reclaim can act.
 */
public final class MemOptSharedMemoryStore implements SharedMemoryStore {

    private final SharedMemoryStore engine;
    private final MemoryObserver observer;

    public MemOptSharedMemoryStore() {
        this(new InMemorySharedMemoryStore(System::currentTimeMillis), MemoryObserver.NOOP);
    }

    public MemOptSharedMemoryStore(SharedMemoryStore engine, MemoryObserver observer) {
        this.engine = engine;
        this.observer = observer == null ? MemoryObserver.NOOP : observer;
    }

    @Override
    public SharedEntry append(String tenantId, String collaborationId, String key, String value, String writerAgentId) {
        long t0 = System.nanoTime();
        try {
            SharedEntry entry = engine.append(tenantId, collaborationId, key, value, writerAgentId);
            observer.onOperation("memopt.shared.append", tenantId, true, elapsedMs(t0));
            return entry;
        } catch (OwnershipViolationException e) {
            observer.onDegraded("memopt.shared.append", tenantId, "ownership-rejected");
            throw e;
        } catch (RuntimeException e) {
            observer.onDegraded("memopt.shared.append", tenantId, "engine-error");
            throw e;
        }
    }

    @Override
    public Optional<SharedEntry> latest(String tenantId, String collaborationId, String key) {
        long t0 = System.nanoTime();
        Optional<SharedEntry> r = engine.latest(tenantId, collaborationId, key);
        observer.onOperation("memopt.shared.latest", tenantId, true, elapsedMs(t0));
        return r;
    }

    @Override
    public List<SharedEntry> history(String tenantId, String collaborationId, String key) {
        return engine.history(tenantId, collaborationId, key);
    }

    @Override
    public List<String> keys(String tenantId, String collaborationId) {
        return engine.keys(tenantId, collaborationId);
    }

    @Override
    public void release(String tenantId, String collaborationId) {
        engine.release(tenantId, collaborationId);
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
