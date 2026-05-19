# ADR-0055 — Permit `agent-platform → agent-runtime`; Forbid the Reverse

- Status: Accepted (supersedes ADR-0026)
- Date: 2026-05-14
- Authority: L1 plan `l1-modular-russell` decision D1, user-approved.

## Context

L0 shipped with **zero** direct imports between `agent-platform` and `agent-runtime`. ADR-0026 (W0-era) anticipated this would be resolved at W1 by extracting an `agent-platform-contracts` module: a pure-Java module holding shared types (`Run`, `RunContext`, `Orchestrator`, etc.) that both modules would depend on.

At L1 planning the architect guidance (`docs/plans/2026-05-13-l1-architecture-design-guidance.en.md` §7.3, §15.5) ruled the contracts-module extraction out: *"Introduce `agent-platform-contracts` only when a real platform-runtime handoff needs shared DTOs or value types. Do not create it as speculative architecture scaffolding."*

L1 introduces a real handoff: `POST /v1/runs` requires `RunController` (in `agent-platform`) to call `Orchestrator.submit(...)` (in `agent-runtime`). Without either a contracts module or a direct dependency, the handoff cannot be expressed.

The choice between the two options reduces to:

- **Option A — Extract `agent-platform-contracts` (W1 target per ADR-0026)**: pure-Java module holding shared types; both modules depend on it.
- **Option B — Allow `agent-platform → agent-runtime` direct dependency**: smaller change; `agent-runtime`'s public types (`runs.*`, `orchestration.spi.*`) become directly callable from the HTTP layer.

Option A creates a third module for a handful of types. Option B keeps the module count at 4 and uses existing public types — matching the guidance's "don't extract speculatively" principle.

The negative invariant — **`agent-runtime` MUST NOT depend on `agent-platform`** — is preserved under both options. The platform's request-scoped concerns (`TenantContextHolder`, JWT filters, idempotency store) MUST stay invisible to runtime code, because runtime code runs in non-request contexts (timer-driven resumes, async orchestration) where those concerns are undefined.

## Decision

**`agent-platform` MAY depend on `agent-runtime` (Maven level, source level).**

**`agent-runtime` MUST NOT depend on `agent-platform` (Maven level, source level).**

The previously-symmetric Gate Rule D-6 is amended to enforce only the runtime→platform direction. A new ArchUnit test (`RuntimeMustNotDependOnPlatformTest`) generalises Rule R-C.e from the single `TenantContextHolder` class to the whole `ascend.springai.service.platform..` package.

The `agent-platform-contracts` module is **not** introduced at L1. If a future wave (W2+) identifies a real handoff that benefits from a third module, that decision lands in its own ADR.

## Enforcement

Rule R-C.a (Code-as-Contract) requires every constraint to have an executable enforcer. This ADR's invariants are backed by:

- **E1** — `gate/check_architecture_sync.sh` Rule D-6: agent-service/pom.xml does not depend on agent-platform.
- **E2** — `RuntimeMustNotDependOnPlatformTest` (ArchUnit): no class under `ascend.springai.service.runtime..` imports `ascend.springai.service.platform..`.
- **E4** — `HttpEdgeMustNotImportMemorySpiTest` (ArchUnit): `agent-platform` does not import `runtime.memory.spi..`.
- **E27** — `module_count_invariant`: root pom declares exactly 9 modules (bumped from 4 to 9 by the 2026-05-17 six-module materialization PR; canonical count now lives in `docs/governance/architecture-status.yaml#repository_counts.total_reactor_modules` and is data-driven cross-checked by Rule 64).

Self-tests in `gate/test_architecture_sync_gate.sh` exercise both PASS (no offending dep) and FAIL (offending dep injected) cases.

## Consequences

### Positive

- Smallest possible L1 module change (zero new modules).
- HTTP handoff uses existing public types — no boilerplate translation layer.
- The L1 invariant ("HTTP edge never imports memory SPI") is explicit and tested.
- Negative invariant on runtime→platform direction is generalised, not weakened.

### Negative

- `agent-platform` transitively pulls `agent-runtime`'s deps (Spring AI starters, Temporal SDK, pgvector). Acceptable: both modules ship as Spring Boot apps and the deps are needed anyway.
- If a future wave needs to extract a contracts module, the refactor is invasive (every `agent-platform → agent-runtime` import becomes `agent-platform → agent-platform-contracts`). Mitigated by keeping the imported surface narrow (`runs.*`, `orchestration.spi.*` only).

### Neutral

- ADR-0026 is marked **Superseded by ADR-0055**. The historical context remains useful.

## Alternatives Considered

### Option A — Extract `agent-platform-contracts` at L1

Rejected: architect guidance §7.3 explicitly forbids speculative extraction. The handoff surface at L1 is narrow (`Run`, `RunStatus`, `Orchestrator`, a few records) — not enough to justify a third module.

### Option C — Defer the run handoff itself

Rejected: defeats L1's purpose. The W1 HTTP API is the headline deliverable of L1.

## §16 Review Checklist

- [x] The module owner is clear (`agent-platform` owns the HTTP edge; `agent-runtime` owns the cognitive kernel).
- [x] The out-of-scope list is explicit (runtime→platform direction forbidden).
- [x] No future-wave capability is described as shipped (no contracts module introduced).
- [x] Spring bean construction has one owner (unchanged).
- [x] Configuration properties are validated and consumed (n/a for this ADR).
- [x] Tenant identity flow is explicit (Rule R-C.e generalised, not weakened).
- [x] Idempotency behavior is tenant-scoped (unchanged).
- [x] Persistence survives restart when claimed (n/a for this ADR).
- [x] Error status codes are stable (n/a for this ADR).
- [x] Metrics and logs exist for failure paths (gate/ArchUnit failures emit clear messages).
- [x] Tests cover unit, integration, and public contract layers (ArchUnit + gate-script).
- [x] `architecture-status.yaml` truth matches implementation (new `module_dep_direction_l1` row added).
- [x] The design does not weaken existing Rule R-C.d, Rule R-C.e, or Rule G-2 sub-clause .a constraints (Rule R-C.e strictly generalised; Rule R-C.d, 25 unchanged).

## References

- Supersedes ADR-0026.
- L1 plan: `D:\.claude\plans\l1-modular-russell.md` §5, §11 (E1, E2, E4, E27).
- ADR-0023 (tenant propagation purity, Rule R-C.e origin).
- ADR-0059 (Rule R-C.a, governs enforcement).
- Architect guidance §7.3, §15.5.
