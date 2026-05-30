---
level: L2
view: scenarios
feature: "fp-engine-dispatch"
status: active
relates_to:
  - architecture/docs/L1/frames/EF-ENGINE-REGISTRY.md
  - architecture/docs/L1/frames/EF-ENGINE-DISPATCH.md
  - architecture/features/function-points.dsl
  - docs/contracts/engine-envelope.v1.yaml
authority: "ADR-0068 (Layered 4+1 + Architecture Graph) + ADR-0072 (Engine Envelope + Strict Matching) + ADR-0140 (Engine Adapter Layer split) + ADR-0157 (EngineeringFrame Ontology)"
---

# L2 — `FP-ENGINE-DISPATCH` (resolve a typed engine envelope to its `ExecutorAdapter`)

This is the **L2 detail home for one shipped FunctionPoint**: `FP-ENGINE-DISPATCH`
(`saa.id` `FP-ENGINE-DISPATCH`, owner `agent-execution-engine`, source `ADR-0140`,
authored in [`../../../features/function-points.dsl`](../../../features/function-points.dsl)).
It carries the **method-level entry / participating-method / runtime-scenario**
detail for the dispatch verb — the C4-Component / arc42-L2 altitude that the L0
constraint corpus and the L1 frame cards deliberately do **not** carry.

The structural boundary that *owns* the types named below is the EngineeringFrame
[`EF-ENGINE-REGISTRY`](../../L1/frames/EF-ENGINE-REGISTRY.md) (`agent-execution-engine`,
`shipped`); its §6 FunctionPoint Mapping anchors this same FunctionPoint. This L2
file is the **FunctionPoint-keyed** scenario sink that frame card §6 delegates the
runtime sequence to ("the runtime sequence and the mismatch failure path are L2
detail, not restated here").

## Authority chain (read top-down)

This L2 file is a **readable interpretation layer**. It invents no IDs and no
relationships; every type, method descriptor, contract operation, and test below is
sourced from an upstream authority, in cascade order (generated facts > DSL >
Card/prose):

1. **Generated facts (binding)** —
   [`../../../facts/generated/code-symbols.json`](../../../facts/generated/code-symbols.json)
   (entry + participating types), 
   [`../../../facts/generated/contract-surfaces.json`](../../../facts/generated/contract-surfaces.json)
   (`contract-yaml/engine-envelope`), and
   [`../../../facts/generated/tests.json`](../../../facts/generated/tests.json)
   (the dispatch test inventory). Every `code-symbol/*`, `contract-yaml/*`, and
   `test/*` id below resolves in these files.
2. **Contract surface (schema authority)** —
   [`../../../../docs/contracts/engine-envelope.v1.yaml`](../../../../docs/contracts/engine-envelope.v1.yaml)
   (`contract-yaml/engine-envelope`): the single source of truth for the envelope
   shape and the `known_engines[]` set the registry resolves against.
3. **FunctionPoint declaration (DSL)** — `fpEngineDispatch` in
   [`../../../features/function-points.dsl`](../../../features/function-points.dsl)
   (`saa.status "shipped"`, `saa.owner "agent-execution-engine"`,
   `saa.sourceAdr "ADR-0140"`).
4. **Structural frame (Card)** —
   [`EF-ENGINE-REGISTRY`](../../L1/frames/EF-ENGINE-REGISTRY.md) §6 (the boundary
   that anchors this FunctionPoint); the sibling service-side frame
   [`EF-ENGINE-DISPATCH`](../../L1/frames/EF-ENGINE-DISPATCH.md) names the
   `design_only` service-side adapter-dispatch boundary, distinct from the shipped
   engine-module realization this FunctionPoint lives on.

## Resolved fact references

The `fpEngineDispatch` DSL element carries no `saa.code_entrypoint_refs` /
`saa.test_refs` / `saa.contract_op_refs` of its own (it predates the FunctionPoint
ref schema); they are resolved here from the anchoring frame card
[`EF-ENGINE-REGISTRY`](../../L1/frames/EF-ENGINE-REGISTRY.md) §6 and §7 and from the
generated facts. None is minted in this document.

| Role | Symbol | Fact ID |
|---|---|---|
| Entry class | `com.huawei.ascend.engine.runtime.EngineRegistry` | `code-symbol/com-huawei-ascend-engine-runtime-engineregistry` |
| Entry method | `EngineRegistry.resolve(EngineEnvelope) : ExecutorAdapter` | `code-symbol/com-huawei-ascend-engine-runtime-engineregistry#resolve(Lcom/huawei/ascend/engine/runtime/EngineEnvelope;)Lcom/huawei/ascend/engine/spi/ExecutorAdapter;` |
| Envelope value | `com.huawei.ascend.engine.runtime.EngineEnvelope` | `code-symbol/com-huawei-ascend-engine-runtime-engineenvelope` |
| Resolved SPI | `com.huawei.ascend.engine.spi.ExecutorAdapter` | `code-symbol/com-huawei-ascend-engine-spi-executoradapter` |
| Mismatch signal | `com.huawei.ascend.engine.spi.EngineMatchingException` | `code-symbol/com-huawei-ascend-engine-spi-enginematchingexception` |
| In-process port | `com.huawei.ascend.engine.runtime.InProcessEnginePort` | `code-symbol/com-huawei-ascend-engine-runtime-inprocessengineport` |
| Contract surface | `docs/contracts/engine-envelope.v1.yaml` | `contract-yaml/engine-envelope` |
| Test (typed dispatch + mismatch) | `EngineRegistryResolveTest` | `test/com-huawei-ascend-engine-runtime-engineregistryresolvetest` |
| Test (no-fallback strictness) | `EngineMatchingStrictnessIT` | `test/com-huawei-ascend-engine-runtime-enginematchingstrictnessit` |
| Test (registry-only dispatch) | `EnginePayloadDispatchOnlyViaRegistryTest` | `test/com-huawei-ascend-engine-runtime-enginepayloaddispatchonlyviaregistrytest` |
| Test (mismatch → Run FAILED) | `EngineMismatchTransitionsRunToFailedIT` | `test/com-huawei-ascend-engine-runtime-enginemismatchtransitionsruntofailedit` |
| Test (envelope validation) | `EngineEnvelopeValidationTest` | `test/com-huawei-ascend-engine-runtime-engineenvelopevalidationtest` |
| Test (registry boot validation) | `EngineRegistryBootValidationIT` | `test/com-huawei-ascend-service-platform-engine-engineregistrybootvalidationit` |

## Entry method

**`EngineRegistry.resolve(EngineEnvelope) : ExecutorAdapter`**
(fact
`code-symbol/com-huawei-ascend-engine-runtime-engineregistry#resolve(Lcom/huawei/ascend/engine/runtime/EngineEnvelope;)Lcom/huawei/ascend/engine/spi/ExecutorAdapter;`).

This is the single, strict-matching dispatch entry. Given an immutable
`EngineEnvelope` value (carrying at least `engineType` and an opaque `payload`),
`resolve` returns the **one** `ExecutorAdapter` registered for
`envelope.engineType()`, or raises `EngineMatchingException` when no adapter is
registered for that type. It performs no fallback, no nearest-match, and no
`instanceof` ladder over `ExecutorDefinition` subtypes — the registry is the only
place engine selection happens (the invariant enforcer `E74` holds). The
`known_engines[]` membership it resolves against is the
`contract-yaml/engine-envelope` schema, asserted at boot by `validateAgainstSchema()`.

## Participating methods

Resolution composes with the following methods, all on types already cited above
(JVM descriptors are the generated `public_methods[]` entries):

- **`EngineEnvelope.of(String, String, ExecutorDefinition) : EngineEnvelope`** /
  the canonical constructor — builds the immutable envelope value carried into
  `resolve` (`code-symbol/com-huawei-ascend-engine-runtime-engineenvelope`,
  `engineType()` / `payload()` accessors supply the resolution key + opaque body).
- **`EngineRegistry.register(ExecutorAdapter) : EngineRegistry`** — the boot-time
  registration that populates the engine-type → adapter table `resolve` reads;
  rejects a blank `engineType()` or a duplicate registration.
- **`EngineRegistry.resolveByEngineType(String) : ExecutorAdapter`** and
  **`EngineRegistry.resolveByPayload(ExecutorDefinition) : ExecutorAdapter`** — the
  two convenience siblings that funnel into the same strict-match table for
  call-sites holding only a type string or only a payload; each raises the same
  `EngineMatchingException` on a miss.
- **`EngineRegistry.validateAgainstSchema() : void`** /
  **`validateAgainstSchema(String) : void`** — the boot self-validation that asserts
  every `known_engines[].id` has a registered adapter and every registered adapter's
  `engineType()` is a known engine (the bidirectional consistency enforcer `E77`
  holds; `EngineRegistryBootValidationIT` covers it).
- **`ExecutorAdapter.execute(RunContext, ExecutorDefinition, Object) : Object`** — the
  resolved adapter's invocation surface the caller drives *after* `resolve` returns
  (`code-symbol/com-huawei-ascend-engine-spi-executoradapter`); `engineType()` and
  `payloadType()` are the adapter-side identity `resolve` matches against.
- **`InProcessEnginePort.execute(ExecutionContext, ExecuteRequest) : Flow.Publisher`**
  (`code-symbol/com-huawei-ascend-engine-runtime-inprocessengineport`) — the
  in-process realization of the bus `EnginePort` that wraps a `resolve` →
  `ExecutorAdapter.execute` call into the neutral engine boundary stream; the
  EnginePort wire detail itself lives in the
  [`../engine-port-boundary/`](../engine-port-boundary/) L2 sink, referenced here
  only for the dispatch call-site.
- **`EngineMatchingException(String, String, String)`** — the strict-mismatch signal
  carrying `requestedEngineType()` + `actualPayloadType()`; the caller maps it to
  `RunStatus.FAILED` with reason `engine_mismatch` (proven by
  `EngineMismatchTransitionsRunToFailedIT`). The Run-state mapping mechanics are the
  orchestrator's; this file documents only the dispatch-side raise.

## Runtime scenario

```
Orchestrator                EngineRegistry            ExecutorAdapter
     |                            |                         |
     | build EngineEnvelope       |                         |
     |   (engineType, payload)    |                         |
     |--------------------------->|  resolve(envelope)      |
     |                            |--- lookup adaptersByEngineType[engineType]
     |                            |                         |
     |       (match)              |   return adapter        |
     |<---------------------------|                         |
     |  adapter.execute(runCtx, definition, input)          |
     |----------------------------------------------------->|
     |                            |        result / stream  |
     |<-----------------------------------------------------|
```

1. **Build** — an orchestrator (or the in-process `EnginePort` realization)
   constructs an immutable `EngineEnvelope` for the Run's engine type and opaque
   `ExecutorDefinition` payload.
2. **Resolve** — `resolve(envelope)` looks up the registered `ExecutorAdapter` for
   `envelope.engineType()` in the boot-built table.
   - **Match** → returns the single adapter; dispatch is `instanceof`-free
     (enforcer `E74`).
   - **Miss** → raises `EngineMatchingException` (`engine_mismatch`); no fallback,
     no silent reinterpretation (enforcer `E75`; `EngineMatchingStrictnessIT`). The
     calling Run transitions to `RunStatus.FAILED` with the mismatch reason
     (`EngineMismatchTransitionsRunToFailedIT`).
3. **Invoke** — the caller drives `ExecutorAdapter.execute(runContext, definition,
   input)` on the resolved adapter, advancing the Run state machine. The cross-cutting
   `HookPoint` lifecycle the adapter fires during execution is a *separate*
   FunctionPoint — [`../fp-hook-dispatch/`](../fp-hook-dispatch/).

### Boot pre-condition

Before the first dispatch, `validateAgainstSchema()` runs once at registry boot and
fails fast (raising `IllegalStateException`) unless the registered adapter set and
the `known_engines[]` block of `engine-envelope.v1.yaml` agree both ways
(`EngineRegistryBootValidationIT`, enforcer `E77`).

## What stays upstream (NOT carried here)

Per the layer-purity verdict, the following remain at L0 / L1 / contract and are only
*referenced* here, never duplicated:

- the L0 §4 constraint that a strict heterogeneous-engine contract exists (the
  *invariant*; L0 owns it, this file owns the method sequence);
- the `EF-ENGINE-REGISTRY` boundary identity — naming `EngineRegistry` /
  `EngineEnvelope` / `InProcessEnginePort` as the registry boundary, and the
  development-view package decomposition `com.huawei.ascend.engine.runtime`
  (frame card §2–§3);
- citing the ArchUnit / gate enforcers (`E74`–`E77`) that pin the boundary (frame
  card §7);
- the **envelope wire shape** and the `known_engines[]` registry — owned by
  `engine-envelope.v1.yaml`; this file cites the operation fact, it does not restate
  the schema;
- the neutral `EnginePort` over-the-wire suspend/resume mechanics — owned by the
  [`../engine-port-boundary/`](../engine-port-boundary/) L2 sink (ADR-0158).

## Gate behaviour

- Rule 37 (`architecture_artefact_front_matter`): this file declares `level: L2` +
  `view: scenarios`.
- Rule 38 (`architecture_graph_well_formed`): the `relates_to:` front-matter links
  upward to the anchoring frame card and the contract surface; the regenerated graph
  is owned by the reconcile/governance wave, not by this document.

## Authority

- ADR-0068 — Layered 4+1 + Architecture Graph as twin sources of truth
  ([`../../../../docs/adr/0068-layered-4plus1-and-architecture-graph.yaml`](../../../../docs/adr/0068-layered-4plus1-and-architecture-graph.yaml)).
- ADR-0072 — Engine Envelope + Strict Matching
  ([`../../../../docs/contracts/engine-envelope.v1.yaml`](../../../../docs/contracts/engine-envelope.v1.yaml) is its SSOT).
- ADR-0140 — Engine Adapter Layer split (the FunctionPoint's `saa.sourceAdr`)
  ([`../../../../docs/adr/0140-engine-adapter-layer-split.yaml`](../../../../docs/adr/0140-engine-adapter-layer-split.yaml)).
- ADR-0157 — EngineeringFrame Ontology (`EF-ENGINE-REGISTRY` structural anchor).
- Structural anchor frame: [`EF-ENGINE-REGISTRY`](../../L1/frames/EF-ENGINE-REGISTRY.md).
- L2 corpus index: [`../README.md`](../README.md).
