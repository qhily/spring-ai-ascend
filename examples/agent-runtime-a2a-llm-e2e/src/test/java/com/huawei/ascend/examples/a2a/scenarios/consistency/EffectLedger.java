package com.huawei.ascend.examples.a2a.scenarios.consistency;

import java.util.ArrayList;
import java.util.List;

/**
 * Append-only ground truth of "what actually happened": every side effect a
 * scripted plan step commits writes exactly one record here. The consistency
 * invariants are then checkable algebra — the ledger must equal a function of
 * the committed plan, independent of timing, retries and replays.
 *
 * <p>Synchronized: steps execute on the runtime's server threads while the
 * test thread asserts.
 */
public final class EffectLedger {

    /**
     * One committed side effect.
     *
     * @param runId       the platform run the effect was committed under (the
     *                    executor's {@code runId} MDC entry); null when the
     *                    effect was committed outside a tracked run
     * @param attemptId   1-based count of plan executions for the same agent
     *                    state key (the handler-side retry counter)
     * @param stepId      plan step that committed the effect
     * @param effectType  the step's declared effect, e.g. {@code place-order}
     * @param payloadHash digest of the effect payload — equal records mean
     *                    the identical effect, so duplicates are provable
     */
    public record EffectRecord(
            String runId, int attemptId, String stepId, String effectType, String payloadHash) {
    }

    private final List<EffectRecord> records = new ArrayList<>();

    public synchronized void append(EffectRecord record) {
        records.add(record);
    }

    /** Every record in commit order. */
    public synchronized List<EffectRecord> records() {
        return List.copyOf(records);
    }

    public synchronized List<EffectRecord> byStep(String stepId) {
        return records.stream().filter(record -> record.stepId().equals(stepId)).toList();
    }

    public synchronized List<EffectRecord> byAttempt(int attemptId) {
        return records.stream().filter(record -> record.attemptId() == attemptId).toList();
    }

    public synchronized int count() {
        return records.size();
    }

    /** Committed step ids in commit order (with duplicates, if any were committed twice). */
    public synchronized List<String> stepIds() {
        return records.stream().map(EffectRecord::stepId).toList();
    }
}
