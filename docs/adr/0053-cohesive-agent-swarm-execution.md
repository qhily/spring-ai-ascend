# 0053. Cohesive Agent Swarm Execution — Workflow Authority Invariant + SpawnEnvelope

**Status:** accepted
**Deciders:** architecture, chaos.xing.xc@gmail.com
**Date:** 2026-05-13
**Technical story:** Reviewer finding P0 (`docs/logs/reviews/2026-05-13-l0-capability-labels-platformization.en.md`): an architecture review proposed naming five L0 contracts for "cohesive Agent Swarm execution" — `SwarmRun`, `ParentRunRef`, `SpawnEnvelope`, `SwarmJoinPolicy`, `CrossWorkflowHandoff` — and requested that "agent-spawned child work remain under the same workflow authority by default" be promoted to a §4 architecture constraint. The class-based self-audit (14th cycle) confirmed that four of the five concepts already have semantic backing in §4 #9/#19/#29 + ADRs 0019/0024/0032 but lack consolidated naming, and **discovered a major hidden-defect cluster**: parent→child Run propagation today covers only 1 of 15 lifecycle dimensions explicitly. The rest are scattered, partial, or design gaps.

## Context

Existing parent-child semantics in the architecture:

- `Run.parentRunId` + `Run.parentNodeKey` + `Run.mode` (§4 #9): nesting chain at the entity level.
- `SuspendReason.ChildRun(UUID childRunId, ChildFailurePolicy, Instant deadline)` and `SuspendReason.AwaitChildren(List<UUID> childRunIds, JoinPolicy, ChildFailurePolicy, Instant deadline)` (ADR-0019, §4 #19): sealed taxonomy for child-spawn suspension. W0 reference impl covers single-`ChildRun` only.
- `RunScope.STEP_LOCAL | SWARM` discriminator (ADR-0032, §4 #29): scope-based hierarchy. `RunRepository.findRootRuns(tenantId)` ships at W0 returning top-level runs with `parentRunId == null`.
- `JoinPolicy: ALL | ANY | N_OF` and `ChildFailurePolicy: PROPAGATE | IGNORE | COMPENSATE` (ADR-0019).
- `RunContext.suspendForChild(parentNodeKey, childMode, childDef, resumePayload)` is the SPI method that initiates a child run from a parent.

**Hidden gap surfaced by class-based audit:** of 15 candidate lifecycle dimensions (tenant_id, permission scope, budget/tokenBudget, cancellation policy, deadline, trace correlation, retry/attemptId, APP_POSTURE, session_id, BusinessRuleSubset, PlaceholderPreservationPolicy, memory ownership boundary, idempotency context, observability tags, audit trail / actor identity), **only 1 is explicitly propagated** (`tenant_id`); **4 are partial**; **7 are full design gaps**; **3 are implicit-only**.

The reviewer's `SpawnEnvelope` ask is therefore not just a naming consolidation — it is a contract-definition exercise that closes the propagation-completeness gap.

## Decision Drivers

- Reviewer P0: name the swarm contracts as L0 vocabulary and add the workflow-authority §4 constraint.
- Class-based audit P1 (hidden): name the 15 lifecycle dimensions explicitly so future implementation cannot accidentally drop one.
- Class-based audit P2 (hidden): document the 5-boundary authority-transfer taxonomy (HTTP→Runtime, C→S, Parent→Child, Run→Skill, Cross-Workflow) so each boundary has a named carrier.
- Avoid SPI bloat at W0: name the contracts, ship Java types as design-only / W2.
- Cross-reference existing ADRs rather than duplicating; preserve evidence trail.

## Considered Options

1. **New ADR introducing all five contracts + 15-dimension SpawnEnvelope field set + 5-boundary taxonomy + §4 #51 invariant** (this decision).
2. Update ADRs 0019/0032 in place — rejected: would mix concerns (taxonomy + new consolidated type); makes review harder.
3. Defer to W2 implementation — rejected: leaves the workflow-authority invariant unstated at L0; permits drift in W1 design work.
4. Adopt the reviewer's exact naming + skip the 15-dimension enumeration — rejected: would lock in the surface-level naming consolidation while silently shipping the 7 design gaps unaddressed (the failure mode the user's class-based directive explicitly forbids).

## Decision Outcome

**Chosen option:** Option 1.

### The five contract names

| Contract | Status | Maps to existing |
|----------|--------|-------------------|
| `SwarmRun` | Alias at L0 contract level | `Run` with `RunScope.SWARM` (ADR-0032). `SwarmRun` is the same Java entity; the alias clarifies that the Run participates in a multi-agent swarm and its lifecycle is autonomous within the parent workflow authority. |
| `ParentRunRef` | Alias at L0 contract level | `Run.parentRunId` + `Run.parentNodeKey` (§4 #9). Carries enough state for lifecycle propagation, trace correlation, quota attribution, and terminal-state aggregation. |
| `SpawnEnvelope` | New L0 contract; Java type deferred to W2 | The consolidated 15-dimension field set that flows from parent to child on `RunContext.suspendForChild(...)`. Replaces the current four-positional-argument signature with a single typed envelope at W2. |
| `SwarmJoinPolicy` | Alias at L0 contract level | `JoinPolicy: ALL \| ANY \| N_OF` (ADR-0019 §4 #19) inside `SuspendReason.AwaitChildren`. The L0 contract name is `SwarmJoinPolicy`; the Java type remains `JoinPolicy`. |
| `CrossWorkflowHandoff` | **New L0 contract**; explicit escape-hatch from the workflow-authority invariant | When child execution genuinely belongs to a different workflow authority (e.g. handoff to an external Temporal workflow, a peer Agent Service instance under a different tenant's quota, an off-platform partner system), a `CrossWorkflowHandoff` MUST be performed. The handoff produces (a) a new lifecycle boundary, (b) a fresh resume contract, (c) explicit ownership transfer (the parent run no longer aggregates terminal-state from the handed-off child), (d) audit-grade attestation. **W0 status: design-only**; Java type deferred to W2. |

### The 15-dimension `SpawnEnvelope` field set

Each dimension is REQUIRED to be defined on the envelope. Implementation status is per dimension:

| # | Dimension | Source authority | W0 today | Wave status |
|---|-----------|------------------|----------|-------------|
| 1 | `parentRunRef` (parentRunId + parentNodeKey) | §4 #9 | ✅ Propagated | W0 shipped |
| 2 | `tenantId` | §4 #22 (ADR-0023) | ✅ Propagated via `RunContext.tenantId()` | W0 shipped |
| 3 | `permissionEnvelopeRef` | §4 #50 (ADR-0052) | ❌ Design gap | W2 contract |
| 4 | `budgetEnvelope` (tokenBudget, wallClockMs, cpuMillis, costCap) | §4 #12 (ADR-0030/0038) | ❌ Design gap (`call_tree_budget_propagation`) | W2 |
| 5 | `cancellationPolicy` (`ChildFailurePolicy`) | §4 #19 (ADR-0019) | ⚠️ Sealed type exists; carrier not wired through `suspendForChild` | W2 |
| 6 | `deadline` (`Instant`) | §4 #19 (ADR-0019) | ⚠️ Mandated in `SuspendReason.deadline()`; watchdog deferred | W2 |
| 7 | `traceCorrelation` (traceparent + tracestate) | §4 #22 (ADR-0023) | ❌ Design gap (OTel propagation W2) | W2 |
| 8 | `attemptId` + retry policy | §4 #20 (ADR-0020) | ⚠️ `attemptId` field exists; child gets fresh `null` | W2 |
| 9 | `posture` (`dev`/`research`/`prod`) | §4 #32 (ADR-0035) | ⚠️ Implicit-only — global env var; not per-Run | W1+ explicit |
| 10 | `sessionId` (optional) | §4 #31 (ADR-0034 `MemoryMetadata`) | ❌ Not in SPI | W2 |
| 11 | `businessRuleSubsetRef` (C-Side authority) | §4 #47 (ADR-0049) | ❌ C/S-protocol level only; not in S-Side SPI | W2 contract |
| 12 | `placeholderPolicy` | §4 #49 (ADR-0051 `PlaceholderPreservationPolicy`) | ❌ Not encoded in spawn interface | W2 contract / W3 enforcement |
| 13 | `memoryOwnershipScope` (C-Side / S-Side / delegated) | §4 #49 (ADR-0051) | ❌ No accessible-memory marker on child | W2 contract |
| 14 | `idempotencyContext` | §4 #4 (ADR-0027) | ⚠️ Child gets fresh `null` | W1 promotion |
| 15 | `observabilityTags` (Micrometer + audit) | §4 #14 + §4 #22 (ADR-0023) | ⚠️ Edge-only at W0; per-span propagation W1+ | W1 / W2 |

Each dimension is tracked as a row in `docs/governance/architecture-status.yaml`. Where the dimension is already covered by an existing capability row (e.g. `tenant_context_filter`, `causal_payload_envelope`), this ADR cross-references; where the dimension is a new gap, a new row is added.

### The 5-boundary authority-transfer taxonomy

The platform recognizes five named authority-transfer boundaries. Each boundary has a named carrier and an explicit transfer point. Implicit transfer is forbidden at every boundary:

| Boundary | Carrier | ADR / §4 anchor |
|----------|---------|----------------|
| HTTP edge → Runtime | `TenantContextFilter` → MDC → `RunContext.tenantId()` | §4 #22, ADR-0023 |
| C-Side → S-Side | `HydrationRequest` / `ResumeEnvelope` | §4 #47, ADR-0049 |
| Parent Run → Child Run | `SpawnEnvelope` (this ADR) | §4 #51 (this ADR) |
| Run → External Skill | `PermissionEnvelope` | §4 #50, ADR-0052 |
| Run → Memory | C-Side / S-Side ownership split + `DelegationGrant` | §4 #49, ADR-0051 |
| Cross-Workflow | `CrossWorkflowHandoff` (this ADR) | §4 #51 (this ADR) |

Note the "Envelope" pattern across these boundaries — `PermissionEnvelope`, `ResumeEnvelope`, `SymbolicReturnEnvelope`, `CausalPayloadEnvelope`, and now `SpawnEnvelope`. The pattern: a typed record carrying the authority context needed to cross a boundary safely. Future authority boundaries SHOULD follow the same pattern.

### Required architecture rule (§4 #51)

> **Cohesive Agent Swarm Execution.** Agent-spawned child work must remain under the same workflow authority by default. A parent Run may spawn child Runs, delegated tasks, or subprocess-like work only through a `SpawnEnvelope` that preserves the 15 lifecycle dimensions defined in ADR-0053 (parent ref, tenant, permission scope, budget, cancellation policy, deadline, trace, attempt, posture, session, business-rule subset, placeholder policy, memory scope, idempotency, observability). Cross-workflow execution requires an explicit `CrossWorkflowHandoff` contract and must not occur implicitly. Implementation status per dimension is tracked in `docs/governance/architecture-status.yaml`.

### Non-Goals

This decision does NOT require:

- A complete distributed workflow engine at W0.
- A Java type for `SpawnEnvelope` at W0 (deferred to W2).
- Full implementation of all four `JoinPolicy` variants (W0 covers single-`ChildRun` per ADR-0019).
- A specific Temporal / Cadence / Camunda engine.
- A specific scheduler algorithm.
- Implementation of `CrossWorkflowHandoff` (W2+ design-only).

The L0 requirement is contract shape, named carriers, and the workflow-authority invariant.

### Out of scope

- Java SPI changes (deferred to W2). The W0 `suspendForChild(parentNodeKey, childMode, childDef, resumePayload)` signature is unchanged.
- Implementation of dimensions #3–#15 (design-only at L0; landing in W1/W2/W3 per the dimension table).
- Scheduler algorithm for `SwarmJoinPolicy` enforcement (W2).

## Consequences

**Positive:**
- Workflow-authority invariant is now a hard §4 constraint (#51), enforceable at gate time.
- The 15-dimension propagation gap is documented per dimension with explicit wave-status; no dimension can be silently dropped during W1/W2 implementation.
- 5-boundary authority-transfer taxonomy gives reviewers and implementers a complete map of where authority crosses and how.
- Envelope pattern is codified across boundaries.
- `CrossWorkflowHandoff` makes the escape hatch explicit so genuinely external work cannot be misclassified as "in-tree child".

**Negative:**
- W2 SPI work to land `SpawnEnvelope` Java type is non-trivial — 15 fields × dimension-specific semantics × backward compatibility with W0 `suspendForChild` signature.
- Documentation burden: per-dimension status must stay in sync between this ADR, §4 #51 prose, and `architecture-status.yaml` rows.
- Risk that `CrossWorkflowHandoff` becomes a wide escape valve if W1+ implementers are tempted to push everything through it. Mitigation: `CrossWorkflowHandoff` MUST emit audit-grade attestation (deferred to W2 audit-trail work).

## References

- `ARCHITECTURE.md` §4 #51 — the constraint this ADR anchors.
- `ARCHITECTURE.md` §4 #9 — parent-child entity model (Run + parentRunId + parentNodeKey).
- `ARCHITECTURE.md` §4 #19 — sealed `SuspendReason` taxonomy (ChildRun, AwaitChildren, JoinPolicy, ChildFailurePolicy, deadline).
- `ARCHITECTURE.md` §4 #20 — RunStatus DFA + audit trail; attemptId.
- `ARCHITECTURE.md` §4 #22 — tenant propagation purity (Rule R-C.e); MDC + Run.tenantId.
- `ARCHITECTURE.md` §4 #29 — RunScope STEP_LOCAL / SWARM + findRootRuns.
- `ARCHITECTURE.md` §4 #32 — posture single-construction-path (AppPostureGate).
- `ARCHITECTURE.md` §4 #47 — C/S Dynamic Hydration Protocol (HydrationRequest, ResumeEnvelope, BusinessRuleSubset).
- `ARCHITECTURE.md` §4 #49 — Memory and Knowledge Ownership Boundary (PlaceholderPreservationPolicy, DelegationGrant).
- `ARCHITECTURE.md` §4 #50 — Skill Topology Scheduler (PermissionEnvelope, CapabilityRegistry).
- ADR-0019 — SuspendReason taxonomy + JoinPolicy + ChildFailurePolicy.
- ADR-0020 — RunLifecycle SPI + RunStatus formal DFA + audit trail.
- ADR-0023 — Tenant context propagation purity + MDC + OTel deferral.
- ADR-0024 — Suspension write atomicity.
- ADR-0027 — Idempotency dedup deferral.
- ADR-0030 — Skill SPI lifecycle + SkillResourceMatrix.
- ADR-0032 — Scope-based run hierarchy + planner contract.
- ADR-0034 — Memory and knowledge taxonomy (M1-M6, MemoryMetadata).
- ADR-0035 — Posture enforcement single-construction-path.
- ADR-0038 — Skill SPI resource-tier classification.
- ADR-0049 — C/S Dynamic Hydration Protocol.
- ADR-0051 — Memory and Knowledge Ownership Boundary.
- ADR-0052 — Skill Topology Scheduler and Capability Bidding (PermissionEnvelope).
- `docs/logs/reviews/2026-05-13-l0-capability-labels-platformization.en.md` — reviewer input.
- `docs/logs/reviews/2026-05-13-l0-capability-labels-platformization-response.en.md` — class-organized response with hidden-defect callouts.
