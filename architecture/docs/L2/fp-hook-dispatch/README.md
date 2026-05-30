---
level: L2
view: scenarios
feature: "fp-hook-dispatch"
status: active
relates_to:
  - architecture/docs/L1/frames/EF-HOOK-SURFACE.md
  - architecture/features/function-points.dsl
  - docs/contracts/engine-hooks.v1.yaml
authority: "ADR-0068 (Layered 4+1 + Architecture Graph) + ADR-0073 (Engine Hooks + Runtime-Owned Middleware SPI) + ADR-0157 (EngineeringFrame Ontology)"
---

# L2 — `FP-HOOK-DISPATCH` (a `RuntimeMiddleware` chain observes a Run dispatch at a canonical `HookPoint`)

This is the **L2 detail home for one shipped FunctionPoint**: `FP-HOOK-DISPATCH`
(`saa.id` `FP-HOOK-DISPATCH`, owner `agent-middleware`, source `ADR-0073`, authored
in [`../../../features/function-points.dsl`](../../../features/function-points.dsl)).
It carries the **method-level entry / participating-method / runtime-scenario**
detail for the hook-dispatch verb — the C4-Component / arc42-L2 altitude that the L0
constraint corpus and the L1 frame card deliberately do **not** carry ("the exact
firing order, fail-fast semantics, and outcome handling are runtime mechanics — see
the contract surface and the frame's L2 sink, not this Card", `EF-HOOK-SURFACE` §5).

The structural boundary that *owns* the types named below is the EngineeringFrame
[`EF-HOOK-SURFACE`](../../L1/frames/EF-HOOK-SURFACE.md) (`agent-middleware`,
`shipped`); its §6 FunctionPoint Mapping anchors this same FunctionPoint. This L2
file is the **FunctionPoint-keyed** scenario sink frame card §6 delegates the
runtime sequence to.

## Authority chain (read top-down)

This L2 file is a **readable interpretation layer**. It invents no IDs and no
relationships; every type, method descriptor, contract operation, and test below is
sourced from an upstream authority, in cascade order (generated facts > DSL >
Card/prose):

1. **Generated facts (binding)** —
   [`../../../facts/generated/code-symbols.json`](../../../facts/generated/code-symbols.json)
   (entry + participating types),
   [`../../../facts/generated/contract-surfaces.json`](../../../facts/generated/contract-surfaces.json)
   (`contract-yaml/engine-hooks`), and
   [`../../../facts/generated/tests.json`](../../../facts/generated/tests.json) (the
   hook test inventory). Every `code-symbol/*`, `contract-yaml/*`, and `test/*` id
   below resolves in these files.
2. **Contract surface (schema authority)** —
   [`../../../../docs/contracts/engine-hooks.v1.yaml`](../../../../docs/contracts/engine-hooks.v1.yaml)
   (`contract-yaml/engine-hooks`): the single source of truth for the canonical
   `hooks:` list, the `ordering`, and the `failure_propagation` semantics the
   `HookPoint` enum mirrors (Rule 57 holds yaml ↔ enum consistency).
3. **FunctionPoint declaration (DSL)** — `fpHookDispatch` in
   [`../../../features/function-points.dsl`](../../../features/function-points.dsl)
   (`saa.status "shipped"`, `saa.owner "agent-middleware"`,
   `saa.sourceAdr "ADR-0073"`).
4. **Structural frame (Card)** —
   [`EF-HOOK-SURFACE`](../../L1/frames/EF-HOOK-SURFACE.md) §6 (the boundary that
   anchors this FunctionPoint, with the entry / SPI-participant / carrier fact ids).

## Resolved fact references

The `fpHookDispatch` DSL element carries no `saa.code_entrypoint_refs` /
`saa.test_refs` / `saa.contract_op_refs` of its own (it predates the FunctionPoint
ref schema); they are resolved here from the anchoring frame card
[`EF-HOOK-SURFACE`](../../L1/frames/EF-HOOK-SURFACE.md) §6 and from the generated
facts. None is minted in this document.

| Role | Symbol | Fact ID |
|---|---|---|
| Entry class | `com.huawei.ascend.middleware.HookDispatcher` | `code-symbol/com-huawei-ascend-middleware-hookdispatcher` |
| Entry method | `HookDispatcher.fire(HookContext) : HookOutcome` | `code-symbol/com-huawei-ascend-middleware-hookdispatcher#fire(Lcom/huawei/ascend/middleware/spi/HookContext;)Lcom/huawei/ascend/middleware/spi/HookOutcome;` |
| SPI participant | `com.huawei.ascend.middleware.spi.RuntimeMiddleware` | `code-symbol/com-huawei-ascend-middleware-spi-runtimemiddleware#onHook(Lcom/huawei/ascend/middleware/spi/HookContext;)Lcom/huawei/ascend/middleware/spi/HookOutcome;` |
| Lifecycle taxonomy | `com.huawei.ascend.middleware.spi.HookPoint` | `code-symbol/com-huawei-ascend-middleware-spi-hookpoint` |
| Input carrier | `com.huawei.ascend.middleware.spi.HookContext` | `code-symbol/com-huawei-ascend-middleware-spi-hookcontext` |
| Result carrier | `com.huawei.ascend.middleware.spi.HookOutcome` | `code-symbol/com-huawei-ascend-middleware-spi-hookoutcome` |
| Result — proceed | `HookOutcome.Proceed` | `code-symbol/com-huawei-ascend-middleware-spi-hookoutcome-proceed` |
| Result — short-circuit | `HookOutcome.ShortCircuit` | `code-symbol/com-huawei-ascend-middleware-spi-hookoutcome-shortcircuit` |
| Result — fail | `HookOutcome.Fail` | `code-symbol/com-huawei-ascend-middleware-spi-hookoutcome-fail` |
| Contract surface | `docs/contracts/engine-hooks.v1.yaml` | `contract-yaml/engine-hooks` |
| Test (declared-order fail-fast) | `HookDispatcherFireOrderTest` | `test/com-huawei-ascend-middleware-hookdispatcherfireordertest` |
| Test (middleware intercepts hooks) | `RuntimeMiddlewareInterceptsHooksIT` | `test/com-huawei-ascend-engine-runtime-runtimemiddlewareinterceptshooksit` |
| Test (every engine declares hook surface) | `EveryEngineDeclaresHookSurfaceTest` | `test/com-huawei-ascend-engine-runtime-everyenginedeclareshooksurfacetest` |
| Test (carrier immutability) | `SpiCarrierImmutabilityTest` | `test/com-huawei-ascend-middleware-spi-spicarrierimmutabilitytest` |

## Entry method

**`HookDispatcher.fire(HookContext) : HookOutcome`**
(fact
`code-symbol/com-huawei-ascend-middleware-hookdispatcher#fire(Lcom/huawei/ascend/middleware/spi/HookContext;)Lcom/huawei/ascend/middleware/spi/HookOutcome;`).

`fire` dispatches one `HookContext` (carrying the `HookPoint`, the Run id, the tenant
id, and an attribute map) across the dispatcher's ordered, immutable
`RuntimeMiddleware` list and returns the aggregated `HookOutcome`. The dispatcher is
constructed once from a middleware list (or `HookDispatcher.empty()` when none is
registered, used by the registry); the list is unmodifiable after construction. An
empty chain returns `HookOutcome.proceed()` directly.

## Participating methods

- **`HookContext.of(HookPoint, UUID, String) : HookContext`** /
  the canonical constructor — builds the immutable per-fire input carrier
  (`code-symbol/com-huawei-ascend-middleware-spi-hookcontext`); `point()` selects the
  ordering + failure-propagation policy, `runId()` / `tenantId()` / `attributes()`
  carry the per-fire context.
- **`RuntimeMiddleware.onHook(HookContext) : HookOutcome`**
  (`code-symbol/com-huawei-ascend-middleware-spi-runtimemiddleware`) — the single SPI
  method each registered cross-cutting policy implements; `fire` invokes it once per
  middleware per fire. A `null` return is normalized to `HookOutcome.proceed()`; a
  thrown `RuntimeException` is caught and converted to
  `HookOutcome.Fail("middleware_threw:<Exception>")` so a misbehaving middleware
  never tears down the orchestrator.
- **`HookOutcome.proceed() : HookOutcome`** — the neutral outcome; the three permitted
  shapes are `HookOutcome.Proceed`
  (`code-symbol/com-huawei-ascend-middleware-spi-hookoutcome-proceed`),
  `HookOutcome.ShortCircuit`
  (`code-symbol/com-huawei-ascend-middleware-spi-hookoutcome-shortcircuit`), and
  `HookOutcome.Fail` (`code-symbol/com-huawei-ascend-middleware-spi-hookoutcome-fail`).
- **`HookPoint.values()` / `HookPoint.valueOf(String)`**
  (`code-symbol/com-huawei-ascend-middleware-spi-hookpoint`) — the canonical
  lifecycle-point taxonomy; the enum constants mirror the `hooks:` list of
  `engine-hooks.v1.yaml` one-to-one (Rule 57 / enforcer `E78`).

## Runtime scenario

```
Engine / Orchestrator        HookDispatcher                 RuntimeMiddleware[]
        |                          |                                 |
        | build HookContext        |                                 |
        |  (point, runId, tenant)  |                                 |
        |------------------------->|  fire(ctx)                      |
        |                          |-- empty? -> proceed()           |
        |                          |-- point == ON_ERROR -> best_effort
        |                          |-- else -> ordered fail-fast     |
        |                          |     for each mw in order:       |
        |                          |--------------------------------->| onHook(ctx)
        |                          |          HookOutcome             |
        |                          |<---------------------------------|
        |                          |   (first non-Proceed stops chain)|
        |     aggregated outcome   |                                 |
        |<-------------------------|                                 |
```

1. **Build** — the firing site (the orchestrator for the three structural hooks
   `on_error` / `before_suspension` / `before_resume`; the engine `execute()` body
   for the remaining LLM / tool / memory hooks) constructs a `HookContext` for the
   current `HookPoint`.
2. **Order** — `fire` selects the dispatch order from `ctx.point()`:
   - `before_*` and `on_*` hooks fire in **registration order**;
   - `after_*` hooks (`after_llm_invocation`, `after_tool_invocation`,
     `after_memory_write`) fire in **reverse** registration order (LIFO unwind so an
     outermost middleware cleans up after an innermost one).
3. **Dispatch + aggregate**:
   - default policy is **`fail_fast`** — the first non-`Proceed` outcome
     (`Fail` or `ShortCircuit`) stops the remaining middlewares **for that fire** and
     is returned;
   - `on_error` is the exception — **`best_effort`**: the full chain always fires
     (so a failing error-handler middleware cannot mask the original error) and the
     first non-`Proceed` outcome wins.
4. **Return** — the aggregated `HookOutcome` is returned to the firing site.

The declared-order + fail-fast property is proven by `HookDispatcherFireOrderTest`;
engine-side interception of a registered middleware by `RuntimeMiddlewareInterceptsHooksIT`;
the uniform-surface pre-condition (every `ExecutorAdapter` exposes a hook surface, so
the SPI is the single attachment point across heterogeneous engines) by
`EveryEngineDeclaresHookSurfaceTest`; and carrier immutability by
`SpiCarrierImmutabilityTest`.

### Scope today (honest assertion)

Hook **delivery** is runtime-enforced; hook **outcome consumption is not yet wired**.
`engine-hooks.v1.yaml` records this explicitly (`status: runtime_enforced` for
delivery, `outcome_consumption_status: design_only`). Concretely:

- the `fail_fast` / `best_effort` aggregation above applies **inside** a single
  `fire(ctx)` call (it stops / continues the chain for that fire);
- the Run-lifecycle effects that the contract's `failure_propagation` block describes
  as the *target* — `HookOutcome.Fail` → `Run.FAILED`, `HookOutcome.ShortCircuit` →
  engine bypass — do **not** happen today: the orchestrator discards the returned
  `HookOutcome` at its call-sites. Those effects land with outcome consumption (W2
  Telemetry Vertical, Rule R-M sub-clause .c.b). This L2 sink documents the wired
  dispatch behaviour, not the deferred Run-state mapping.

Of the canonical `HookPoint` set, only the three structural hooks (`on_error`,
`before_suspension`, `before_resume`) have orchestrator call-sites today; the six
engine-fired hooks (LLM × 2, tool × 2, memory × 2) and `on_yield` land with the
first consumer middlewares (per `engine-hooks.v1.yaml` Phase-2 wiring note +
ADR-0073).

## What stays upstream (NOT carried here)

Per the layer-purity verdict, the following remain at L0 / L1 / contract and are only
*referenced* here, never duplicated:

- the L0 §4 constraint that runtime middleware attaches via an engine hook surface
  (the *invariant*; L0 owns it, this file owns the method sequence);
- the `EF-HOOK-SURFACE` boundary identity — naming `RuntimeMiddleware` / `HookPoint`
  / `HookContext` / `HookOutcome` / `HookDispatcher` as the hook boundary, and the
  development-view package cluster (`com.huawei.ascend.middleware` +
  `com.huawei.ascend.middleware.spi`) (frame card §2–§3);
- citing the ArchUnit / gate enforcers (`E78`, `E79`, `E80`) that pin the boundary
  (frame card §7);
- the **canonical hook list, ordering, and failure-propagation schema** — owned by
  `engine-hooks.v1.yaml`; this file cites the operation fact, it does not restate the
  schema.

## Gate behaviour

- Rule 37 (`architecture_artefact_front_matter`): this file declares `level: L2` +
  `view: scenarios`.
- Rule 38 (`architecture_graph_well_formed`): the `relates_to:` front-matter links
  upward to the anchoring frame card and the contract surface; the regenerated graph
  is owned by the reconcile/governance wave, not by this document.

## Authority

- ADR-0068 — Layered 4+1 + Architecture Graph as twin sources of truth
  ([`../../../../docs/adr/0068-layered-4plus1-and-architecture-graph.yaml`](../../../../docs/adr/0068-layered-4plus1-and-architecture-graph.yaml)).
- ADR-0073 — Engine Hooks + Runtime-Owned Middleware SPI (the FunctionPoint's
  `saa.sourceAdr`)
  ([`../../../../docs/adr/0073-engine-hooks-and-runtime-middleware.yaml`](../../../../docs/adr/0073-engine-hooks-and-runtime-middleware.yaml)).
- ADR-0157 — EngineeringFrame Ontology (`EF-HOOK-SURFACE` structural anchor).
- Hook contract surface (wire mechanics live here):
  [`../../../../docs/contracts/engine-hooks.v1.yaml`](../../../../docs/contracts/engine-hooks.v1.yaml).
- Structural anchor frame: [`EF-HOOK-SURFACE`](../../L1/frames/EF-HOOK-SURFACE.md).
- L2 corpus index: [`../README.md`](../README.md).
