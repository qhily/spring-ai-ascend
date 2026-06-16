package com.huawei.ascend.memopt.user;

import java.util.List;

/**
 * Backend SPI for per-user long-term memory (ADR-0162). A single shared store
 * partitioned by {@link MemoryScope#key()} — NOT a table per user. In-process
 * {@link InMemoryUserMemoryStore} for eval; a gRPC client to the closed engine in
 * production. Implementations MUST isolate by scope and MAY bound per-user
 * footprint (the cost lever).
 */
public interface UserMemoryStore {

    /** Relevant facts for the scope, best match first. */
    List<MemoryHit> search(MemoryScope scope, String query, int limit);

    /** Persist distilled facts for the scope (implementations may cap / evict). */
    void save(MemoryScope scope, List<MemoryRecord> records);

    /** Delete all memory for the scope (right-to-be-forgotten). */
    void forget(MemoryScope scope);
}
