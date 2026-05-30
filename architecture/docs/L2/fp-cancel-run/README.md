---
level: L2
view: scenarios
status: active
feature: "fp-cancel-run"
relates_to:
  - "architecture/features/function-points.dsl"
  - "architecture/features/engineering-frames.dsl"
  - "architecture/docs/L1/frames/EF-TASK-CONTROL.md"
  - "architecture/docs/L2/run-http-contract/README.md"
  - "docs/contracts/openapi-v1.yaml"
authority: "ADR-0068 (Layered 4+1 + Architecture Graph) + ADR-0161 (EngineeringFrame package-cluster anchor + Card over DSL) + ADR-0108 (tenant re-auth widening) + ADR-0118 (atomic updateIfNotTerminal CAS) + ADR-0142 (Run aggregate single owner) + ADR-0056 (JWT validation + tenant claim cross-check)"
---

<!--
  ALTITUDE: this is an L2 FunctionPoint spec. It carries the entry method, the
  participating method hops, the runtime scenario, the error matrix, and the
  contract/test evidence for ONE FunctionPoint. It is a READABLE INTERPRETATION
  layer (ADR-0161 / Rule 146): it invents no FunctionPoint ID, frame ID, operation
  ID, status code, error code, or method name — every identity is copied from the
  authoring DSL and every fact is cited from the generated facts. Authority
  cascade: generated facts > DSL > Card/prose.
-->

# L2 FunctionPoint — `FP-CANCEL-RUN` (cancel a Run via not-terminal CAS)

> Cancel an in-flight Run over `POST /v1/runs/{runId}/cancel`: re-validate tenant
> ownership, then attempt an atomic not-terminal compare-and-set to the cancelled
> state. This spec is the **method-level detail home** for the cancel verb; the
> feature-level cancel-CAS realization and cancel-race winner/loser ordering live
> in the [`../run-http-contract/process.md`](../run-http-contract/process.md)
> §4–§6 sink and are cited, not duplicated, here.

## Authority chain (read top-down)

1. **FunctionPoint identity (authoring DSL)** — element `fpCancelRun` in
   [`../../../features/function-points.dsl`](../../../features/function-points.dsl),
   `saa.id` = `FP-CANCEL-RUN`. The §1 identity table copies its `saa.*`
   properties verbatim.
2. **Owning EngineeringFrame (structural parent)** — `EF-TASK-CONTROL`
   (element `efTaskControl`) holds the `anchors` edge to `fpCancelRun` in
   [`../../../features/engineering-frames.dsl`](../../../features/engineering-frames.dsl);
   its Frame Card is
   [`../../L1/frames/EF-TASK-CONTROL.md`](../../L1/frames/EF-TASK-CONTROL.md).
   (The HTTP edge that physically hosts the route is `EF-ACCESS-ADMISSION`; the
   *control* semantics — cancel re-authorization and the transition — are this
   frame's responsibility, which is why the DSL anchors the cancel FunctionPoint
   here.)
3. **Generated facts (binding factual authority)** — the `code-symbol/*`,
   `test/*`, and `contract-op/*` facts in
   [`../../../facts/generated/code-symbols.json`](../../../facts/generated/code-symbols.json),
   [`../../../facts/generated/tests.json`](../../../facts/generated/tests.json), and
   [`../../../facts/generated/contract-surfaces.json`](../../../facts/generated/contract-surfaces.json).
4. **Contract surface (binding wire authority)** — operation `cancelRun`
   (`POST /v1/runs/{runId}/cancel`) in
   [`../../../../docs/contracts/openapi-v1.yaml`](../../../../docs/contracts/openapi-v1.yaml),
   extracted as `contract-op/cancelrun`.
5. **L0 constraint authority** —
   [`../../L0/ARCHITECTURE.md`](../../L0/ARCHITECTURE.md) §4 owns the invariant
   that cancel is a *state transition* (not a delete) re-validated for tenancy;
   this spec owns the verb, the CAS hop, and the status codes.

---

## 1. Behavior

This FunctionPoint realizes one behaviour: transition an in-flight Run to the
cancelled terminal state atomically, or reject the cancel when the Run is already
terminal or not owned by the caller's tenant. On the value axis it is
`Run Lifecycle Control` (feature `featRunLifecycleControl`) under requirement
`REQ-001`; on the structural axis it is
`agent-service -> EF-TASK-CONTROL -> FP-CANCEL-RUN`.

| Field | Value (copied from the DSL element) |
|---|---|
| FunctionPoint ID | `FP-CANCEL-RUN` |
| Status | `shipped` (`saa.status`) |
| Owning EngineeringFrame | `EF-TASK-CONTROL` (the `anchors` parent) |
| Owner module | `agent-service` (`saa.owner`) |
| Requirement | `REQ-001` (`saa.requirement`) |
| Channel | `http` (`saa.channel`) |
| Actor | `tenant-developer` (`saa.actor`) |
| Trigger | `HTTP POST /v1/runs/(runId)/cancel` (`saa.trigger`) |
| Source ADR | `ADR-0108` (`saa.sourceAdr`) |

## 2. I/O

- **Input** — the `runId` path parameter (a `String`/UUID), carried with the
  `X-Tenant-Id` header and bearer token. There is no request body. The tenant of
  the bearer token is cross-checked against the owning tenant of the Run before
  the transition (ADR-0108 re-auth widening).
- **Output (success)** — `200 OK` when the cancel is applied (or when the Run is
  already in the same cancelled terminal state — an idempotent no-op).
- **Side effects** — exactly one atomic write through the Run aggregate's
  single-owner SPI
  (`code-symbol/com-huawei-ascend-service-runtime-runs-spi-runrepository`,
  `updateIfNotTerminal(...)`), guarded by the transition validator
  (`code-symbol/com-huawei-ascend-service-runtime-runs-runstatemachine`,
  `validate(...)`) running *inside* the CAS so validate + transition is atomic
  (ADR-0118 + ADR-0142). No write occurs on the loser side of a race.

## 3. Runtime Sequence

```mermaid
sequenceDiagram
    autonumber
    participant Actor as tenant-developer
    participant Entry as RunController.cancel
    participant RR as RunRepository (CAS owner, ADR-0142)
    participant SM as RunStateMachine.validate

    Actor->>Entry: POST /v1/runs/{runId}/cancel (X-Tenant-Id, bearer)
    Entry->>RR: findById(runId) -> Run (tenant cross-check, ADR-0108)
    Entry->>RR: updateIfNotTerminal(tenantId, runId, lambda -> CANCELLED)
    RR->>SM: validate(currentStatus, CANCELLED) inside the CAS
    alt not-terminal CAS succeeds
        RR-->>Entry: post-CAS Run = CANCELLED
        Entry-->>Actor: 200 OK
    else Run already terminal (different state) / lost the race
        RR-->>Entry: post-CAS Run = SUCCEEDED | FAILED | EXPIRED
        Entry-->>Actor: 409 illegal_state_transition
    end
```

The cancel-vs-complete race winner/loser ordering (the
`WHERE status NOT IN (CANCELLED, SUCCEEDED, FAILED, EXPIRED)` predicate, the
in-memory `computeIfPresent` equivalent, the 200-vs-409 outcome matrix) is the
[`../run-http-contract/process.md`](../run-http-contract/process.md) §4–§6
sequence; this spec names the CAS hop, that sink owns the race-resolution detail.

## 4. Class / Method Anchors (from facts)

| Role | Symbol | Fact id (+ method descriptor) |
|---|---|---|
| Entry (`saa.code_entrypoint_refs`) | `RunController.cancel` | `code-symbol/com-huawei-ascend-service-platform-web-runs-runcontroller#cancel(Ljava/lang/String;)Lorg/springframework/http/ResponseEntity;` |
| Atomic CAS (boundary SPI) | `RunRepository.updateIfNotTerminal` | `code-symbol/com-huawei-ascend-service-runtime-runs-spi-runrepository#updateIfNotTerminal(Ljava/lang/String;Ljava/util/UUID;Ljava/util/function/UnaryOperator;)Ljava/util/Optional;` |
| Transition guard | `RunStateMachine.validate` | `code-symbol/com-huawei-ascend-service-runtime-runs-runstatemachine#validate(Lcom/huawei/ascend/service/runtime/runs/RunStatus;Lcom/huawei/ascend/service/runtime/runs/RunStatus;)V` |
| Lookup (tenant cross-check) | `RunRepository.findById` | `code-symbol/com-huawei-ascend-service-runtime-runs-spi-runrepository#findById(Ljava/util/UUID;)Ljava/util/Optional;` |

The `saa.code_entrypoint_refs` source-path form on the DSL element
(`agent-service/.../RunController.java#cancel`) is the human pointer; the
`code-symbol/*` fact-id form above is the gate-resolvable citation. All fact ids
in this section resolve in
[`../../../facts/generated/code-symbols.json`](../../../facts/generated/code-symbols.json).

## 5. Error Paths

| Cause (observable) | Outcome | Status / signal | `error.code` / exception |
|---|---|---|---|
| Missing / invalid bearer token | rejected at auth edge | `401` | unauthenticated |
| `runId` not found, or owned by another tenant (tenant-scope-as-not-found) | rejected before the CAS | `404` | `not_found` |
| Run already in a *different* terminal state (lost the cancel-vs-complete race) | CAS predicate does not match | `409` | `illegal_state_transition` |

Every status code in this table appears in the `cancelRun` fact's
`response_status_codes` (`200`, `401`, `404`, `409` — see §6). The `404`
cross-tenant case is exercised by `RunHttpContractIT.cancelCrossTenantRunReturns404`;
the `409` different-terminal case by `RunHttpContractIT.cancelFailedRunReturns409`
and `cancelTerminalReturns409`; the idempotent same-status `200` by
`RunHttpContractIT.cancelPendingRunReturns200ThenIdempotent`.

## 6. Contracts

| Operation | Fact id | Method + path | Success | Status codes |
|---|---|---|---|---|
| `cancelRun` | `contract-op/cancelrun` | `POST /v1/runs/{runId}/cancel` | `200` (cancel applied / idempotent no-op) | `200`, `401`, `404`, `409` |

- The contract-op fact resolves in
  [`../../../facts/generated/contract-surfaces.json`](../../../facts/generated/contract-surfaces.json).
- The binding wire authority is the OpenAPI document
  ([`../../../../docs/contracts/openapi-v1.yaml`](../../../../docs/contracts/openapi-v1.yaml),
  operation `cancelRun`). This table is a readable interpretation of it; the
  full cancel outcome matrix is in
  [`../run-http-contract/process.md`](../run-http-contract/process.md) §6.

## 7. Tests

| Layer | Test class | Fact id | Covers |
|---|---|---|---|
| Integration / contract | `com.huawei.ascend.service.platform.web.runs.RunHttpContractIT` | `test/com-huawei-ascend-service-platform-web-runs-runhttpcontractit` | the cancel verb over the wire — `cancelPendingRunReturns200ThenIdempotent`, `cancelTerminalReturns409`, `cancelFailedRunReturns409`, `cancelCrossTenantRunReturns404`, `cancel_route_is_post_not_delete`. |
| Unit / domain | `com.huawei.ascend.service.runtime.runs.RunStateMachineTest` | `test/com-huawei-ascend-service-runtime-runs-runstatemachinetest` | the transition legality the in-CAS `validate(...)` enforces — `cancelled_is_terminal`, `run_with_status_rejects_illegal_transition`, `failed_cannot_go_to_cancelled_directly`. |

- Both fact ids resolve in
  [`../../../facts/generated/tests.json`](../../../facts/generated/tests.json).
- The authoring-DSL `verifies` edges for this FunctionPoint are in
  [`../../../features/verification.dsl`](../../../features/verification.dsl)
  (`testRunControllerCancelIT -> fpCancelRun`); this table is a readable view of
  those edges joined to the generated `test/*` facts.

## 8. Gates

| Concern | Gate rule / enforcer | What it blocks |
|---|---|---|
| FunctionPoint element well-formedness | Rule G-14 | a profile-tagged FP element missing a required `saa.*` property. |
| Frame anchors >= 1 FP (shipped) | Rule G-23 | promoting `EF-TASK-CONTROL` to `shipped` without anchoring >= 1 FunctionPoint. |
| Card / spec is a readable interpretation | Rule 146 / E196 | a citation here (`code-symbol/*`, `test/*`, `contract-op/*`, method descriptor) that does not resolve in the generated facts, or an FP/frame relationship not present in the DSL. |
| No L2 detail left upstream | Rule 145 / E194-E195 | the cancel-CAS / race / wire detail this spec carries being left in L0 / L1 prose instead. |
| FunctionPoint readiness | Rule 147 / E197 (kernel Rule G-30) | a FunctionPoint marked ready whose axis obligations (frame anchors + module implements; a Feature contains; contract-or-rationale + a resolving generated-fact ref + test-or-exception + a gate ref; a citeable normalized ADR view) are absent — `gate/lib/check_feature_readiness.py`, ADVISORY at the ADR-0159 §13.3 landing rung. |

---

## What stays upstream (NOT carried here)

- the L0 §4 *invariant* that cancel is a tenant-re-validated state transition (not
  a delete) — L0 owns the invariant; this spec owns the verb, the CAS hop, and the
  status codes;
- naming `RunController` / the Access Layer as a **boundary identity** and naming
  the Run aggregate's single-owner `RunRepository` SPI as the transition boundary
  (L1 / Frame Card material);
- the cancel-CAS atomic-primitive realization, the cancel-race winner/loser
  ordering, and the full outcome matrix — owned by the
  [`../run-http-contract/process.md`](../run-http-contract/process.md) §4–§6 sink
  and cited above;
- citing the ArchUnit / gate enforcer that pins the boundary (named in §8, not
  re-specified).

## Authority

- ADR-0068 — Layered 4+1 + Architecture Graph as twin sources of truth
  ([`../../../../docs/adr/0068-layered-4plus1-and-architecture-graph.yaml`](../../../../docs/adr/0068-layered-4plus1-and-architecture-graph.yaml)).
- ADR-0161 — EngineeringFrame package-cluster anchor + Card over DSL
  ([`../../../../docs/adr/0161-engineering-frame-package-cluster-anchor-and-card-over-dsl.yaml`](../../../../docs/adr/0161-engineering-frame-package-cluster-anchor-and-card-over-dsl.yaml)).
- ADR-0108 — tenant re-auth widening and graph isolation
  ([`../../../../docs/adr/0108-tenant-reauth-widening-and-graph-isolation.yaml`](../../../../docs/adr/0108-tenant-reauth-widening-and-graph-isolation.yaml)).
- ADR-0118 — atomic `updateIfNotTerminal` CAS
  ([`../../../../docs/adr/0118-rc38-audit-corrective-latent-correctness-and-deploy-packaging.yaml`](../../../../docs/adr/0118-rc38-audit-corrective-latent-correctness-and-deploy-packaging.yaml)).
- ADR-0142 — Run aggregate single owner
  ([`../../../../docs/adr/0142-run-aggregate-single-owner.yaml`](../../../../docs/adr/0142-run-aggregate-single-owner.yaml)).
- L2 corpus index: [`../README.md`](../README.md).
