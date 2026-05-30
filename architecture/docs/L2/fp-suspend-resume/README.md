---
level: L2
view: process
status: active
feature: "fp-suspend-resume"
relates_to:
  - "architecture/features/function-points.dsl"
  - "architecture/features/engineering-frames.dsl"
  - "architecture/docs/L1/frames/EF-TASK-CONTROL.md"
  - "architecture/docs/L1/agent-service/features/task-centric-control.md"
authority: "ADR-0068 (Layered 4+1 + Architecture Graph) + ADR-0161 (EngineeringFrame package-cluster anchor + Card over DSL) + ADR-0137 (SuspendSignal canonical interrupt vocabulary)"
extends:
  - ADR-0137
---

# L2 FunctionPoint Spec — `FP-SUSPEND-RESUME`

This is the **L2 detail home** for the `FP-SUSPEND-RESUME` FunctionPoint: the
runtime sequence by which a Run leaves `RUNNING`, is parked as `SUSPENDED` under
the state-machine guard, and is later returned to `RUNNING`. It carries the
method call chain and the suspend -> resume sequence that the layer-purity verdict
ruled does **NOT** belong in L0 / L1 prose (Rule 145 / E194-E195); L0 keeps the
RunStatus DFA *invariant*, this spec owns the verbs and the method hops.

> **Readable interpretation layer (Rule 146 / E196).** This spec invents no
> FunctionPoint ID, no frame ID, no method name, and no status value. Every
> identity is copied from the authoring DSL; every code anchor is cited from the
> generated facts. Where this prose and the DSL disagree, the DSL wins; where the
> DSL and the generated facts disagree, the generated facts win (ADR-0154 cascade:
> `generated facts > DSL > Card/prose`).

## Authority chain (read top-down)

1. **FunctionPoint identity (authoring DSL)** — element `fpSuspendResume` in
   [`../../../features/function-points.dsl`](../../../features/function-points.dsl),
   `saa.id` = `FP-SUSPEND-RESUME`. `saa.status` `shipped`, `saa.channel`
   `internal`, `saa.actor` `platform-runtime`, `saa.trigger`
   `internal-orchestration-event`, `saa.requirement` `REQ-004`, `saa.sourceAdr`
   `ADR-0137`. The element declares **no** `saa.code_entrypoint_refs`, **no**
   `saa.test_refs`, and **no** `saa.contract_op_refs`; this spec adds none.
2. **Owning EngineeringFrame (structural parent)** — `EF-TASK-CONTROL`
   (`efTaskControl`, owner `agent-service`), which holds the `anchors` edge to
   this FunctionPoint in
   [`../../../features/engineering-frames.dsl`](../../../features/engineering-frames.dsl).
   Its Frame Card is
   [`../../L1/frames/EF-TASK-CONTROL.md`](../../L1/frames/EF-TASK-CONTROL.md).
3. **Generated facts (binding factual authority)** — the `code-symbol/*` facts in
   [`../../../facts/generated/code-symbols.json`](../../../facts/generated/code-symbols.json).
   Every anchor cited in §4 resolves there; facts are never hand-edited.
4. **Contract surface** — none. `FP-SUSPEND-RESUME` is an `internal`-channel
   orchestration collaboration with no OpenAPI / AsyncAPI operation; its boundary
   is the owning frame's SPI types, cited in §4 / §6.
5. **L0 constraint authority** — the RunStatus DFA invariant at
   [`../../L0/ARCHITECTURE.md`](../../L0/ARCHITECTURE.md) §4 #20. L0 keeps the
   legal-transition invariant; this spec carries the suspend/resume verbs and the
   method sequence. The transitions themselves are grounded in the
   `RunStateMachine` code fact (§4).

---

## 1. Behavior

A Run that is executing (`RUNNING`) parks itself as `SUSPENDED`, recording the
moment it suspended, and is later returned to `RUNNING` when its blocking
condition clears — both directions passing the `RunStateMachine` legal-transition
guard. The structural axis is `agent-service -> EF-TASK-CONTROL -> FP-SUSPEND-RESUME`;
the value axis is `PC-003 -> REQ-004 -> FEAT-SUSPEND-RESUME-CONTROL ->
FP-SUSPEND-RESUME` (the `featSuspendResumeControl requires fpSuspendResume` edge
in `features.dsl`).

| Field | Value (copied from the DSL element) |
|---|---|
| FunctionPoint ID | `FP-SUSPEND-RESUME` |
| Status | `shipped` (`saa.status`) |
| Owning EngineeringFrame | `EF-TASK-CONTROL` (the `anchors` parent) |
| Owner module | `agent-service` (`saa.owner`) |
| Requirement | `REQ-004` (`saa.requirement`) |
| Channel | `internal` (`saa.channel`) |
| Actor | `platform-runtime` (`saa.actor`) |
| Trigger | `internal-orchestration-event` (`saa.trigger`) |
| Source ADR | `ADR-0137` (`saa.sourceAdr`) |

## 2. I/O

`internal` channel — no wire request / response. The input and output are typed
in-process collaborations:

- **Input (suspend leg)** — a `SuspendSignal`
  (`code-symbol/com-huawei-ascend-bus-spi-engine-suspendsignal`) raised by the
  executing leg, paired with a `SuspendReason`
  (`code-symbol/com-huawei-ascend-service-runtime-resilience-spi-suspendreason`,
  the sealed suspend taxonomy). The `Run` aggregate snapshot
  (`code-symbol/com-huawei-ascend-service-runtime-runs-run`) carries the current
  status.
- **Output (suspend leg)** — a copied `Run` snapshot at status `SUSPENDED` with
  `suspendedAt` set, produced by `Run.withSuspension(...)` (§4).
- **Output (resume leg)** — a copied `Run` snapshot back at status `RUNNING`,
  produced by `Run.withStatus(RUNNING)` after the resume transition is validated.
- **Side effects** — the guarded status change is committed through the Run
  aggregate's persistence port `RunRepository` (anchored by `EF-SESSION-TASK-STATE`,
  `FP-RUN-STATE-TRANSITION`); this FunctionPoint uses that boundary but does not
  own it (the atomic compare-and-set realization is that FunctionPoint's detail,
  not restated here).

## 3. Runtime Sequence

The legal transitions this FunctionPoint drives are the two suspend/resume edges
of the RunStatus DFA (`RunStateMachine`): `RUNNING -> SUSPENDED` on suspend, and
`SUSPENDED -> RUNNING` on resume. Each edge is admitted only after
`RunStateMachine.validate(from, to)` passes (it throws `IllegalStateException` on
an illegal edge).

```mermaid
sequenceDiagram
    autonumber
    participant Exec as Executing leg
    participant Orch as Orchestrator (catch site)
    participant SM as RunStateMachine.validate
    participant Run as Run snapshot
    participant Repo as RunRepository (EF-SESSION-TASK-STATE)

    Exec->>Orch: throw SuspendSignal (+ SuspendReason)
    Orch->>SM: validate(RUNNING, SUSPENDED)
    SM-->>Orch: ok (illegal edge -> IllegalStateException)
    Orch->>Run: withSuspension(reason, now)
    Run-->>Orch: Run snapshot @ SUSPENDED (suspendedAt set)
    Orch->>Repo: commit guarded status change
    Note over Orch,Repo: blocking condition clears (resume trigger)
    Orch->>SM: validate(SUSPENDED, RUNNING)
    SM-->>Orch: ok
    Orch->>Run: withStatus(RUNNING)
    Run-->>Orch: Run snapshot @ RUNNING
    Orch->>Repo: commit guarded status change
```

The catch-site / dispatch participant (`Orchestrator`) lives in the neutral
execution model (`EF-ORCHESTRATION-SPI`, package `com.huawei.ascend.bus.spi.engine`)
and is named here only as the boundary that reacts to a `SuspendSignal`; this
FunctionPoint's own frame (`EF-TASK-CONTROL`) anchors the *control* behaviour —
the guarded `Run` snapshot transition. The end-to-end orchestration plumbing
(checkpoint persistence, scheduler parking) is the orchestration SPI's process
detail, referenced not duplicated.

## 4. Class / Method Anchors (from facts)

Every code anchor cited from the generated facts; no class or method name is
minted. Method descriptors are verbatim entries in each class fact's
`public_methods[]`.

| Role | Symbol | Fact id (+ method descriptor) |
|---|---|---|
| In-frame state snapshot (suspend) | `Run.withSuspension` | `code-symbol/com-huawei-ascend-service-runtime-runs-run#withSuspension(Ljava/lang/String;Ljava/time/Instant;)Lcom/huawei/ascend/service/runtime/runs/Run;` |
| In-frame state snapshot (resume) | `Run.withStatus` | `code-symbol/com-huawei-ascend-service-runtime-runs-run#withStatus(Lcom/huawei/ascend/service/runtime/runs/RunStatus;)Lcom/huawei/ascend/service/runtime/runs/Run;` |
| In-frame transition guard | `RunStateMachine.validate` | `code-symbol/com-huawei-ascend-service-runtime-runs-runstatemachine#validate(Lcom/huawei/ascend/service/runtime/runs/RunStatus;Lcom/huawei/ascend/service/runtime/runs/RunStatus;)V` |
| In-frame status model | `RunStatus` (enum) | `code-symbol/com-huawei-ascend-service-runtime-runs-runstatus` |
| Collaborating signal carrier | `SuspendSignal` | `code-symbol/com-huawei-ascend-bus-spi-engine-suspendsignal` |
| Collaborating reason taxonomy | `SuspendReason` (sealed) | `code-symbol/com-huawei-ascend-service-runtime-resilience-spi-suspendreason` |

All fact ids in this section resolve in
[`../../../facts/generated/code-symbols.json`](../../../facts/generated/code-symbols.json).
The `SuspendReason` interface declares no public methods (it is a sealed type with
record variants), so it is cited as a type only.

## 5. Error Paths

| Cause (observable) | Outcome | Status / signal | Exception |
|---|---|---|---|
| Suspend requested from a status with no `-> SUSPENDED` edge (only `RUNNING` has one) | rejected at the model boundary | no transition; the suspend is refused | `IllegalStateException` from `RunStateMachine.validate` |
| Resume requested from a status with no `-> RUNNING` edge | rejected at the model boundary | no transition; the resume is refused | `IllegalStateException` from `RunStateMachine.validate` |

Both outcomes are the same guarded-transition rejection the DFA enforces; this
FunctionPoint introduces no `error.code` of its own (no contract surface — §6).
The terminal-failure paths a suspend can later resolve to (for example a
`SuspendReason.AwaitClientCallback` deadline elapsing to `FAILED`) are the
collaborating reason variant's concern, not this FunctionPoint's transition guard.

## 6. Contracts

No external contract surface — internal boundary; the contract is the owning
frame's SPI types (cited in §4): the `Run` / `RunStatus` / `RunStateMachine`
public surface of `com.huawei.ascend.service.runtime.runs`, and the collaborating
`SuspendSignal` / `SuspendReason` carriers. The DSL element `fpSuspendResume`
declares no `saa.contract_op_refs`, so none is cited.

## 7. Tests

The authoring DSL declares **no** FunctionPoint-level test for `FP-SUSPEND-RESUME`:
the element carries no `saa.test_refs`, and
[`../../../features/verification.dsl`](../../../features/verification.dsl) holds no
`verifies` edge into `fpSuspendResume`. Per the readable-interpretation discipline
(Rule 146 clause 2), no `test/*` fact is attributed to this FunctionPoint here —
inventing one would assert a `verifies` relationship the DSL does not record.

The state-machine *invariant* this FunctionPoint relies on (the legal `RUNNING <->
SUSPENDED` edges) is exercised at the frame altitude by the `RunStateMachine` DFA
suite recorded in the `EF-TASK-CONTROL` Frame Card `fact_refs:`
(`test/com-huawei-ascend-service-runtime-runs-runstatemachinetest`); a
FunctionPoint-scoped `verifies` edge is the authoring step that would let this
section cite a test directly, and lands with the same PR that adds it.

## 8. Gates

| Concern | Gate rule / enforcer | What it blocks |
|---|---|---|
| FunctionPoint element well-formedness | Rule G-14 / E160 | a profile-tagged FP element missing a required `saa.*` property. |
| Frame anchors >= 1 FP (shipped) | Rule G-23 | promoting `EF-TASK-CONTROL` to `shipped` without anchoring >= 1 FunctionPoint (this FP is one of its anchors). |
| Card / spec is a readable interpretation | Rule 146 / E196 | a `code-symbol/*` citation or method descriptor here that does not resolve in the generated facts, or an FP/frame relationship not present in the DSL. |
| No L2 detail left upstream | Rule 145 / E194-E195 | the suspend/resume method-chain and sequence this spec carries being left in L0 / L1 prose instead. |
| FunctionPoint readiness | Rule 147 / E197 (kernel Rule G-30) | a FunctionPoint marked ready whose axis obligations are absent — `gate/lib/check_feature_readiness.py`, ADVISORY at the ADR-0159 §13.3 landing rung. |

---

## What stays upstream (NOT carried here)

Per the layer-purity keep-list (Rule 145), the following remain at L0 / L1 and are
only *referenced* here, never duplicated:

- the L0 §4 RunStatus DFA *invariant* (which transitions are legal / terminal) —
  L0 owns the invariant; this spec owns the suspend/resume verbs and method hops;
- naming `EF-TASK-CONTROL` / the `com.huawei.ascend.service.runtime.runs` package
  as a **boundary identity** (Frame Card material);
- the Run aggregate's atomic compare-and-set persistence realization
  (`FP-RUN-STATE-TRANSITION` / `EF-SESSION-TASK-STATE`), used here but detailed
  there;
- citing the ArchUnit / gate enforcer that pins the boundary (named in §8, not
  re-specified).

## Authority

- ADR-0068 — Layered 4+1 + Architecture Graph as twin sources of truth
  ([`../../../../docs/adr/0068-layered-4plus1-and-architecture-graph.yaml`](../../../../docs/adr/0068-layered-4plus1-and-architecture-graph.yaml)).
- ADR-0161 — EngineeringFrame package-cluster anchor + Card over DSL
  ([`../../../../docs/adr/0161-engineering-frame-package-cluster-anchor-and-card-over-dsl.yaml`](../../../../docs/adr/0161-engineering-frame-package-cluster-anchor-and-card-over-dsl.yaml)).
- ADR-0137 — SuspendSignal canonical interrupt vocabulary
  ([`../../../../docs/adr/0137-suspendsignal-canonical-interruptsignal-glossary.yaml`](../../../../docs/adr/0137-suspendsignal-canonical-interruptsignal-glossary.yaml)).
- Frame Card: [`../../L1/frames/EF-TASK-CONTROL.md`](../../L1/frames/EF-TASK-CONTROL.md).
- L2 corpus index: [`../README.md`](../README.md).
