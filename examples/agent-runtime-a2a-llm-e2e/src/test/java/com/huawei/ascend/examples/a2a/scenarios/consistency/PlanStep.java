package com.huawei.ascend.examples.a2a.scenarios.consistency;

import java.util.List;
import java.util.Objects;

/**
 * One declared step of a scripted plan, with the fault/behaviour script the
 * scenario pins to it.
 *
 * @param stepId           unique step id within the plan
 * @param effect           the side effect committed when the step executes
 * @param kind             how the step behaves when reached (see {@link Kind})
 * @param prompt           the agent's question to the caller — {@link Kind#SUSPEND} only
 * @param revisedRemainder replacement for the steps after this one — {@link Kind#REVISE} only
 */
public record PlanStep(
        String stepId, String effect, Kind kind, String prompt, List<PlanStep> revisedRemainder) {

    /** Step behaviour script. */
    public enum Kind {
        /** Commit the effect and continue. */
        OK,
        /** Throw before committing anything — once; the next attempt commits normally. */
        FAIL_ONCE,
        /** Pause before committing: the run suspends on input-required with {@code prompt}. */
        SUSPEND,
        /** Commit the effect, then replace every remaining step with {@code revisedRemainder}. */
        REVISE
    }

    public PlanStep {
        Objects.requireNonNull(stepId, "stepId");
        Objects.requireNonNull(effect, "effect");
        Objects.requireNonNull(kind, "kind");
        revisedRemainder = revisedRemainder == null ? List.of() : List.copyOf(revisedRemainder);
    }

    public static PlanStep ok(String stepId, String effect) {
        return new PlanStep(stepId, effect, Kind.OK, null, null);
    }

    public static PlanStep failOnce(String stepId, String effect) {
        return new PlanStep(stepId, effect, Kind.FAIL_ONCE, null, null);
    }

    public static PlanStep suspend(String stepId, String effect, String prompt) {
        return new PlanStep(stepId, effect, Kind.SUSPEND,
                Objects.requireNonNull(prompt, "prompt"), null);
    }

    public static PlanStep revise(String stepId, String effect, List<PlanStep> revisedRemainder) {
        return new PlanStep(stepId, effect, Kind.REVISE, null,
                Objects.requireNonNull(revisedRemainder, "revisedRemainder"));
    }
}
