---
rule_id: 99
title: "Kernel Terminal-Verb vs Shipped-Decision Check"
level: L0
view: process
principle_ref: P-D
authority_refs: [ADR-0085, "rc10 post-corrective review P1-1"]
enforcer_refs: [E139, E140]
status: active
kernel_cap: 8
kernel: |
  **For every `#### Rule N` kernel block in `CLAUDE.md`, if a matching `## Rule N.<letter>` sub-clause exists in `docs/CLAUDE-deferred.md` AND the kernel body uses end-state verb tokens implying a shipped Run-state transition (`are SUSPENDED`, `is SUSPENDED`, `transitions to FAILED`, `consumes the * capacity`, `is rejected, not failed`, `admits the caller`), the gate MUST FAIL. The active kernel is overclaiming shipped behaviour when the matching obligation is explicitly deferred. Closes rc10 post-corrective review P1-1 (J-α family): Rule 41 kernel said "over-cap callers are SUSPENDED, not rejected" while `DefaultSkillResilienceContract.resolve` returns a decision envelope (`SkillResolution.reject(SuspendReason.RateLimited)`); the actual Run-state transition is deferred to Rule 41.c (W2 scheduler admission).**
---

# Rule 99 — Kernel Terminal-Verb vs Shipped-Decision Check

## Motivation

The rc8 post-corrective review (P1-1) found that Rule 42 + Rule 46 active kernels stated current-tense `MUST` for behaviour that `docs/CLAUDE-deferred.md` correctly assigned to W2 sub-clauses. The rc9 wave introduced Rule 96 to enforce the bidirectional link — each deferred sub-clause must be cited in the active kernel OR the rule card.

The rc10 post-corrective review (P1-1) then surfaced a SECOND form of the same drift: Rule 41 kernel said *"over-cap callers are SUSPENDED, not rejected"* while the shipped Java surface (`DefaultSkillResilienceContract.resolve`) returns a **decision envelope** (`SkillResolution.reject(SuspendReason.RateLimited)`), not a `Run.SUSPENDED` state transition. The deferred sub-clause Rule 41.c (introduced in rc11) covers the actual scheduler admission step.

Rule 96 alone cannot catch this defect because Rule 96 checks for the *literal* `Rule N.<letter>` reference — it doesn't read the kernel's verb. The kernel could (and did) include `(W2 scheduler admission per Rule 41.c)` while still saying *"are SUSPENDED"* in the main clause. The semantic claim is wrong even though the structural reference is present.

## What Rule 99 catches

Rule 99 is the SEMANTIC layer Rule 96 doesn't cover. The invariant: if a deferred sub-clause Rule N.<letter> exists in `CLAUDE-deferred.md`, the matching active kernel block for Rule N MUST NOT use end-state verbs that imply the deferred behaviour has shipped.

The end-state verb token list (rc11):

- `are SUSPENDED` / `is SUSPENDED` / `callers are SUSPENDED` — implies a `RunStatus.SUSPENDED` transition.
- `transitions to FAILED` / `transitions to SUSPENDED` — explicit state-machine claim.
- `consumes the * capacity` — implies actual capacity-counter mutation, not just decision-envelope return.
- `is rejected, not failed` — implies a Run-status outcome distinction.
- `admits the caller` — implies an admission decision that mutates scheduler state.

Decision-envelope-friendly verbs that PASS (the kernel is honestly describing what's shipped):

- `MUST return SkillResolution.reject(...)` — return value, not transition.
- `MUST consult the matrix` — schema-level enforcement.
- `MUST cite a yaml schema` — corpus-level enforcement.
- `MUST suspend via SuspendSignal` — describes a primitive call, but only paired with a deferred sub-clause naming the transition step.

## Algorithm

1. Parse `docs/CLAUDE-deferred.md` for `## Rule N.<letter>` headings → build the set of rule numbers with deferred sub-clauses.
2. For each `#### Rule N` heading in `CLAUDE.md`, extract the body (between heading and next `---`).
3. If Rule N has a deferred sub-clause AND the body contains any end-state verb token → FAIL with the rule id + matched verb.
4. Otherwise pass.

## Why not Rule 96 widening

Rule 96 enforces the *structural* invariant (literal `Rule N.<letter>` reference present). Rule 99 enforces the *semantic* invariant (kernel verb honestly describes shipped behaviour). Both are needed: the structural reference can be present while the semantic claim is wrong (rc10 P1-1 demonstrated this).

Widening Rule 96 to also check kernel verbs would conflate two cleanly separable invariants and make the rule harder to audit. Separate rules keep each one debuggable.

## Enforcement

Enforced by E139 (Gate Rule 99 — `kernel_terminal_verb_vs_shipped_decision_check`). Positive self-test: a Rule N kernel saying "MUST return SkillResolution.reject(...)" with matching Rule N.c deferred sub-clause → PASS. Negative self-test: a Rule N kernel saying "callers are SUSPENDED" with matching Rule N.c deferred sub-clause → FAIL.

## Activation

Activated 2026-05-19 by the v2.0.0-rc11 wave (rc10 post-corrective review response). Enforcer E139 + E140 (positive + negative self-test fixtures).

## Cross-references

- ADR-0085 — rc11 kernel-truth + shadow-corpus-precision authority record.
- Rule 96 — `kernel_deferred_clause_coherence` (sibling: structural reference invariant; Rule 99 covers the semantic-verb invariant).
- Rule 41 — first known instance of the drift Rule 99 catches; rc11 narrowed the kernel + added Rule 41.c sub-clause.
- `docs/reviews/2026-05-19-l0-rc10-post-corrective-architecture-review.en.md` finding P1-1 — origin.
