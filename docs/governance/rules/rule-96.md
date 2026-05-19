---
rule_id: 96
title: "Kernel-Deferred Clause Coherence"
level: L0
view: process
principle_ref: P-D
authority_refs: [ADR-0083, ADR-0085, "rc8 post-corrective review P1-1", "rc10 post-corrective review P1-3"]
enforcer_refs: [E133, E134]
status: active
kernel_cap: 8
kernel: |
  **For every `## Rule N.<letter>` sub-clause heading in `docs/CLAUDE-deferred.md`, EITHER the matching `#### Rule N` kernel block in `CLAUDE.md` (between the heading and the next `---`) OR the matching `docs/governance/rules/rule-NN.md` card MUST contain the literal string `Rule N.<letter>` to acknowledge the deferred runtime obligation. Closes rc8 post-corrective review P1-1 + rc10 post-corrective review P1-3 (kernel-vs-implementation alignment): cards have more room than always-loaded kernels, so a rule with a long deferred discussion can cite there without bloating CLAUDE.md.**
---

# Rule 96 — Kernel-Deferred Clause Coherence

## Motivation

The rc8 post-corrective review (P1-1) found that two active rule kernels in `CLAUDE.md` stated current-tense `MUST` for behavior that `docs/CLAUDE-deferred.md` correctly deferred to W2:

- Rule 42 kernel said `The runtime SandboxExecutor MUST refuse a logical permission grant whose scope exceeds the declared physical limits.` — but `CLAUDE-deferred.md` 42.b deferred runtime refusal.
- Rule 46 kernel said `Callbacks consume the s2c.client.callback skill capacity declared in docs/governance/skill-capacity.yaml.` — but `CLAUDE-deferred.md` 46.b deferred runtime capacity admission to W2.

Two authoritative sources disagreeing creates a logical conflict for implementers: one says it's current `MUST`, the other says it's deferred. Rule 9 (self-audit ship gate) and Rule 28 (Code-as-Contract) cannot both be satisfied when active prose overclaims runtime enforcement.

The structural fix is the bidirectional link: the deferred sub-clause exists (CLAUDE-deferred.md 42.b, 46.b, etc.), AND the active kernel must explicitly reference it by name (`Rule 42.b`, `Rule 46.b`). That way readers see both halves of the truth at the kernel-reading step.

## Algorithm

The gate parses `docs/CLAUDE-deferred.md` for sub-clause headings of the form `## Rule N.<letter>` (e.g. `## Rule 42.b — SandboxExecutor Subsumption Runtime Check`). For each, the gate checks two surfaces:

1. The matching `#### Rule N` kernel block in `CLAUDE.md` (from the heading to the next `---`) — `_r96_kernel_has`.
2. The matching `docs/governance/rules/rule-NN.md` card — `_r96_card_has`.

Coherence is satisfied if **either** surface contains the literal substring `Rule N.<letter>` (e.g. `Rule 42.b`). Only if BOTH are absent does Rule 96 fail.

If Rule N itself is deferred (not present as `#### Rule N` in CLAUDE.md), the check is skipped — the rule isn't active so it has no active kernel/card obligation to acknowledge sub-clauses.

## Why "either kernel or card" instead of "kernel only"

The rc10 post-corrective review (P1-3) noted that the original Rule 96 kernel said "the matching `CLAUDE.md` kernel block MUST contain" while the implementation accepted EITHER the CLAUDE kernel OR the rule card. The kernel-narrow-impl-broad drift was a Code-as-Contract violation in the rule whose whole job is preventing such drift. rc11 aligned the kernel + card + enforcer wording to the implemented "either surface" policy.

The justification for the broader policy: rule cards have no `kernel_cap`, so a rule with a long deferred discussion can cite the sub-clause in the card without bloating the always-loaded kernel. The structural invariant (the bidirectional link exists between deferred sub-clauses and active rules) is preserved regardless of which surface holds the literal reference.

## Why literal-string match, not semantic equivalence

Semantic equivalence checks ("does the kernel describe the same deferred behavior?") would need natural-language understanding and would be fragile. The literal `Rule N.<letter>` reference is a cheap, audit-friendly invariant: if either surface cites the sub-clause ID, the bidirectional link exists; if neither does, the link is broken regardless of how the kernel/card describes the behavior.

## Enforcement

Enforced by E133 (Gate Rule 96 — `kernel_deferred_clause_coherence`). The enforcer's truth-table:

| Kernel cites `Rule N.b`? | Card cites `Rule N.b`? | Outcome |
|---|---|---|
| Yes | Yes | PASS |
| Yes | No  | PASS (kernel-only path) |
| No  | Yes | PASS (card-only path) |
| No  | No  | FAIL — broken bidirectional link |

Positive self-tests:
- `test_rule_96_kernel_pos`: a Rule N kernel containing `Rule N.b` → PASS.
- `test_rule_96_card_only_pos`: a Rule N.b deferred sub-clause cited only in the rule card → PASS (rc11 addition).

Negative self-test:
- `test_rule_96_neg`: a Rule N.b deferred sub-clause with no reference in either the kernel or the card → FAIL.

## Activation

Activated 2026-05-19 by the v2.0.0-rc9 wave (rc8 post-corrective review response P1-1).

Kernel + card + enforcer wording aligned to the "either surface" policy by the v2.0.0-rc11 wave (rc10 post-corrective review response P1-3 + ADR-0085).

## Cross-references

- ADR-0083 — rc9 corpus-truth + CI-acceptance authority record.
- Rule 9 — self-audit ship gate (Rule 96 is a structural precondition for Rule 9 to be satisfied without false-positive findings against deferred obligations).
- Rule 28 — Code-as-Contract (active `MUST` requires enforcer; Rule 96 surfaces the case where the `MUST` should be deferred-pointing prose instead).
- `docs/reviews/2026-05-18-l0-rc8-post-corrective-architecture-review.en.md` finding P1-1 — origin.
