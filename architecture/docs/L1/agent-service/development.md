---
level: L1
view: development
module: agent-service
status: active
authority: "ADR-0143 (rc55 — canonical 4+1 source moved here) + ADR-0078 (consolidation) + ADR-0099 (rc22 — Rule G-1.1 L1 depth + grounding) + ADR-0100 (rc22 — 5-component decomposition) + ADR-0138 (rc53 — 5-layer L1 ratification) + ADR-0140 (rc55 — Engine Adapter split 5a/5b) + ADR-0141 (rc55 — Internal Event Queue design_only; service.queue/ NOT shown in tree) + ADR-0144 (rc55 — Layer↔Package matrix)"
---

# agent-service — Development View

> **Altitude discipline (L1).** The development view's job is the
> **structural decomposition**: the package tree, the Layer↔Package
> matrix, the public-SPI surface (named, with generated-fact refs), and
> the future-sub-package roadmap. These are L1-defensible. What this view
> does NOT carry is the **realisation** of any delegated L2 zone: SQL
> migration bodies, RLS policy statements, GUC wiring, CAS clauses, and
> backpressure / routing field schemas are L2 design + contract material.
> §5 publishes each delegated zone's **Boundary Contract** (the
> inputs/outputs/DFX obligations a future L2 doc MUST satisfy) per Rule
> G-1.1.c — it names the contract, it does not implement it.

## 1. Target Directory Tree (Rule G-1.1.a — E166)

Cross-walked against the filesystem at gate time. Every documented
package exists on disk; every existing production package is documented
OR explicitly enumerated under §4 as a future/staged sub-package.

```text
agent-service/
└── src/main/java/
    └── com/huawei/ascend/service/
        ├── dispatcher/                  # rc22 ADR-0100 — Polymorphic Dispatcher (top-level sub-package; entry-point intake)
        ├── orchestrator/                # rc22 ADR-0100 — Reactive Orchestrator (top-level sub-package; tempo control)
        ├── task/                        # rc22 ADR-0100 — Task aggregate
        │   └── spi/                     # TaskStateStore SPI (per ADR-0100)
        ├── session/                     # rc22 ADR-0100 — Session aggregate
        │   └── spi/                     # ContextProjector SPI (per ADR-0100)
        ├── engine/                      # rc23 ADR-0100 — Execution Engine Adapter (Layer 5a per ADR-0140)
        │   ├── adapter/                 # ExecutionEngineAdapter impls (StatelessEngine consumers)
        │   └── spi/                     # StatelessEngine SPI per ADR-0100
        ├── agent/                       # rc43 ADR-0128 — Agent first-class entity host
        │   └── spi/                     # Agent + AgentRegistry SPIs (design_only)
        ├── integration/
        │   └── springai/                # rc51 — Spring AI reference adapter shells (Layer 5b per ADR-0140)
        ├── platform/                    # HTTP edge + cross-cutting (Phase C consolidation per ADR-0078)
        │   ├── auth/                    # JWT validation wiring + tenant-claim cross-check
        │   ├── engine/                  # StatelessEngine auto-configuration + adapter wiring
        │   ├── idempotency/             # idempotency filter + IdempotencyStore (historical platform interface; not under .spi per Rule R-D.d)
        │   ├── observability/           # metric + trace edge filters (per ADR-0061 Telemetry Vertical)
        │   ├── persistence/             # DataSource / database presence conditions
        │   ├── posture/                 # PostureBootGuard (boot-time fail-closed per ADR-0058)
        │   ├── probe/                   # platform probe auto-configuration
        │   ├── resilience/              # resilience auto-configuration
        │   ├── tenant/                  # tenant binding + MDC binding
        │   └── web/                     # HTTP front door + security config + error envelope
        │       └── runs/                # run route controller + exception mapper + request/response DTOs
        └── runtime/                     # Run kernel (Phase C consolidation per ADR-0078; post-ADR-0088 RunRepository SPI lives here)
            ├── evolution/               # EvolutionExport enum (discriminator for the ADR-0145 sealed RunEvent hierarchy; Java sealed type lands in a follow-up impl-mode wave)
            ├── idempotency/             # IdempotencyRecord contract-spine entity
            ├── memory/
            │   └── spi/                 # GraphMemoryRepository SPI (1 interface)
            ├── orchestration/
            │   └── inmemory/            # posture-gated reference impls (orchestrator + executors + checkpointer + run registry)
            ├── posture/                 # AppPostureGate (dev-only guard)
            ├── probe/                   # OssApiProbe (W0 classpath shape probe)
            ├── resilience/              # resilience contract impls + capacity registry impl
            │   └── spi/                 # ResilienceContract + SkillCapacityRegistry + decision carriers
            ├── runs/                    # Run + RunStatus + RunStateMachine + RunMode (Run aggregate per ADR-0142 single-owner pinning)
            │   └── spi/                 # RunRepository SPI
            └── s2c/                     # S2C transport reference impl (consumes bus.spi.s2c per ADR-0088)
```

**Cross-walk verification status:** every directory listed above exists
on disk per the rc55 W5 filesystem scan. Future sub-packages NOT yet on
disk are listed under §4 with their ADR anchor.

### v1.2 SPI package additions (ADR-0155)

Two new SPI packages live under `agent-service/src/main/java/`,
declaring the v1.2 execution + interception boundary surface (interface
identities only; their generated-fact refs appear in
[`spi-appendix.md`](spi-appendix.md)):

```text
service.runtime.executor.spi/      # ExecutorAdapter SPI + InjectionMode enum (wiring choice per ADR-0155 §4)
service.runtime.intercept.spi/     # PlatformChatClient / PlatformToolCallback / PlatformMemoryProvider / PlatformRetriever SPIs
```

`InjectionMode` values (`NATIVE_DI | THIRD_PARTY_BRIDGE | EVENT_RELAY |
NONE`) are the L5a Engine-Dispatch wiring discriminator; their semantics
are specified in the features inventory (L5a EDE-08), not here.

## 2. Layer ↔ Package Matrix (ADR-0144)

The canonical mapping between ADR-0138's 5-layer logical-view
decomposition and ADR-0100's 5-component (+ Phase C platform/runtime)
package-structural decomposition. Layer 5 is split into 5a + 5b per
ADR-0140.

| Logical Layer (ADR-0138 + ADR-0140) | Owned sub-packages (rc55 actual) | Notes |
|---|---|---|
| **1. Access Layer** | `service.dispatcher/`, `service.platform.web/` (+ `web/runs/`), `service.platform.idempotency/`, `service.platform.tenant/`, `service.platform.auth/`, `service.platform.observability/` | Inbound protocol convergence + tenant binding + idempotency claim + trace origination. Future `service.platform.a2a/` (W3+) joins this layer when the A2A SDK lands. |
| **2. Session & Task Manager (Run aggregate owner per ADR-0142)** | `service.runtime.runs/` (+ `runs/spi/`), `service.task/` (+ `task/spi/`), `service.session/` (+ `session/spi/`), `service.runtime.idempotency/` | Owns Run / Task / Session aggregates + their SPIs (`RunRepository`, `TaskStateStore`, `ContextProjector`). Run aggregate ownership pinned exclusively here; the `RunRepository` SPI is the SINGLE sanctioned Run-state-transition path. |
| **3. Internal Event Queue** | `service.queue/` *(design_only — DOES NOT EXIST on filesystem at rc55 per ADR-0141)* | Binding-only layer over agent-bus three-track channels per Rule R-E. Code home deferred; Boundary Contract published in §5.5. |
| **4. Task-Centric Control Layer (RuntimeMiddleware exclusive home per ADR-0140)** | `service.orchestrator/`, `service.runtime.orchestration/` (+ `orchestration/inmemory/`), `service.runtime.posture/` | State-machine validation delegated to Layer 2 (ADR-0142). RuntimeMiddleware chain dispatched on HookPoint events (exclusive home, no double-homing). DualTrackRouter *(design_only — W2, ADR-0112)*. SuspendSignal handling (child-run + S2C variants). |
| **5a. Engine Dispatch & Execution (ADR-0140)** | `service.engine.adapter/`, `service.engine.spi/` — consumes `agent-execution-engine.engine.spi.*` cross-module per the allowed-dependency declaration in `agent-service/module-metadata.yaml` | `EngineRegistry.resolve` per Rule R-M.a. `ExecutorAdapter` impls per Rule R-M.b strict matching. EngineHookSurface emits HookPoint events INTO Layer 4. |
| **5b. Translation & Tool-Intercept (ADR-0140)** | `service.integration.springai/` + consumes `service.session.spi.ContextProjector` (cross-layer read-only) | Spring AI shaping primitives composed into the model-invocation pipeline. No RuntimeMiddleware here. Spring AI evolution cadence independent of Rule R-M. |
| **(cross-cutting)** | `service.platform.{posture,persistence,engine,resilience,probe}/`, `service.runtime.{resilience,memory,s2c,evolution,probe}/`, `service.agent.spi/` | Cross-cutting concerns not owned by a specific layer: posture gating, persistence wiring, autoconfig, resilience contracts, memory SPI, S2C transport, evolution-export discriminator, probes, agent SPIs. |

**Layer-assignment discipline going forward** (ADR-0144 §4):
- New sub-packages under `service.**` MUST be classified into exactly
  ONE layer OR the cross-cutting bucket — never two (the
  `F-layer-decomposition-low-cohesion` guard).
- The author MUST update this matrix in the same commit that adds the
  new sub-package.
- If a sub-package's responsibility crosses layers, split it OR refine
  the layer ownership BEFORE landing.

## 3. Logical Layer ↔ Package Tree Mapping (clarifications)

### 3.1 `service.dispatcher/` boundary clarification

`service.dispatcher/` is a TOP-LEVEL sub-package introduced at rc22 per
ADR-0100 for the Polymorphic Dispatcher. It is a sibling of `platform/`
and `runtime/`, NOT nested under `platform/`. It participates in the
**Layer 1 (Access Layer)** logical view per ADR-0144, alongside
`platform/web/`, `platform/idempotency/`, `platform/tenant/`,
`platform/auth/`, and `platform/observability/`.

**Rationale for the split** (rc22 ADR-0100): the dispatcher's
execution-domain responsibility (deciding which engine adapter to call)
has a different change cadence and test surface than `platform/web/`'s
HTTP-edge responsibility; lumping them under `platform/` would obscure
the distinction.

### 3.2 `platform/` vs `runtime/` cross-cutting boundary

`service.platform.*` owns HTTP-edge cross-cutting concerns (web,
idempotency, tenant, auth, observability, posture, persistence,
resilience + engine auto-config, probes). `service.runtime.*` owns
Runtime-kernel concerns (Run aggregate, RunRepository SPI, orchestration
reference impls, resilience SPI + impls, memory SPI, S2C transport impl,
evolution discriminator, idempotency entity, probes).

**Layering invariant** (Rule R-C.e, retargeted at Phase C per ADR-0078):
`service.runtime.**` MUST NOT import any class under
`service.platform.**`. Enforced by the ArchUnit mechanism
`ServiceRuntimeMustNotDependOnServicePlatformTest` (enforcer **E2**). The
reverse (`service.platform.* → service.runtime.*`) is permitted ONLY to
the runtime public surface declared by the mechanism
`ServicePlatformImportsOnlyServiceRuntimePublicApiTest` (enforcer
**E34**): the `runs/` package, the neutral engine SPI
(`bus.spi.engine.*`), the `posture/` package, and the dev-posture-gated
in-memory run registry.

### 3.3 New rc22 sub-packages (`dispatcher / orchestrator / task / session / engine`)

These five top-level sub-packages were INTRODUCED at rc22 alongside the
ADR-0100 5-component decomposition. They are siblings of `platform/` and
`runtime/`, NOT nested inside them. The bulk Java refactor (moving the
Run/Task/Session aggregates out of `runtime/runs/` etc. into these
sub-packages) is rc23+ scope per the ADR-0100 timeline; rc55 does NOT
execute that refactor.

## 4. Future Sub-Packages (declared in design, NOT on disk at rc55)

| Sub-package | Wave | Authority | Status |
|---|---|---|---|
| `service.queue/` | W4+ (or W2 per scheduling) | ADR-0141 (Internal Event Queue design_only Boundary Contract) | NOT on disk; hosts the Layer 3 Producer/Consumer split routing to the three bus channels per Rule R-E |
| `service.runtime.llm/` | W2 | per `ARCHITECTURE.md` §2.B wave-staged placeholders | NOT on disk; LLM gateway + cost metering |
| `service.runtime.outbox/` | W2 | per ARCHITECTURE.md | NOT on disk; durable outbox publisher |
| `service.runtime.observability/` (kernel side) | W2 | per ARCHITECTURE.md | NOT on disk; custom metrics + span propagation |
| `service.runtime.tool/` | W3 | per ARCHITECTURE.md | NOT on disk; MCP server registry + per-tenant tool allowlist |
| `service.runtime.action/` | W3 | per ARCHITECTURE.md | NOT on disk; ActionGuard filter chain |
| `service.runtime.temporal/` | W4 | per ARCHITECTURE.md | NOT on disk; durable workflow + activity classes (long-running runs) |
| `service.platform.a2a/` | W3+ | per ADR-0100 §rejected-framing #1 (CONTRACT-only A2A; no `a2a-java` SDK runtime dep) | NOT on disk; A2A Server + Client when SDK adoption lands |
| `service.runtime.evolution.event.*` (sealed RunEvent + records) | follow-up impl-mode rc | ADR-0145 + [`run-event.v1.yaml`](../../../../docs/contracts/run-event.v1.yaml) (`status: design_only`) | NOT on disk; Java sealed type + record variants land when the impl-mode wave executes; Rule R-M.e becomes non-vacuous then |

**Discipline**: any new sub-package added under `service.**` MUST be
either listed here (with ADR anchor + wave) OR added to the §1 tree in
the same commit + classified under the §2 layer matrix.

## 5. L2 Boundary Contracts (Rule G-1.1.c — E168)

Five L2 zones are **delegated** from this L1 design. For each, the
Boundary Contract below states the **zone identity, owning authority,
and the inputs / outputs / DFX obligations** a future L2 doc MUST
satisfy. The **realisation** of each zone — SQL migrations, RLS policy
bodies, GUC wiring, CAS statements, backpressure / routing field schemas
— is produced by the L2 design (or the cited contract) when authored;
it is deliberately NOT reproduced here.

### 5.1 Zone — Run lifecycle extended for Session decoupling (rc25 candidate)

- **Authority:** ADR-0135 (AgentSession-as-Run-projection) + ADR-0100.
- **Inputs:** the Run / Session / Task aggregates (per ADR-0142 / 0100).
- **Outputs:** Run↔Session N:M projection, Task↔Session 1:N ownership,
  tenant-bound RLS coverage extending to the projection tables.
- **DFX obligations:** durable projection store (in-memory ref impl W0);
  read-replica resilience tolerated to the W2 SLA; eventual consistency
  with last-known UI surface; cross-tenant projection blocked at the RLS
  layer; per-tenant projection-lag observability.

### 5.2 Zone — Reactive Orchestrator backpressure protocol (rc23-25 candidate)

- **Authority:** ADR-0100 + the (design_only) backpressure-request
  contract.
- **Inputs:** a backpressure `SuspendReason` variant (W2-deferred); the
  backpressure-request contract on the bus control track.
- **Outputs:** an Orchestrator admission decision (admit / queue /
  reject); `SkillCapacityRegistry`-mediated grants.
- **DFX obligations:** hot-reloadable admission policy from
  `skill-capacity.yaml`; per-skill escalation paths; rejected admissions
  return a typed `SuspendReason` envelope; per-tenant capacity
  not bypassable; per-tenant admission-decision observability.

### 5.3 Zone — Postgres RLS migration sequence (rc25 candidate)

> This zone owns the persistence realisation that the Physical and
> Logical views delegate: the RLS policy bodies, the CAS SQL backing
> `RunRepository`'s transition primitive, the session-level GUC wiring,
> the per-table column schema, and the Flyway migration sequence.

- **Authority:** Rule R-J.a + ADR-0118 (atomic CAS) + ADR-0142
  (single-owner pinning).
- **Inputs:** existing Flyway migrations (the grandfathered idempotency
  table per `gate/rls-baseline-grandfathered.txt`); tenant propagation
  from the persisted Run (Rule R-J.a).
- **Outputs:** RLS policies on every tenant-scoped table (runs / tasks /
  sessions / lifecycle-state audit); session-level tenant-GUC wiring;
  a backfill plan for the grandfathered table; an atomic transition
  primitive for `RunRepository`.
- **DFX obligations:** per-table migration + smoke test with documented
  rollback; RLS-bypass detection (warn in dev, fail in prod);
  zero-downtime migration via a dual-write window; cross-tenant reads
  return empty (defence-in-depth); RLS-blocked reads counted per tenant.

### 5.4 Zone — DualTrackRouter predicate refinement (W2 candidate)

- **Authority:** ADR-0112 (SlowTrackJudge) + ADR-0139 (narrowed
  Fast/Slow semantics).
- **Inputs:** the (design_only) dual-track-routing-policy; the
  SlowTrackJudge SPI; per-tenant routing thresholds.
- **Outputs:** a Fast-Path / Slow-Path decision per Run dispatch; a
  per-decision audit event (extends the RunEvent hierarchy via a
  follow-up ADR if needed).
- **DFX obligations:** hot-reloadable routing policy; misclassification
  recovery via a mid-execution SuspendSignal upgrade (Fast→Slow);
  per-Run failure tolerated; the decision does NOT bypass tenant scoping
  / RLS / Rule R-G / Rule R-H / Rule R-J.a (ADR-0139 red line);
  per-tenant Fast/Slow-ratio observability.

### 5.5 Zone — Internal Event Queue (Layer 3) (ADR-0141 — published at design time; no L2 doc yet)

- **Authority:** ADR-0141 + Rule R-E + `bus-channels.yaml`.
- **Inputs:** RunEvent emissions from Layer 2 (per ADR-0145);
  control-track signals from Layer 4; rhythm-track ticks from the bus
  Tick Engine (Rule R-H).
- **Outputs:** control-channel, data-channel, and rhythm-channel
  publications (the per-variant channel mapping + inline payload cap are
  the contract material in
  [`run-event.v1.yaml`](../../../../docs/contracts/run-event.v1.yaml)
  `channel_routing` + [`bus-channels.yaml`](../../../../docs/governance/bus-channels.yaml)).
- **DFX obligations:** per-channel durability tier chosen at deployment
  time; at-least-once delivery with dedup keyed by tenant + idempotency
  identity (per ADR-0057); back-pressure surfaced as a `SuspendReason`
  (W2-deferred); tenant binding on every channel emission (Rule R-J.a);
  per-channel observability.

## 6. Cross-references

- Scenarios: [`scenarios.md`](scenarios.md) — S1-S5 + cross-scenario
  invariants.
- Logical: [`logical.md`](logical.md) — 5-layer + ADR-0140 split + the
  aggregate model + state machines + the RunEvent hierarchy fact.
- Process: [`process.md`](process.md) — layer-interaction flows P1-P6.
- Physical: [`physical.md`](physical.md) — 5-plane deployment +
  persistence-plane tenancy posture + 3-track bus + sandbox.
- SPI Appendix: [`spi-appendix.md`](spi-appendix.md) — active SPI 4-way
  parity + generated-fact refs.
- Module-root: [`ARCHITECTURE.md`](ARCHITECTURE.md) — shipped components
  + dependencies + wave plan.
- ADRs: [ADR-0078](../../../../docs/adr/0078-agent-service-consolidation.yaml) (consolidation), [ADR-0088](../../../../docs/adr/0088-agent-runtime-core-dissolution.yaml) (kernel redistribution), [ADR-0099](../../../../docs/adr/0099-rc22-l1-architecture-depth-and-grounding.yaml) (Rule G-1.1), [ADR-0100](../../../../docs/adr/0100-rc22-agent-service-l1-runtime-role-decomposition.yaml) (5-component), [ADR-0138](../../../../docs/adr/0138-agent-service-five-layer-l1-ratification.yaml) (5-layer), [ADR-0140](../../../../docs/adr/0140-engine-adapter-layer-split.yaml) (5a/5b split), [ADR-0141](../../../../docs/adr/0141-internal-event-queue-design-only.yaml) (Layer 3 design_only), [ADR-0142](../../../../docs/adr/0142-run-aggregate-single-owner.yaml) (Run aggregate pinning), [ADR-0144](../../../../docs/adr/0144-layer-vs-package-matrix.yaml) (THIS matrix), [ADR-0145](../../../../docs/adr/0145-run-event-sealed-hierarchy.yaml) (sealed RunEvent).
