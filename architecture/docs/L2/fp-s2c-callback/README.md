---
level: L2
view: scenarios
status: active
feature: "fp-s2c-callback"
relates_to:
  - "architecture/features/function-points.dsl"
  - "architecture/features/engineering-frames.dsl"
  - "architecture/docs/L1/frames/EF-S2C-TRANSPORT.md"
  - "architecture/docs/L1/agent-bus/ARCHITECTURE.md"
  - "docs/contracts/s2c-callback.v1.yaml"
authority: "ADR-0068 (Layered 4+1 + Architecture Graph) + ADR-0161 (EngineeringFrame package-cluster anchor + Card over DSL) + ADR-0088 (S2C transport relocation to agent-bus) + ADR-0074 (S2C Capability Callback Protocol) + ADR-0157 (EngineeringFrame Ontology)"
---

# L2 FunctionPoint Spec — `FP-S2C-CALLBACK` (Server-to-Client Callback)

This is the **L2 technical-detail home** for the single FunctionPoint
`FP-S2C-CALLBACK`: the server-to-client callback path by which a suspended Run
hands a capability invocation to its client and resumes on the client's response.
It carries the dispatch method contract, the suspend/resume runtime sequence, the
failure transitions, and the test evidence that the layer-purity verdict ruled
does **not** belong in L0 / L1 prose (Rule 145 / E194-E195).

> **This document is a READABLE INTERPRETATION layer (Rule 146 / E196).** It
> invents no FunctionPoint ID, frame ID, type name, method descriptor, error
> code, or status value. Every identity is copied from the authoring DSL; every
> code/test/contract fact is cited from the generated facts. Cascade on
> disagreement: `generated facts > DSL > Card/prose`.

## Authority chain (read top-down)

1. **FunctionPoint identity (authoring DSL)** — element `fpS2cCallback`
   (`saa.id` = `FP-S2C-CALLBACK`) in
   [`../../../features/function-points.dsl`](../../../features/function-points.dsl).
   Its `saa.status`, `saa.channel`, `saa.actor`, `saa.trigger`, `saa.requirement`,
   and `saa.sourceAdr` are copied verbatim into §1.
2. **Owning EngineeringFrame (structural parent)** — `EF-S2C-TRANSPORT`
   (`efS2cTransport`, owner `agent-bus`) holds the `anchors` edge
   `efS2cTransport -> fpS2cCallback` in
   [`../../../features/engineering-frames.dsl`](../../../features/engineering-frames.dsl).
   Frame Card: [`../../L1/frames/EF-S2C-TRANSPORT.md`](../../L1/frames/EF-S2C-TRANSPORT.md).
3. **Generated facts (binding factual authority)** — the `code-symbol/*` and
   `test/*` facts cited in §4 / §7 resolve in
   [`../../../facts/generated/code-symbols.json`](../../../facts/generated/code-symbols.json)
   and [`../../../facts/generated/tests.json`](../../../facts/generated/tests.json).
4. **Contract surface (binding wire / SPI authority)** — the schema contract
   [`../../../../docs/contracts/s2c-callback.v1.yaml`](../../../../docs/contracts/s2c-callback.v1.yaml)
   (`contract-yaml/s2c-callback`), Authority ADR-0074; `status: runtime_enforced`.
5. **L0 constraint authority** — the L0 §4 cross-plane control-surface constraint
   that names the S2C boundary without carrying its dispatch / suspend mechanics.
   This spec carries the detail; L0 keeps the invariant.

---

## 1. Behavior

`FP-S2C-CALLBACK` realizes the **server-to-client capability callback**: when a
Run needs a client-side capability invocation, the runtime emits one
`S2cCallbackEnvelope` through the `S2cCallbackTransport` SPI; the calling Run
suspends, and the client's `S2cCallbackResponse` (validated against
`s2c-callback.v1.yaml`) resumes the Run. The transport SPI lives in `agent-bus`
to keep the service free of edge-direction transport concerns; the envelope
schema is the runtime promise surface (ADR-0074 / ADR-0088).

On the value axis this FunctionPoint serves
`PC-004 -> REQ-003 -> FEAT-SERVER-CLIENT-CALLBACK -> FP-S2C-CALLBACK`; on the
structural axis it is `agent-bus -> EF-S2C-TRANSPORT -> FP-S2C-CALLBACK`.

| Field | Value (copied from the DSL element) |
|---|---|
| FunctionPoint ID | `FP-S2C-CALLBACK` |
| Status | `shipped` (`saa.status`) |
| Owning EngineeringFrame | `EF-S2C-TRANSPORT` (the `anchors` parent) |
| Owner module | `agent-bus` (`saa.owner`) |
| Requirement | `REQ-003` (`saa.requirement`) |
| Channel | `internal` (`saa.channel`) |
| Actor | `platform-runtime` (`saa.actor`) |
| Trigger | `internal-orchestration-event` (`saa.trigger`) |
| Source ADR | `ADR-0088` (`saa.sourceAdr`) |
| Value-axis Feature | `FEAT-SERVER-CLIENT-CALLBACK` (`requires` edge, owner `agent-bus`) |

> **Boundary vs orchestration.** This FunctionPoint's frame owns the **transport
> SPI** only — the `dispatch` boundary and the request/response value envelopes.
> Catching the client-callback `SuspendSignal`, transitioning the Run to
> `SUSPENDED` / `FAILED`, firing the error hook, and resuming on the response is
> orchestration owned by `EF-TASK-CONTROL` (agent-service); the in-process
> reference transport `InMemoryS2cCallbackTransport` is owned by agent-service,
> outside this frame's package. This spec names those collaborators (§3–§4) but
> does not re-specify the orchestrator's state machine.

## 2. I/O

- **Input** — `S2cCallbackEnvelope`
  (`code-symbol/com-huawei-ascend-bus-spi-s2c-s2ccallbackenvelope`), an immutable
  record carrying the callback id (primary correlation key), the suspending
  `serverRunId`, the declared `capabilityRef`, the opaque request payload, the
  trace id (must equal the suspending `Run.traceId`), the idempotency key, an
  optional deadline, and request attributes. Construction validates the identity /
  correlation field set.
- **Output (success)** — a `CompletionStage` from
  `S2cCallbackTransport.dispatch(...)` that completes with `S2cCallbackResponse`
  (`code-symbol/com-huawei-ascend-bus-spi-s2c-s2ccallbackresponse`), whose closed
  outcome enumeration is `S2cCallbackResponse$Outcome`
  (`code-symbol/com-huawei-ascend-bus-spi-s2c-s2ccallbackresponse-outcome`). The
  response record exposes one factory per outcome — `ok(...)`, `error(...)`,
  `timeout(...)` (cited in §4) — pinning a typed, closed result.
- **Side effects** — the calling Run **suspends** (via the client-callback
  `SuspendSignal`) and later **resumes** on the validated response, or transitions
  to `FAILED` on a failure outcome. These Run-state writes cross the
  `EF-TASK-CONTROL` boundary and are owned by the orchestrator, not by this SPI;
  this spec names the transition, it does not inline the persistence.

## 3. Runtime Sequence

```mermaid
sequenceDiagram
    autonumber
    participant Run as Suspending Run (orchestrator, EF-TASK-CONTROL)
    participant Transport as S2cCallbackTransport.dispatch
    participant Client as Client capability endpoint
    participant Resp as S2cCallbackResponse (ok / error / timeout)

    Run->>Transport: dispatch(S2cCallbackEnvelope)
    Note over Run: Run suspends (client-callback SuspendSignal); no busy-wait
    Transport->>Client: deliver capability invocation
    Client-->>Transport: client result
    Transport-->>Resp: complete CompletionStage with S2cCallbackResponse
    Resp-->>Run: validated response resumes Run (ok) — or transitions Run to FAILED (error / timeout)
```

The single boundary hop owned by this frame is `dispatch`; the
suspend/transition/resume choreography around it is the orchestrator's and is
referenced here only for the sequence's shape.

## 4. Class / Method Anchors (from facts)

| Role | Symbol | Fact id (+ method descriptor) |
|---|---|---|
| Entry SPI (dispatch entrypoint) | `S2cCallbackTransport.dispatch` | `code-symbol/com-huawei-ascend-bus-spi-s2c-s2ccallbacktransport#dispatch(Lcom/huawei/ascend/bus/spi/s2c/S2cCallbackEnvelope;)Ljava/util/concurrent/CompletionStage;` |
| Request envelope (type) | `S2cCallbackEnvelope` | `code-symbol/com-huawei-ascend-bus-spi-s2c-s2ccallbackenvelope` |
| Response envelope (type) | `S2cCallbackResponse` | `code-symbol/com-huawei-ascend-bus-spi-s2c-s2ccallbackresponse` |
| Response: ok factory | `S2cCallbackResponse.ok` | `code-symbol/com-huawei-ascend-bus-spi-s2c-s2ccallbackresponse#ok(Ljava/util/UUID;Ljava/lang/String;Ljava/lang/Object;)Lcom/huawei/ascend/bus/spi/s2c/S2cCallbackResponse;` |
| Response: error factory | `S2cCallbackResponse.error` | `code-symbol/com-huawei-ascend-bus-spi-s2c-s2ccallbackresponse#error(Ljava/util/UUID;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Lcom/huawei/ascend/bus/spi/s2c/S2cCallbackResponse;` |
| Response: timeout factory | `S2cCallbackResponse.timeout` | `code-symbol/com-huawei-ascend-bus-spi-s2c-s2ccallbackresponse#timeout(Ljava/util/UUID;Ljava/lang/String;)Lcom/huawei/ascend/bus/spi/s2c/S2cCallbackResponse;` |
| Outcome alphabet (type) | `S2cCallbackResponse$Outcome` | `code-symbol/com-huawei-ascend-bus-spi-s2c-s2ccallbackresponse-outcome` |
| Reference transport (impl, agent-service) | `InMemoryS2cCallbackTransport` | `code-symbol/com-huawei-ascend-service-runtime-s2c-inmemorys2ccallbacktransport` |

The DSL element declares no `saa.code_entrypoint_refs`; the entry above is the SPI
dispatch method the owning frame anchors (`efS2cTransport -> fpS2cCallback`),
copied from the Frame Card §6 and resolving in
[`../../../facts/generated/code-symbols.json`](../../../facts/generated/code-symbols.json).

## 5. Error Paths

Non-success outcomes are carried in the closed `S2cCallbackResponse$Outcome` set;
on a failure outcome the orchestrator transitions the suspending Run to `FAILED`
with a typed reason (held by the FAILED-transition enforcer, §8).

| Cause (observable) | Outcome | Status / signal | `error.code` / outcome |
|---|---|---|---|
| Client reports a capability error | `error` response → Run `FAILED` | typed-reason FAILED transition | `S2cCallbackResponse.error(...)`, outcome `error` |
| Client does not respond within the deadline | `timeout` response → Run `FAILED` | orchestrator-tripped timeout | `S2cCallbackResponse.timeout(...)`, outcome `timeout` |
| Malformed envelope (missing correlation / trace field) | rejected at boundary | construction failure | envelope construction invariant |
| Client responds successfully | `ok` response → Run resumes | resume signal | `S2cCallbackResponse.ok(...)`, outcome `ok` |

The closed outcome alphabet `{ok, error, timeout}` is the contract's
`outcome_values` (cited as a type in §4); it is not minted here.

## 6. Contracts

`FP-S2C-CALLBACK` travels on an **internal** channel; it has no HTTP route and
therefore no `contract-op/*`. Its envelope / response shape is pinned by a schema
contract.

| Operation | Fact id | Surface | Status |
|---|---|---|---|
| S2C callback envelope + response schema | `contract-yaml/s2c-callback` | `docs/contracts/s2c-callback.v1.yaml` | `runtime_enforced` |

- The binding wire authority is the schema contract document itself
  ([`../../../../docs/contracts/s2c-callback.v1.yaml`](../../../../docs/contracts/s2c-callback.v1.yaml),
  Authority ADR-0074); this table is a readable interpretation of it. The Java
  records under `com.huawei.ascend.bus.spi.s2c` mirror that schema and validate
  required fields on construction. The request field set, the closed
  `outcome_values` `{ok, error, timeout}`, and the W3C trace-id character class are
  contract material asserted by the enforcers in §8.
- The contract for this internal boundary **is** the owning frame's SPI type
  `S2cCallbackTransport` (cited in §4).

## 7. Tests

The DSL element declares no `saa.test_refs`; the verification evidence for this
boundary is the test-fact set the Frame Card carries, each resolving in
[`../../../facts/generated/tests.json`](../../../facts/generated/tests.json).

| Layer | Test class | Fact id | Covers |
|---|---|---|---|
| Unit / domain | `S2cCallbackEnvelopeLibraryTest` | `test/com-huawei-ascend-bus-spi-s2c-s2ccallbackenvelopelibrarytest` | the in-package envelope identity / correlation invariants on construction. |
| Integration / contract | `S2cCallbackRoundTripIT` | `test/com-huawei-ascend-service-runtime-s2c-s2ccallbackroundtripit` | the happy-path callback round trip + parent Run resume on `ok`. |
| Integration / contract | `S2cCallbackEnvelopeValidationTest` | `test/com-huawei-ascend-service-runtime-s2c-s2ccallbackenvelopevalidationtest` | envelope validation against the `s2c-callback.v1.yaml` required-field set. |
| Integration / contract | `S2cFailureTransitionsRunToFailedIT` | `test/com-huawei-ascend-service-runtime-s2c-s2cfailuretransitionsruntofailedit` | failure outcomes (`error`) transitioning the Run to `FAILED` with a typed reason. |
| Integration / contract | `S2cTransportTimeoutTrippedByOrchestratorTest` | `test/com-huawei-ascend-service-runtime-s2c-s2ctransporttimeouttrippedbyorchestratortest` | the orchestrator-tripped `timeout` outcome path. |
| Architecture / enforcer | `S2cCallbackRespectsRule38Test` | `test/com-huawei-ascend-service-runtime-s2c-s2ccallbackrespectsrule38test` | the S2C SPI + reference impl never busy-wait (no `Thread.sleep`); the wait routes through suspend/checkpoint. |

Per-test asserted behaviour lives with these test facts'
`test_methods[]`; this table is a readable view of the `verifies`-adjacent
evidence, not a re-inventory.

## 8. Gates

| Concern | Gate rule / enforcer | What it blocks |
|---|---|---|
| SPI purity (framework-free boundary) | `SpiPurityGeneralizedArchTest` (enforcer `E93`) | a class under `com.huawei.ascend.bus.spi.s2c` importing anything beyond `java.*` + same-SPI-package siblings. |
| No busy-wait in the S2C wait | `S2cCallbackRespectsRule38Test` (enforcer `E83`) | the S2C SPI or reference impl calling `Thread.sleep(...)` instead of suspend/checkpoint. |
| Schema contract present + well-formed | `s2c_callback_yaml_present_and_wellformed` (enforcer `E81`) | the `s2c-callback.v1.yaml` schema being missing or malformed (required fields, closed `outcome_values`). |
| Failure transitions Run to FAILED | FAILED-transition enforcer (`E90`) + round-trip enforcer (`E82`) | a failure outcome not transitioning the Run to `FAILED` with a typed reason. |
| Frame anchors >= 1 FP (shipped) | Rule G-23 (enforcer `E188`) | promoting `EF-S2C-TRANSPORT` to `shipped` without the `anchors` edge to `FP-S2C-CALLBACK`. |
| Card / spec is a readable interpretation | Rule 146 / E196 | a citation here (`code-symbol/*`, `test/*`, method descriptor) that does not resolve, or an FP/frame relationship absent from the DSL. |
| No L2 detail left upstream | Rule 145 / E194-E195 | the dispatch method / suspend sequence / envelope detail this spec carries being left in L0 / L1 prose. |

---

## What stays upstream (NOT carried here)

Per the layer-purity keep-list, the following remain at L0 / L1 and are only
*referenced* here, never duplicated (Rule 145):

- the L0 §4 cross-plane control-surface *invariant* (the S2C direction is a
  distinct boundary from C2S ingress) — L0 owns the invariant; this spec owns the
  dispatch method, the suspend/resume sequence, and the response outcomes;
- naming `S2cCallbackTransport` / `com.huawei.ascend.bus.spi.s2c` as the boundary
  identity and the development-view package decomposition of `agent-bus` (Frame
  Card / L1 material);
- the orchestrator's Run state machine (`SuspendSignal` catch, `SUSPENDED` /
  `FAILED` transitions, resume) — that is `EF-TASK-CONTROL` material, named here
  only for sequence shape;
- citing the ArchUnit / gate enforcer that pins the boundary (named in §8, not
  re-specified).

## Authority

- ADR-0068 — Layered 4+1 + Architecture Graph as twin sources of truth.
- ADR-0074 — S2C Capability Callback Protocol (the envelope/response contract).
- ADR-0088 — S2C transport relocation to `agent-bus` (the boundary's structural home).
- ADR-0157 — EngineeringFrame Ontology (`EF-S2C-TRANSPORT` structural anchor).
- ADR-0161 — EngineeringFrame package-cluster anchor + Card over DSL.
- Rule 33 — Layered 4+1 Discipline; Rule 145 — L2 detail sink; Rule 146 — Frame
  Card / FunctionPoint-spec is a readable interpretation (`CLAUDE.md`).
- L2 corpus index: [`../README.md`](../README.md).
