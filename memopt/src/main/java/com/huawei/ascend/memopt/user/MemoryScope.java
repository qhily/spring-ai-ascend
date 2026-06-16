package com.huawei.ascend.memopt.user;

/**
 * Per-user memory isolation key (ADR-0162). Default granularity is
 * {@code tenantId + userId} — a user's memory is shared across that user's
 * agents. {@code agentId} is an OPTIONAL sub-namespace: set it for memory private
 * to one agent; leave it blank for the shared user-level partition.
 *
 * <p>Isolation is enforced at the store/engine, and in production the identity is
 * injected server-side from the authenticated session — a client-supplied scope
 * is routing, not a trust boundary.
 */
public record MemoryScope(String tenantId, String userId, String agentId) {

    public MemoryScope {
        tenantId = tenantId == null ? "default" : tenantId;
        userId = userId == null ? "anonymous" : userId;
        agentId = agentId == null ? "" : agentId;
    }

    /** Shared user-level scope (visible to all of the user's agents). */
    public static MemoryScope ofUser(String tenantId, String userId) {
        return new MemoryScope(tenantId, userId, "");
    }

    /** Agent-private sub-namespace within the user. */
    public static MemoryScope ofAgent(String tenantId, String userId, String agentId) {
        return new MemoryScope(tenantId, userId, agentId);
    }

    /** Stable partition key; agent sub-namespace only when set. */
    public String key() {
        return agentId.isBlank() ? tenantId + " " + userId : tenantId + " " + userId + " " + agentId;
    }
}
