package com.huawei.ascend.memopt.user;

/**
 * One per-user memory recall hit.
 *
 * @param content the remembered fact
 * @param kind    its label
 * @param score   relevance (higher = more relevant); 0 when the backend gives none
 */
public record MemoryHit(String content, String kind, double score) {
}
