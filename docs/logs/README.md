# docs/logs/

Archive partition for historical material. **Not loaded by AI agents reading the normative authority surface.** Read on-demand for forensics, audit, or migration archaeology.

## Layout

| Path | Contents | Source |
|---|---|---|
| `releases/` | Release notes per RC cycle (rc4..rc11 + pre-rc baselines) | Moved from `docs/releases/` |
| `reviews/` | Architecture review proposals + responses | Moved from `docs/reviews/` |
| `waves/<rcN-name>/` | Per-wave findings + closure narratives extracted from rule kernels | Wave 3 scrub |
| `baselines/` | Per-rc baseline snapshots extracted from `architecture-status.yaml` | Wave 3 scrub |
| `adr-amendment-narratives/` | Inline `amendments:` blocks extracted from active ADRs + retired rcN-closure ADRs (0083/0084/0085) | Wave 1B + Wave 3 scrub |
| `code-refactorings/` | Narratives like "rc3 S2cCallbackSignal → SuspendSignal unification" | Wave 3 scrub |
| `governance-waves.md` | Single consolidated record of rc1..rc11 wave history (supersedes ADR-0083/0084/0085) | Wave 1A |
| `rule-migration-map.yaml` | Old rule N → new ID + sub-clause + verbatim assertion text (Wave 4 audit bridge) | Wave 4 ratchet |
| `principle-history.yaml` | Legacy P1/P2/P3/E1 principles extracted from `principle-coverage.yaml` | Wave 4 ratchet |
| `adr-triage-manifest.md` | Per-ADR classification (locked / active / archive) with reason | Wave 1B |
| `migration-coverage-report.md` | Wave 5 audit output: every old rule maps to ≥1 new sub-clause | Wave 5 |

## How to find context

- **"What changed in rc8?"** → `waves/rc8-post-corrective/findings.md` and `releases/2026-05-18-l0-rc8-corrective.en.md`.
- **"Why does Rule X have sub-clause Y?"** → `rule-migration-map.yaml` row for the old rule that became sub-clause Y.
- **"What did ADR-0083 decide?"** → `adr-amendment-narratives/0083-rc8-corrective-closure.md` (the ADR was absorbed into `governance-waves.md` at Wave 4).
- **"What was the rc6 architecture-graph node count?"** → `baselines/baselines-historical.yaml#rc6`.

## Do not delete

This partition is the audit trail. Locked-down per the migration plan; no rule body in `docs/governance/rules/` may directly cite these files except via the `[hist→docs/logs/...]` or `[snapshot→docs/logs/baselines/...]` footnote tokens (whitelisted by `gate/historical-marker-vocabulary.txt`).
