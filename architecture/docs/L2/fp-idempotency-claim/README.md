---
level: L2
view: process
status: active
feature: "fp-idempotency-claim"
relates_to:
  - "architecture/features/function-points.dsl"
  - "architecture/features/engineering-frames.dsl"
  - "architecture/docs/L1/frames/EF-ACCESS-ADMISSION.md"
  - "architecture/docs/L1/agent-service/features/access-layer.md"
  - "docs/contracts/openapi-v1.yaml"
authority: "ADR-0068 (Layered 4+1 + Architecture Graph) + ADR-0161 (EngineeringFrame package-cluster anchor + Card over DSL) + ADR-0057 (durable idempotency claim/replay)"
---

# L2 FunctionPoint Spec — `FP-IDEMPOTENCY-CLAIM`

This is the L2 detailed-design home for the admission **idempotency claim + replay**
mechanism: the cross-cutting filter that, before any state write, claims an
`(tenant, idempotency-key)` slot against a durable store and short-circuits a
duplicate request to the prior outcome. It carries the method call chain, runtime
sequence, error paths, and test inventory that the layer-purity verdict ruled does
**NOT** belong in L0 / L1 prose (Rule 145 / E194-E195). It is the migration target
for that leaked detail, not a second source of truth.

> **This document is a READABLE INTERPRETATION layer (Rule 146 / E196 discipline).**
> It invents no FunctionPoint ID, frame ID, operation ID, status code, error code,
> or method name. Every identity is **copied** from the authoring DSL; every fact is
> **cited** from the generated facts. Where this prose and the DSL disagree, the DSL
> wins; where the DSL and generated facts disagree, the **generated facts win**
> (ADR-0154 cascade: `generated facts > DSL > Card/prose`).

## Authority chain (read top-down)

1. **FunctionPoint identity (authoring DSL)** — element `fpIdempotencyClaim` in
   [`../../../features/function-points.dsl`](../../../features/function-points.dsl),
   `saa.id` = `FP-IDEMPOTENCY-CLAIM`. Its `saa.status`, `saa.channel`, `saa.actor`,
   `saa.trigger`, `saa.requirement`, and `saa.sourceAdr` are copied verbatim into
   the sections below; this spec adds no property the element does not declare.
2. **Owning EngineeringFrame (structural parent)** — `EF-ACCESS-ADMISSION`, which
   holds the `anchors` edge to this FunctionPoint in
   [`../../../features/engineering-frames.dsl`](../../../features/engineering-frames.dsl)
   (`efAccessAdmission -> fpIdempotencyClaim`, `saa.rel = anchors`). Its Frame Card
   is [`../../L1/frames/EF-ACCESS-ADMISSION.md`](../../L1/frames/EF-ACCESS-ADMISSION.md)
   (section 6, `FP-IDEMPOTENCY-CLAIM`).
3. **Generated facts (binding factual authority)** — the `code-symbol/*` and
   `test/*` facts in
   [`../../../facts/generated/code-symbols.json`](../../../facts/generated/code-symbols.json)
   and [`../../../facts/generated/tests.json`](../../../facts/generated/tests.json).
   Every anchor cited below resolves in these files; facts are never hand-edited.
4. **Contract surface** — this FunctionPoint declares no `saa.contract_op_refs`. It
   is an internal admission filter, not a wire operation; its boundary is the
   `IdempotencyStore` SPI type cited in §4 (see §6).
5. **L0 constraint authority** — `architecture/docs/L0/ARCHITECTURE.md` §4 names the
   at-most-once admission invariant; this spec carries the claim/replay runtime
   detail. The durable-storage behaviour is governed by ADR-0057.

---

## 1. Behavior

This FunctionPoint realizes **at-most-once admission**: a mutating request carrying
an idempotency key is admitted exactly once per `(tenant, key)` pair; a duplicate
re-presentation of the same key is decided against the prior claim rather than
re-executed, and a re-presentation of the same key with a *different* request body
is rejected as a conflict. The decision is made at the edge, before any Run state
write. On the value axis it serves `REQ-005` (`ProductClaim -> Requirement ->
Feature -> FunctionPoint`); on the structural axis it is anchored by
`EF-ACCESS-ADMISSION` (`Module agent-service -> EngineeringFrame -> FunctionPoint`).

| Field | Value (copied from the DSL element) |
|---|---|
| FunctionPoint ID | `FP-IDEMPOTENCY-CLAIM` |
| Status | `shipped` (`saa.status`) |
| Owning EngineeringFrame | `EF-ACCESS-ADMISSION` (the `anchors` parent) |
| Owner module | `agent-service` (`saa.owner`) |
| Requirement | `REQ-005` (`saa.requirement`) |
| Channel | `internal` (`saa.channel`) |
| Actor | `platform-runtime` (`saa.actor`) |
| Trigger | `internal-orchestration-event` (`saa.trigger`) |
| Source ADR | `ADR-0027` (`saa.sourceAdr`) |

> The DSL `saa.sourceAdr` value (`ADR-0027`) is copied verbatim above. The durable
> claim/replay runtime behaviour this spec details is governed by ADR-0057
> (`docs/adr/0057-durable-idempotency-claim-replay.md`), cited in Authority below.

## 2. I/O

- **Input** — the inbound `(tenantId, idempotencyKey, requestHash)` triple derived
  from the request: the tenant from the bound `TenantContext`
  (`code-symbol/com-huawei-ascend-service-platform-tenant-tenantcontext`), the key
  from the parsed `IdempotencyKey`
  (`code-symbol/com-huawei-ascend-service-platform-idempotency-idempotencykey`), and
  a content hash of the request body computed by the filter. The header that carries
  the key (name, posture-conditioned requirement, `400` mapping for a malformed key)
  is contract-surface / wire material, cited not inlined.
- **Output (success)** — on a first claim the filter continues the chain (downstream
  admission proceeds); on a duplicate claim it short-circuits to the prior outcome.
  The claim record is the
  `code-symbol/com-huawei-ascend-service-platform-idempotency-idempotencystore-idempotencyrecord`
  returned by the store SPI as an `Optional` (empty = first claim, present =
  duplicate).
- **Side effects** — one durable claim row written through the `IdempotencyStore`
  SPI boundary (`code-symbol/com-huawei-ascend-service-platform-idempotency-idempotencystore`);
  the row's schema, TTL, and SQL are the store-implementation / contract-surface
  concern, named by the SPI here, never inlined as a statement.

## 3. Runtime Sequence

```mermaid
sequenceDiagram
    autonumber
    participant Caller as platform-runtime (admission edge)
    participant Filter as IdempotencyHeaderFilter
    participant Store as IdempotencyStore (SPI)
    participant Chain as downstream admission

    Caller->>Filter: mutating request (key + body)
    Filter->>Filter: parse IdempotencyKey, compute requestHash
    Filter->>Store: claimOrFind(tenantId, key, requestHash)
    alt first claim (Optional empty)
        Store-->>Filter: empty
        Filter->>Chain: continue chain (admit once)
    else duplicate, same hash
        Store-->>Filter: existing IdempotencyRecord (same hash)
        Filter-->>Caller: replay prior outcome (no re-execution)
    else duplicate, drifted hash
        Store-->>Filter: existing IdempotencyRecord (different hash)
        Filter-->>Caller: conflict (body drift; downstream not invoked)
    end
```

The store-side claim is a single atomic `claimOrFind` over the SPI; the
compare-and-insert mechanics and the body-drift detection inside the store
implementation are the store's own L2 / contract-surface detail, named here by the
SPI method (cited in §4), not spelled as SQL.

## 4. Class / Method Anchors (from facts)

| Role | Symbol | Fact id (+ method descriptor) |
|---|---|---|
| Mechanism (filter) | `IdempotencyHeaderFilter` | `code-symbol/com-huawei-ascend-service-platform-idempotency-idempotencyheaderfilter` |
| Boundary / SPI | `IdempotencyStore.claimOrFind` | `code-symbol/com-huawei-ascend-service-platform-idempotency-idempotencystore#claimOrFind(Ljava/util/UUID;Ljava/util/UUID;Ljava/lang/String;)Ljava/util/Optional;` |
| SPI impl (in-memory) | `InMemoryIdempotencyStore.claimOrFind` | `code-symbol/com-huawei-ascend-service-platform-idempotency-inmemoryidempotencystore#claimOrFind(Ljava/util/UUID;Ljava/util/UUID;Ljava/lang/String;)Ljava/util/Optional;` |
| SPI impl (JDBC / durable) | `JdbcIdempotencyStore.claimOrFind` | `code-symbol/com-huawei-ascend-service-platform-idempotency-jdbcidempotencystore#claimOrFind(Ljava/util/UUID;Ljava/util/UUID;Ljava/lang/String;)Ljava/util/Optional;` |
| Claim record (type) | `IdempotencyStore$IdempotencyRecord` | `code-symbol/com-huawei-ascend-service-platform-idempotency-idempotencystore-idempotencyrecord` |
| Claim status (type) | `IdempotencyStore$Status` | `code-symbol/com-huawei-ascend-service-platform-idempotency-idempotencystore-status` |
| Key value object (type) | `IdempotencyKey` | `code-symbol/com-huawei-ascend-service-platform-idempotency-idempotencykey` |

The `IdempotencyHeaderFilter` extends `OncePerRequestFilter`; its per-request
override (`doFilterInternal`) is `protected` and therefore not a member of the class
fact's `public_methods[]`, so it is named here as the override but is intentionally
not cited as a `#descriptor` method ref (only `public_methods[]` entries are
gate-resolvable). All fact ids in this section resolve in
[`../../../facts/generated/code-symbols.json`](../../../facts/generated/code-symbols.json).

## 5. Error Paths

| Cause (observable) | Outcome | Status / signal | Mechanism |
|---|---|---|---|
| Same `(tenant, key)`, identical request hash | Replay of prior outcome; downstream not re-invoked | short-circuit (no re-execution) | duplicate `IdempotencyRecord` from `claimOrFind`, same `requestHash` |
| Same `(tenant, key)`, drifted request hash | Conflict; downstream not invoked | rejected (body drift) | duplicate `IdempotencyRecord` from `claimOrFind`, differing `requestHash` |
| Malformed idempotency key | Rejected at the edge | `400` | `IdempotencyKey.parse` rejects; the `400` mapping is `contract-op/createrun` wire material |
| Missing key on a mutating request (research/prod posture) | Rejected at the edge | `400` | posture-conditioned header requirement; the status mapping is contract-surface material |

The exact HTTP status mapping and header semantics are owned by the contract surface
(the mutating admission op `contract-op/createrun`); this table names the *cause →
mechanism* relation that this FunctionPoint realizes, and defers the wire status
codes to that operation's `response_status_codes`.

## 6. Contracts

No external contract surface — this is an internal-channel admission filter (the DSL
element declares no `saa.contract_op_refs` and no HTTP entry). The binding boundary
is the owning frame's SPI type
`code-symbol/com-huawei-ascend-service-platform-idempotency-idempotencystore`
(method `claimOrFind`, cited in §4). The filter runs in front of the mutating
admission operation `contract-op/createrun`
([`../../../facts/generated/contract-surfaces.json`](../../../facts/generated/contract-surfaces.json));
the route, verb, header, and status detail of that operation are its own
contract-surface / L2 material, not restated here.

## 7. Tests

| Layer | Test class | Fact id | Covers |
|---|---|---|---|
| Unit | `IdempotencyHeaderFilterTest` | `test/com-huawei-ascend-service-platform-idempotency-idempotencyheaderfiltertest` | filter admit / reject decisions, posture-conditioned missing-header handling, malformed-key rejection |
| Unit | `IdempotencyHeaderFilterBodyReplayTest` | `test/com-huawei-ascend-service-platform-idempotency-idempotencyheaderfilterbodyreplaytest` | body re-readability across the chain, body-drift short-circuit |
| Unit | `IdempotencyHeaderFilterMethodScopeTest` | `test/com-huawei-ascend-service-platform-idempotency-idempotencyheaderfiltermethodscopetest` | mutating-vs-non-mutating method scope of the filter |
| Unit / store | `IdempotencyStoreTest` | `test/com-huawei-ascend-service-platform-idempotency-idempotencystoretest` | `claimOrFind` first-claim / duplicate / drift / TTL-recovery / per-tenant isolation |
| Integration / contract | `IdempotencyStorePostgresIT` | `test/com-huawei-ascend-service-platform-idempotency-idempotencystorepostgresit` | durable claim/replay against PostgreSQL (schema check, per-tenant non-collision, JDBC wiring) |
| Integration / durability | `IdempotencyDurabilityIT` | `test/com-huawei-ascend-service-platform-idempotency-jdbc-idempotencydurabilityit` | claim row survival across failure, TTL renewal, drift-on-retry |
| Integration / wiring | `InMemoryIdempotencyAllowFlagIT` | `test/com-huawei-ascend-service-platform-idempotency-inmemoryidempotencyallowflagit` | posture-gated in-memory-store wiring (research must not wire in-memory) |
| Integration / exemption | `IdempotencyHeaderFilterIT` | `test/com-huawei-ascend-service-platform-idempotency-idempotencyheaderfilterit` | health endpoint exempt from the filter |

The authoring-DSL `verifies` edge for this FunctionPoint
(`testIdempotencyStoreIT -> fpIdempotencyClaim` in
[`../../../features/verification.dsl`](../../../features/verification.dsl)) records
the integration intent; the resolving generated fact for that durable claim/replay
evidence is `test/com-huawei-ascend-service-platform-idempotency-idempotencystorepostgresit`
(the shipped PostgreSQL IT), cited above. All `test/*` ids in this table resolve in
[`../../../facts/generated/tests.json`](../../../facts/generated/tests.json).

## 8. Gates

| Concern | Gate rule / enforcer | What it blocks |
|---|---|---|
| FunctionPoint element well-formedness | Rule G-14 | a profile-tagged FP element missing a required `saa.*` property. |
| Frame anchors >= 1 FP (shipped) | Rule G-23 | promoting `EF-ACCESS-ADMISSION` to `shipped` without anchoring >= 1 FunctionPoint. |
| Card / spec is a readable interpretation | Rule 146 / E196 | a citation here (`code-symbol/*`, `test/*`, method descriptor) that does not resolve in the generated facts, or an FP/frame relationship absent from the DSL. |
| No L2 detail left upstream | Rule 145 / E194-E195 | the filter call chain, claim/replay sequence, store SQL, or test inventory being left in L0 / L1 prose instead of here. |
| FunctionPoint readiness | Rule 147 / E197 (kernel Rule G-30) | a FunctionPoint marked ready whose axis obligations (frame anchors + owning-module implements; a Feature requires; a resolving generated-fact ref + tests + a gate ref) are absent — `gate/lib/check_feature_readiness.py`, ADVISORY at the ADR-0159 §13.3 rung. |

---

## What stays upstream (NOT carried here)

Per the layer-purity keep-list, the following remain at L0 / L1 and are only
*referenced* from this spec, never duplicated (Rule 145):

- the L0 §4 at-most-once admission *invariant* (L0 owns the invariant; this spec owns
  the claim/replay method hops, the body-drift sequence, and the error matrix);
- naming `IdempotencyHeaderFilter` / the `IdempotencyStore` SPI as a **boundary
  identity** and the development-view package decomposition of the admission cluster
  (that is the `EF-ACCESS-ADMISSION` Frame Card material);
- citing the ArchUnit / gate enforcer that pins the boundary (named in §8, not
  re-specified).

## Authority

- ADR-0068 — Layered 4+1 + Architecture Graph as twin sources of truth
  ([`../../../../docs/adr/0068-layered-4plus1-and-architecture-graph.yaml`](../../../../docs/adr/0068-layered-4plus1-and-architecture-graph.yaml)).
- ADR-0161 — EngineeringFrame package-cluster anchor + Card over DSL
  ([`../../../../docs/adr/0161-engineering-frame-package-cluster-anchor-and-card-over-dsl.yaml`](../../../../docs/adr/0161-engineering-frame-package-cluster-anchor-and-card-over-dsl.yaml)).
- ADR-0057 — durable idempotency claim / replay (the resolvable ADR governing this
  FunctionPoint's runtime behaviour)
  ([`../../../../docs/adr/0057-durable-idempotency-claim-replay.md`](../../../../docs/adr/0057-durable-idempotency-claim-replay.md)).
- Rule 33 — Layered 4+1 Discipline; Rule 145 — L2 detail sink; Rule 146 — Frame
  Card / FunctionPoint spec is a readable interpretation (`CLAUDE.md` / rule cards).
- Owning Frame Card: [`../../L1/frames/EF-ACCESS-ADMISSION.md`](../../L1/frames/EF-ACCESS-ADMISSION.md).
- L2 corpus index: [`../README.md`](../README.md).
