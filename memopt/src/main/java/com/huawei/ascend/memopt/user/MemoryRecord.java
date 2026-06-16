package com.huawei.ascend.memopt.user;

/**
 * One thing to remember about a user — a distilled soft fact (preference, risk
 * attitude, stated intent, conversation conclusion). Authoritative data
 * (holdings, transactions) is NOT stored here; it is read live from the bank's
 * system of record. Keeping memory to a few soft facts is the per-user cost lever.
 *
 * @param content the fact text (already distilled by the caller / engine)
 * @param kind    a coarse label, e.g. "preference" / "risk" / "conclusion"
 */
public record MemoryRecord(String content, String kind) {

    public MemoryRecord {
        content = content == null ? "" : content;
        kind = kind == null || kind.isBlank() ? "fact" : kind;
    }

    public static MemoryRecord of(String content) {
        return new MemoryRecord(content, "fact");
    }
}
