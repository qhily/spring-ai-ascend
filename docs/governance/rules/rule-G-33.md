---
rule_id: G-33
title: "ADR ID Uniqueness"
level: L1
view: scenarios
principle_ref: P-C
authority_refs: [ADR-0160]
enforcer_refs: [E200]
status: active
scope_phase: design
kernel_cap: 8
governance_infra: true
scope_surfaces:
  - docs/adr
  - docs/adr/locked
  - architecture/facts/generated/adrs.json
  - gate/lib/check_adr_id_uniqueness.py
kernel: |
  An ADR number is the single authoritative IDENTIFIER for one architecture decision; it MUST be claimed by exactly one raw ADR source file. Rule G-33 asserts that no two raw ADR sources under `docs/adr/` (the YAML actives `docs/adr/*.yaml` whose number is the `id:` field, plus the legacy Markdown actives `docs/adr/*.md` and the locked Markdown `docs/adr/locked/*.md` whose number is the leading `# NNNN.` / `# ADR-NNNN` heading) declare the same ADR number. A duplicate is a structural lie the downstream ID-keyed projections silently collapse: both the fact extractor (`AdrFactExtractor`) and the DSL emitter (`AdrGraphFragmentEmitter`) key ADRs by their number, and `AdrGraphFragmentEmitter` builds a `TreeMap<String,…>` keyed by id, so N files sharing one number collapse to ONE map entry (last-writer-wins by file sort order) — silently dropping the other N−1 ADRs from `architecture/generated/adr-graph.dsl`, from `architecture/facts/generated/adrs.json`, and from the workspace closure; a reviewer (or an AI agent) reading "ADR-NNNN" cannot tell which of the colliding decisions the number names. This is the corpus-level governance-clarity failure the progressive-learning-curve remediation closes. The single gate Rule 150 invokes `gate/lib/check_adr_id_uniqueness.py` (E200), which globs the raw ADR sources, extracts each file's declared ADR number (YAML `id:` for `*.yaml`; the leading ADR-number heading for `*.md`), and reports any number claimed by two or more files (`DUPLICATE-ID`) plus any file whose ADR number cannot be parsed (`UNPARSEABLE-ID`). It invents no ADR id and no relationship and never outranks a generated fact (cascade: generated facts > DSL > Card/prose): it reads the raw sources as the identity authority and treats `architecture/facts/generated/adrs.json` as the apex factual cross-check (Rule G-15 / ADR-0154) — a number that resolves in the fact layer to a different raw path than the one it scanned is the same collision surfaced from the fact side. A NON-VACUITY GUARD fails the rule closed (exit 2) when the glob matches ZERO ADR source files — a format/path drift that silently empties the scan set is never a pass; the check is materially vacuous only if `docs/adr/` itself is absent (greenfield). A missing helper fails closed; a missing python interpreter is a vacuous pass (Rule G-7 lists WSL as the canonical env). Runs BLOCKING at this rung per the ADR-0160 ledger-totality model: a duplicate ADR number breaks the one-entry-per-raw-ADR ledger keying and the one-normalized-view-per-accepted-ADR coverage G-28 asserts, so unlike the lane-purity ratchets there is no advisory soak — a colliding identifier is a hard fail the instant the corpus carries one.
---

# Rule G-33 — ADR ID Uniqueness

## What

Pins the ADR number as a **singly-claimed identifier**: exactly one raw ADR
source file may declare any given ADR number. The rule scans the raw ADR sources
ADR-0160 governs and fails closed when a number is claimed by two or more files,
so the silent last-writer-wins collapse in the ID-keyed extractors becomes a
gate-able defect instead of unobservable data loss.

The raw ADR sources carry their number in two shapes, both scanned:

- `docs/adr/*.yaml` — the YAML actives; the number is the `id:` field
  (`id: ADR-NNNN`).
- `docs/adr/*.md` and `docs/adr/locked/*.md` — the legacy active and the locked
  Markdown ADRs; the number is the leading first-heading ADR number
  (`# NNNN. …` or `# ADR-NNNN — …`), not a YAML field.

The authority direction is fixed and one-way; the rule reads the raw sources +
the apex fact and asserts none of its own:

    ADR-0160 (ADR Governance Model — one ledger entry per raw ADR; one normalized
              view per accepted ADR)
      -> docs/adr/**  (the raw sources: the identity authority for which numbers exist)
        -> architecture/facts/generated/adrs.json  (the apex factual cross-check, ADR-0154 / Rule G-15)
          -> gate Rule 150 / E200  (this check)

The rule runs over the scanned sources:

- DUPLICATE NUMBER. Two or more raw ADR files declare the same number. →
  `DUPLICATE-ID` (the finding names every file that claims the number).
- UNPARSEABLE NUMBER. A scanned ADR file carries no extractable number (a
  malformed `id:`, or a Markdown ADR with no leading ADR-number heading). →
  `UNPARSEABLE-ID` (a file whose identity cannot be read cannot be proven unique).
- NON-VACUITY. The glob matches ZERO ADR source files while `docs/adr/` exists —
  a path/format drift that silently empties the scan set. → fails closed
  (exit 2). The check is materially vacuous (clean, every mode) only when
  `docs/adr/` itself is absent (greenfield).

## Why

ADR-0160 makes the ADR number the keying identity of the whole decision spine:
the remediation ledger keys exactly one entry per raw ADR, and G-28 (ADR
Normalization) requires exactly one normalized view per accepted ADR number — a
duplicate number can satisfy neither uniquely. The 2026-05-29 engineering-
governance systemic-remediation wave hit this concretely: three methodology-
ratification ADRs were drafted all carrying one number, after which the fact
extractor enumerated only the file that sorted last and the graph emitter would
have emitted a single node for all three (the `F-adr-id-collision-across-files`
family). The collision was resolved by renumbering, but no executable gate yet
failed closed on a FUTURE duplicate — the candidate ID-uniqueness check sat in the
free `G-27..G-33` band as planning input. This rule lands it: a multiply-claimed
ADR number is a finding the gate reports and blocks, rather than a silent
TreeMap collapse that drops the other decisions and leaves a reviewer unable to
tell which decision "ADR-NNNN" names. The check stays subordinate to the authority
spine — it reads which numbers the raw sources declare and which the fact layer
enumerated; it never mints an ADR number, and it never outranks a generated fact
(cascade: generated facts > DSL > Card/prose).

## How it works

The single gate Rule 150 invokes one helper:

- `gate/lib/check_adr_id_uniqueness.py` (E200) — globs the raw ADR sources
  (`docs/adr/*.yaml`, `docs/adr/*.md`, `docs/adr/locked/*.md`), extracts each
  file's declared ADR number (YAML `id:` for `*.yaml`; the leading ADR-number
  heading for `*.md`), and groups files by number. It reports, file-oriented and
  naming every colliding path, a short machine code per finding (`DUPLICATE-ID`,
  `UNPARSEABLE-ID`). It cross-checks `architecture/facts/generated/adrs.json` as
  the apex factual authority — a number the fact layer resolves to a different
  raw path than the file that declared it is the same collision surfaced from the
  fact side. It invents no ID and no relationship and never outranks a generated
  fact — it is a classifier over the raw ADR identifiers.

Non-vacuity guard. The check FAILS CLOSED (exit 2) when its glob matches zero
ADR source files while `docs/adr/` exists — a non-vacuity guard for an
auto-discovering scan, so a renamed directory or a changed file extension cannot
silently empty the scan set and report a false pass. The check is materially
vacuous (clean in every mode) only when `docs/adr/` itself is absent — the
greenfield case in which the corpus has no ADR to key.

Authority cross-check. `architecture/facts/generated/adrs.json` is read as a
cross-check, never as the sole input: the fact layer enumerates only the YAML
ADRs and (until the extractor is widened) under-counts the Markdown corpus, so
the raw sources remain the identity authority for which numbers exist. The fact
layer confirms the YAML half from the apex side; the Markdown half is proven from
the raw files directly.

## Ratchet

BLOCKING from the start (no advisory soak). Unlike the lane-purity, readiness,
and reading-path gates (Rules 145–149 / G-27..G-32), which ratchet
advisory → changed-files-blocking → full-blocking while a corpus is brought
clean, an ADR-number collision is never a tolerable interim state: it silently
drops decisions from the fact layer and the graph and breaks the ADR-0160 ledger
+ normalized-view keying the moment it exists. The check therefore lands blocking
— a duplicate or unparseable ADR number is a hard fail, with no grandfather list
and no changed-files scoping (the identifier space is global; a collision is a
collision regardless of which file a PR touched). A missing helper fails closed;
a missing python interpreter is a vacuous pass (Rule G-7 lists WSL as the
canonical env).

## Test fixtures

  - VALID  : an absent `docs/adr/` is materially vacuous (greenfield) — every mode
             passes with zero findings.
  - VALID  : a corpus in which every raw ADR file declares a distinct number
             (YAML `id:` + Markdown headings all unique) passes blocking with zero
             findings.
  - INVALID: two `*.yaml` ADRs declaring the same `id: ADR-NNNN` yield
             `DUPLICATE-ID` naming both files and fail closed (blocking).
  - INVALID: a `*.yaml` ADR and a `docs/adr/locked/*.md` ADR claiming the same
             number (one via `id:`, one via its `# ADR-NNNN` heading) yield
             `DUPLICATE-ID` across formats.
  - INVALID: a scanned ADR file with no extractable number (a malformed `id:`, or
             a Markdown ADR with no leading ADR-number heading) yields
             `UNPARSEABLE-ID`.
  - INVALID: a glob that matches zero ADR source files while `docs/adr/` exists
             (a renamed dir / changed extension) fails closed (exit 2) — the
             non-vacuity guard, never a silent pass.
  - INVALID: a number whose `adrs.json` row points at a different raw path than the
             file that declared it yields `DUPLICATE-ID` from the fact-side
             cross-check.
  - INVALID: a vanished/unreadable `architecture/facts/generated/adrs.json` fails
             closed (exit 2) — a missing apex authority is never a pass.

## Cross-references

  - ADR-0160 — ADR Governance Model (the authority this rule enforces: one ledger
    entry per raw ADR keyed by its number, one normalized view per accepted ADR
    number; a duplicate number satisfies neither uniquely)
  - ADR-0154 — Fact-Layer Authority (the cascade `generated facts > DSL >
    Card/prose` this rule never outranks; `adrs.json` is the apex raw-ADR
    enumeration the cross-check reads)
  - ADR-0159 — Progressive Learning Curve and Authority Lanes (the decision spine
    whose authority traces to an accepted ADR; this rule keeps that spine's
    identifier space single-claimed, the structural precondition for the lane and
    readiness gates that cite ADR numbers)
  - Rule G-15 — Fact-Layer Integrity (`adrs.json` is the apex raw-ADR fact this
    rule cross-checks; G-15 guards the byte-identity of that fact, this rule guards
    the uniqueness of the identifiers it enumerates)
  - Rule G-28 — ADR Normalization (the ledger-totality + normalized-view-coverage
    gate whose one-view-per-accepted-ADR-number invariant a duplicate number
    breaks; this rule is the upstream identity guard that makes G-28's keying
    well-defined)
  - Rule G-7 — Linux-First Dev Environment (the helper is run via WSL/Linux; a
    missing python interpreter is a vacuous pass)
