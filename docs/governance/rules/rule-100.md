---
rule_id: 100
title: "Kernel-Implementation Disjunction Truth"
level: L0
view: process
principle_ref: P-D
authority_refs: [ADR-0085, "rc10 post-corrective review P1-3"]
enforcer_refs: [E141, E142]
status: active
kernel_cap: 8
kernel: |
  **For every rule listed in `gate/rule-100-disjunction-allowlist.txt`, BOTH the `#### Rule N` kernel block in `CLAUDE.md` AND the matching `docs/governance/rules/rule-NN.md` card MUST contain explicit disjunction wording (`EITHER` / `OR` / `either surface` / `either ... or ...` / `either kernel` / `either the`). The allow-list captures rules whose gate-script implementation uses `||` (disjunction) on the asserted conditions — the kernel and card MUST honestly declare the disjunction so a reader can reconcile the implemented policy with the documented contract. Closes rc10 post-corrective review P1-3 (J-γ family): Rule 96 kernel said "the matching CLAUDE.md kernel block MUST contain" while the impl accepted EITHER the kernel OR the rule card — a kernel-AND-impl-OR drift in the rule whose whole job is preventing kernel/deferred drift.**
---

# Rule 100 — Kernel-Implementation Disjunction Truth

## Motivation

The rc10 post-corrective review (P1-3) noted that Rule 96's kernel was narrow (`MUST contain` on the CLAUDE.md kernel block) while its implementation was broad (accepted EITHER the kernel OR the rule card). This is the worst class of Code-as-Contract drift: a rule whose JOB is preventing kernel/deferred drift contains kernel/impl drift of its own.

The rc11 wave (per ADR-0085) chose to keep the broader "either surface" implementation (cards have no kernel_cap, so a long deferred discussion can live there without bloating CLAUDE.md) and align the kernel + card wording to match. Rule 100 prevents recurrence by enforcing the bidirectional declaration: for every rule whose impl uses `||`-style disjunction on a structurally-important predicate, the kernel AND the card MUST both carry the EITHER/OR wording.

## Why allow-list scope, not corpus-wide

A fully-general "scan every `_rNN_*` block for `&&` vs `||` and cross-check against the kernel's `AND`/`OR` connective" rule is fragile:

- Bash predicate grammar varies (`[[ ... && ... ]]`, `[[ ... ]] || [[ ... ]]`, multi-stage checks via temp variables).
- Some rules use multi-stage checks where the surface AND-vs-OR doesn't map cleanly to a single connective.
- Many `&&`/`||` joins are structural (`[[ $? -eq 0 ]] || _fail=1`), not semantically load-bearing.

The allow-list captures only rules where the disjunction is *structurally load-bearing* — meaning the difference between AND-implementation and OR-implementation would change which corpus inputs pass.

Initial allow-list (rc11):

- Rule 96 — `kernel_deferred_clause_coherence` (CLAUDE.md kernel block OR rule card).

Future additions surfaced by J-γ family sweeps:

- Rule 48 (yaml schema OR grandfather entry) — needs verification.
- Rule 69 (active heading OR deferred reference) — needs verification.
- Rule 95 (catalog row OR `(internal)` mark) — needs verification.

Each addition requires kernel + card to explicitly declare EITHER/OR wording before the rule id lands in the allow-list. A new addition without kernel/card alignment will fail Rule 100 on its first run.

## Algorithm

1. Read `gate/rule-100-disjunction-allowlist.txt` (one rule id per line, `#` comments).
2. For each rule N in the allow-list:
   - Extract the `#### Rule N` block from `CLAUDE.md`.
   - Read the matching `docs/governance/rules/rule-NN.md` card.
   - Test BOTH for explicit disjunction tokens: `EITHER` (uppercase), `OR` (uppercase word), `either surface`, `either ... or`, `either kernel`, `either the`.
3. If either surface lacks the disjunction wording → FAIL with the rule id + (kernel=Y/N, card=Y/N) tally.

## Enforcement

Enforced by E141 (Gate Rule 100 — `kernel_implementation_disjunction_truth`). Positive self-test: a Rule N kernel + card both saying "EITHER the kernel OR the rule card" → PASS. Negative self-test: a Rule N kernel saying "the kernel block MUST contain" with `Rule N` added to the allow-list → FAIL (kernel=0, card=?).

## Activation

Activated 2026-05-19 by the v2.0.0-rc11 wave (rc10 post-corrective review response). Enforcer E141 + E142 (positive + negative self-test fixtures).

## Cross-references

- ADR-0085 — rc11 kernel-truth + shadow-corpus-precision authority record.
- Rule 96 — first allow-list entry; the rule whose kernel-AND-impl-OR drift this prevention rule catches.
- Rule 99 — sibling: semantic-verb invariant for deferred sub-clauses (Rule 99 covers `MUST` verbs that overclaim shipped behaviour; Rule 100 covers `AND/OR` connectives that mismatch the implementation).
- `gate/rule-100-disjunction-allowlist.txt` — the canonical allow-list.
- `docs/reviews/2026-05-19-l0-rc10-post-corrective-architecture-review.en.md` finding P1-3 — origin.
