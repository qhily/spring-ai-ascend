---
level: L1
view: physical
module: agent-service
status: active
authority: "ADR-0143 (rc55 — canonical 4+1 source moved here) + ADR-0078 (consolidation) + ADR-0101 (rc26 — Mode A platform-centric + Mode B business-centric deployment loci) + ADR-0138 (rc53 — 5-layer L1 ratification) + ADR-0141 (rc55 — Internal Event Queue physical-channel binding per bus-channels.yaml) + Rule R-E (three-track channel isolation) + Rule R-I (five-plane manifest) + Rule R-J.a (Postgres RLS per tenant_id column) + Rule R-L (sandbox policy subsumption)"
---

# agent-service — Physical View

> **Altitude discipline (L1).** This view names the deployment planes
> the module occupies, the deployment loci it supports, and the
> governance manifests it binds to. Runtime-level detail — RLS policy
> bodies, SQL CAS statements, GUC wiring, Flyway migration files,
> column-level schema, envelope field lists, channel-routed variant
> sets — is **L2 / contract** material and lives in the authority
> surfaces cited per row, NOT in this file. Where a row needs a
> concrete shape, it points at the contract or the L2 Boundary Contract
> (published in [`development.md`](development.md) §5) that owns it.

## 1. Five-Plane Deployment Mapping (Rule R-I)

Each plane row states **which plane this module occupies** and **what
boundary responsibility it carries there**. Per-plane wire shapes and
durability tiers are governance-manifest material (see §3) — not
restated here.

| Plane | agent-service presence | Boundary responsibility on this plane |
|---|---|---|
| **Edge Access** | Not deployed here | Layer 1 (Access Layer) is the inbound boundary FOR THIS MODULE; no edge-only code lives in agent-service. The agent-client SDK (W3+) owns the edge plane. |
| **Compute & Control** | **Primary host** | Hosts Layers 1–5. Owns the Run aggregate and its single-writer invariant (ADR-0142). `deployment_plane: compute_control` per `agent-service/module-metadata.yaml`. Posture-gated startup per `PostureBootGuard` (ADR-0058). |
| **Bus & State Hub** | Binds (does not host) | Layer 3 Internal Event Queue `(design_only — ADR-0141)` is a BINDING layer over the bus's physical channels. No bus-side code lives in agent-service. |
| **Sandbox Execution** | Routes to (does not host) | Routes untrusted-code execution to the Sandbox SPI in `agent-middleware` via the Layer 4 RuntimeMiddleware chain. No sandbox execution code lives here. |
| **Evolution** | Produces to (does not host) | Emits `RunEvent` variants carrying the `EvolutionExport` discriminator; the Evolution plane (`agent-evolve`) consumes them. Export-scope semantics per Rule R-M.e + [`run-event.v1.yaml`](../../../../docs/contracts/run-event.v1.yaml). |

**Dual-Locus Deployment** (per ADR-0101):

- **Mode A (Platform-Centric, default)** — `agent-service` deploys on
  platform infrastructure; the Compute & Control plane is hosted by
  the platform team.
- **Mode B (Business-Centric)** — `agent-service` deploys on the
  business unit's servers / client devices alongside
  `agent-execution-engine` for zero-latency local execution loops, for
  latency-critical or data-sovereignty-sensitive scenarios.

In both modes the Run aggregate single-owner invariant (ADR-0142)
holds: Layer 2 is the sole writer; Layer 4 mutates Run state only
through the `RunRepository` SPI
([`code-symbol/com-huawei-ascend-service-runtime-runs-spi-runrepository`](../../../../architecture/facts/generated/code-symbols.json)).
The atomic-primitive obligation that backend implementations must
satisfy is an L2 concern, specified in the
[`development.md`](development.md) §5.3 Postgres RLS Boundary Contract;
the Mode A (Postgres) vs Mode B (embedded) backend choice does not
change the boundary.

### 1.x Deployment vs Code Ownership (ADR-0155 §4 — orthogonal dimensions)

Code home and deployment locus are **orthogonal** axes. The Translation
& Tool-Intercept interception range per agent form, and the bridge
mechanism that wires platform beans into each form, are L2 design
detail; this table records only the orthogonality and the three
sanctioned combinations.

| Agent form | Code home | Deployment | Notes |
|---|---|---|---|
| Native | This repo (`agent-service/...`) | In-process JVM | Platform beans injected via DI. |
| Third-party (AgentScope-java, LangGraph4j, …) | External library | In-process JVM | Framework abstractions bridged at startup. |
| Remote | External service | Out-of-process / remote | A2A protocol boundary; audit-only locally. |

A self-developed Agent CAN be deployed remotely. Combinations beyond
these three rows (Managed Remote / Resource Gateway) are deferred per
ADR-0155 §6 (v2 scope).

## 2. Persistence-Plane Tenancy Posture (Rule R-J.a)

This module's persistence posture is: **every tenant-scoped aggregate
is RLS-bound at the table-creation boundary**, and **every Run state
transition is a single atomic primitive** owned by Layer 2 (ADR-0142).
Both are aggregate-ownership invariants stated here; their concrete
realisation — the RLS policy bodies, the CAS SQL, the `SET LOCAL`
GUC, the per-table column schema, and the Flyway migration sequence —
is delegated to the **Postgres RLS L2 Boundary Contract**
([`development.md`](development.md) §5.3) and produced by a future L2
design + migration wave.

| Aggregate | tenant-scoped | RLS obligation | Persistence status |
|---|---|---|---|
| `IdempotencyRecord` (contract-spine entity) | yes | RLS retrofit deferred; legacy table grandfathered in `gate/rls-baseline-grandfathered.txt` (Rule R-J.a.b deferred) | **shipped W1** |
| Run aggregate (`Run` + `RunStatus` + `RunStateMachine`, owned by Layer 2) | yes | RLS policy required at table-creation time | **W2-deferred** (durable `RunRepository` lands W2) |
| Task aggregate (`Task`, `TaskStateStore` SPI) | yes | RLS policy required | **W2-deferred** |
| Session aggregate (`Session`, `ContextProjector` SPI) | yes | RLS policy required | **W2-deferred** |
| Lifecycle-state audit trail (emission shape per ADR-0145 `RunStateTransitionEvent`) | yes (derived from FK) | RLS policy required | **W2-deferred** |

Cross-tenant isolation is enforced at the **authorization boundary**
(Rule R-J.b) before a row would be visible; persistence-layer RLS is
defence-in-depth. The exact failure-response posture (which status code
a cross-tenant access collapses to, at which wave) is a Layer 1
contract concern owned by [`openapi-v1.yaml`](../../../../docs/contracts)
and the cancel/get route definitions — not restated here.

## 3. Three-Track Bus Bindings (Rule R-E + bus-channels.yaml)

The Layer 3 Internal Event Queue `(design_only — ADR-0141; no code
home at rc55)` binds to the canonical physical channels declared in
[`docs/governance/bus-channels.yaml`](../../../../docs/governance/bus-channels.yaml).
Per Rule R-E + Principle P-E, **physical isolation** (channel choice)
and **durability tier** (per-channel backend choice) are ORTHOGONAL
axes; the single-tier-queue-with-3-storage-modes design was rejected
at the L1 design layer (ADR-0138 §3 red-line c).

| Channel | Intent | Orthogonal durability axis |
|---|---|---|
| `control` | Highest priority, out-of-band: cancel / resume / suspend / S2C-callback signalling. | Per-channel backend chosen independently at deployment time. |
| `data` | In-band, payload-bearing: state-transition and response events; child-run completion. | Per-channel backend chosen independently. |
| `rhythm` | Heartbeat / liveness ticks; long-horizon Run timers (Rule R-H Chronos Hydration). | Per-channel backend chosen independently. |

The **mapping of each RunEvent variant onto a channel**, the inline
payload cap, and the producer-side routing enforcement are contract
material, declared in the `channel_routing` block of
[`run-event.v1.yaml`](../../../../docs/contracts/run-event.v1.yaml) and
[`bus-channels.yaml`](../../../../docs/governance/bus-channels.yaml).
The Layer 3 binding obligation (inputs, outputs, DFX) is published as
an L2 Boundary Contract in [`development.md`](development.md) §5.5.

## 4. Sandbox Isolation Boundary (Rule R-L)

agent-service hosts **no** sandbox execution code; it routes
untrusted-code execution to the Sandbox SPI in `agent-middleware`. The
route is established through the Layer 4 RuntimeMiddleware chain (the
HookPoint-mediated boundary per Rule R-M.c — see
[`logical.md`](logical.md) §1).

The per-skill sandbox policy MUST NOT widen the default beyond what the
physical sandbox can enforce; the required policy keys and the
subsumption rule are declared in
[`docs/governance/sandbox-policies.yaml`](../../../../docs/governance/sandbox-policies.yaml)
(Rule R-L). Runtime refusal of over-wide grants is W2-deferred (Rule
R-L.b); at W0/W1 the subsumption check is the design-time boot-side
enforcement.

## 5. Cross-Plane Tenant Propagation (Rule R-J.a + R-C.2.a)

The cross-plane red lines this module upholds — stated as
architectural invariants, not as field-level envelope shapes:

1. **No anonymous events.** Every cross-plane envelope carries a tenant
   identity. The per-envelope field that carries it is contract
   material (see [`run-event.v1.yaml`](../../../../docs/contracts/run-event.v1.yaml)
   common-fields and the ingress / S2C envelope contracts).
2. **No platform-ThreadLocal leakage across the boundary.** The
   `service.runtime.**` sub-package MUST NOT import the platform-side
   tenant ThreadLocal; cross-plane / async tenant propagation sources
   from the persisted Run, not a thread-bound holder. Enforced by
   ArchUnit (Rule R-C.e; enforcers **E2** + the
   `TenantPropagationPurityTest` mechanism — see
   [`development.md`](development.md) §3.2).
3. **No tenant inference.** Cross-plane consumers MUST NOT infer tenant
   from any other field; the tenant identity field is authoritative.
4. **Cross-tenant blocked at the boundary.** Cross-tenant access is
   refused at the authorization boundary (Rule R-J.b); the precise
   response-code posture per wave is a Layer 1 contract concern
   (`openapi-v1.yaml`), not a physical-view detail.

## 6. Cross-references

- Scenarios: [`scenarios.md`](scenarios.md) — S1-S5 cross-plane
  interactions and red-line invariants.
- Logical: [`logical.md`](logical.md) §1 — 5-layer model + 5a/5b split
  per ADR-0140; the Compute & Control plane hosts all 5 layers.
- Process: [`process.md`](process.md) — message-level cross-plane
  interaction sequences (P1-P6).
- Development: [`development.md`](development.md) — package tree +
  Layer↔Package matrix + §5 L2 Boundary Contracts (the home for RLS /
  CAS / channel-routing realisation detail).
- Contracts (realisation home for the detail this view delegates):
  [`run-event.v1.yaml`](../../../../docs/contracts/run-event.v1.yaml),
  [`s2c-callback.v1.yaml`](../../../../docs/contracts/s2c-callback.v1.yaml),
  [`engine-envelope.v1.yaml`](../../../../docs/contracts/engine-envelope.v1.yaml).
- Governance manifests: [`bus-channels.yaml`](../../../../docs/governance/bus-channels.yaml),
  [`sandbox-policies.yaml`](../../../../docs/governance/sandbox-policies.yaml),
  [`skill-capacity.yaml`](../../../../docs/governance/skill-capacity.yaml).
- Module-root grounding: [`ARCHITECTURE.md`](ARCHITECTURE.md)
  §4 OSS dependencies + §6 Posture-aware defaults.
