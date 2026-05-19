---
rule_id: 94
title: "Active Corpus Deleted-Module Name Truth"
level: L0
view: development
principle_ref: P-D
authority_refs: [ADR-0083, ADR-0085, "rc8 post-corrective review P1-3", "rc10 post-corrective review noted-Rule-94-drift (user-elected widening)"]
enforcer_refs: [E129, E130]
status: active
kernel_cap: 8
kernel: |
  **Every active `.md`, `.yaml`, `.yml`, and `*.java` file in the repo (excluding `target/`, `.git/`, `node_modules/`, `docs/archive/`, `docs/adr/`, `docs/reviews/`, `docs/releases/2026-05-1[0-8]-*.md` + rc1..rc10 historical release notes, fenced code blocks, and the explicit historical-by-location exemption list defined in `gate/check_architecture_sync.sh` Rule 94 block) MUST NOT contain a current-tense word-boundary reference to the pre-Phase-C module names `agent-platform` or `agent-runtime` (the latter negative-filtered against `agent-runtime-core`) outside an explicit historical marker (`historical`, `pre-ADR-NNNN`, `pre-Phase-C`, `consolidated into`/`consolidated from`/`consolidation of`, `merged into`/`merger of`, `was rooted`, `formerly`, `superseded`, `deprecated`, `archived`, `moved`, `extracted per ADR-NNNN`/`Extracted from`, `post-ADR-NNNN`, `post-Phase-C`/`after Phase C`, `ADR-NNNN`, `subsumes prior`, etc.) within ±3 lines. Closes rc8 post-corrective P1-3 (rc9) + rc10 post-corrective Rule 94 kernel-vs-implementation drift (rc11): rc9 kernel said "every active .md/.yaml/.java" but the rc9 impl scanned only 3 narrow surfaces (ARCHITECTURE.md + rule cards + test Javadocs); rc11 widens the impl to match the kernel.**
---

# Rule 94 — Active Corpus Deleted-Module Name Truth

## Motivation

Rule 87 (rc7) prevented stale `agent-platform` / `agent-runtime` claims in `architecture-status.yaml#allowed_claim` text. The rc8 post-corrective review (P1-3) found that **equivalent current-tense claims still appeared** in:

- `ARCHITECTURE.md` §4 #59 — ArchUnit enforcement prose listed `agent-platform/web/replay/`, `agent-platform/web/trace/`, `agent-platform/web/session/` paths.
- `agent-service/src/test/java/.../McpReplaySurfaceArchTest.java` Javadoc — said "The rule lives in agent-platform" and "agent-runtime hosts no HTTP endpoints".
- `docs/governance/rules/rule-37.md` — said "Scope is intentionally narrow to `agent-runtime`" with existing `agent-platform` references.

The actual tests still check the current package names, so this was not a runtime failure — it was a contract-truth failure: an active L0 constraint teaches the wrong module path, and the gate didn't cover that surface.

Rule 94 widens Rule 87's scope from one yaml field to the entire active corpus (`.md`, `.yaml`, `.java`), with the same historical-marker exemption pattern.

## Algorithm

For each candidate file across the corpus-wide find (rc11 widening: every `.md`, `.yaml`, `.yml`, `.java` minus the build-artefact + historical-by-location + frozen-release exemption list — see `gate/check_architecture_sync.sh` for the canonical case branches):

1. Track fenced-code-block state (`^````).
2. Skip yaml comment lines (`^[[:space:]]*#`).
3. For each remaining line, test `\bagent-platform\b` OR (`\bagent-runtime\b` AND NOT `\bagent-runtime-core\b`).
4. On a match, look ±3 lines for any historical marker. If found, the match is exempt.
5. Otherwise, flag as a violation with file:line.

## Exemption list (rc11 widening)

The rc9 impl scanned 3 narrow surfaces; the rc11 widening flips the model to "scan everything **minus** an explicit exemption list":

**Build artefacts / version control** — `target/`, `**/target/*`, `.git/`, `node_modules/` (skipped at `find` time).

**Frozen-by-location** — `docs/archive/`, `docs/adr/`, `docs/reviews/`, `docs/releases/2026-05-1[0-8]-*.md`, `docs/releases/2026-05-19-l0-rc[1-9]-*` (single-digit rc1..rc9 historical), `docs/releases/2026-05-19-l0-rc10-*` (retracted), `docs/v6-rationale/`, `docs/delivery/`, `docs/plans/`, `docs/runbooks/`, `docs/cross-cutting/`, `docs/architecture-views/`, `docs/CLAUDE-deferred.md`, `docs/quickstart.md`.

**Generated artefacts** — `docs/governance/architecture-graph.yaml` (built by `gate/build_architecture_graph.py`), `agent-service/target/classes/*` (generated from `src/main/resources`).

**Surfaces that necessarily name the deleted modules** — `docs/governance/architecture-status.yaml` (allowed_claim narrative tracks wave history), `docs/governance/enforcers.yaml` (enforcer descriptions name what they check), `docs/governance/rule-history.md` (historical rule scope catalog), `docs/governance/principles/*` (principle cards reference deferred sub-clauses naming pre-Phase-C modules), `docs/governance/whitepaper-alignment-matrix.md`, `docs/governance/rules/rule-{87,93,94,98,33,37,21}.md` (rule cards about the leakage rule itself + retargeted-Rule-21/33/37 cards), `docs/telemetry/policy.md` (backward-compat metric tag), `docs/dfx/*` (DFX yaml descriptions naming subsumed prior artifacts), `agent-runtime-core/ARCHITECTURE.md` (kernel module names the legacy loop it broke), `perf/*` (perf docs name pre-Phase-C tests as W4 targets), `spring-ai-ascend-dependencies/module-metadata.yaml` (BoM description).

**Rule-98 domain (avoid duplicate-fail)** — `ops/*` and `docs/contracts/*` are Rule 98's primary scope; Rule 94 skips them to prevent both rules failing on the same hit.

**Live contract** — `docs/contracts/openapi-v1.yaml` (carries `x-contract-owner` metadata; separate update plan).

## Why this and not just expanding Rule 87

Rule 87 specifically targets the `allowed_claim:` field of `architecture-status.yaml` — a tightly-bounded vocabulary check. Rule 94 needs different ergonomics (multi-file, multi-language, fenced-block awareness, ±3-line marker window). Keeping them as separate rules makes each one auditable.

## Enforcement

Enforced by E129 (Gate Rule 94 — `active_corpus_deleted_module_name_truth`). Positive self-test: clean fixture passes. Negative self-test: a synthetic .md with `agent-platform/web/foo` on a line without a marker → FAIL; same with `pre-Phase-C` marker within ±3 lines → PASS.

## Activation

Activated 2026-05-19 by the v2.0.0-rc9 wave (rc8 post-corrective review response). Enforcer E129 + E130 (positive + negative self-test fixtures).

## Cross-references

- ADR-0078 — the Phase-C consolidation that deleted agent-platform and agent-runtime.
- ADR-0083 — rc9 corpus-truth + CI-acceptance authority record.
- Rule 87 — sibling: same family, narrower scope (`architecture-status.yaml#allowed_claim`).
- Rule 84 — same family family at the agent-*/ARCHITECTURE.md scope.
- Rule 86 — same family at the root ARCHITECTURE.md scope.
- `docs/reviews/2026-05-18-l0-rc8-post-corrective-architecture-review.en.md` finding P1-3 — origin.
