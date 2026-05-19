---
rule_id: 98
title: "Broad-Corpus Deleted-Module-Name Truth"
level: L0
view: development
principle_ref: P-D
authority_refs: [ADR-0078, ADR-0084, ADR-0085, "rc10 category-sweep I-ε family", "rc10 post-corrective review P1-2"]
enforcer_refs: [E137, E138]
status: active
kernel_cap: 8
kernel: |
  **Files under `ops/**/*.{yaml,yml,tpl,md}`, `docs/contracts/*.yaml`, and `**/module-metadata.yaml` (excluding `docs/archive/`, `docs/reviews/`, and `docs/releases/2026-05-1[0-7]-*.md`) MUST NOT contain word-boundary current-tense references to the pre-Phase-C module names `agent-platform` or `agent-runtime` (the latter NOT matching `agent-runtime-core`) — including inside YAML comment lines — outside an explicit historical marker (`historical`, `pre-ADR-NNNN`, `pre-Phase-C`, `consolidated into`, `merged into`, `was rooted`, `formerly`, `superseded`, `deprecated`, `archived`, `moved`, `extracted per ADR-NNNN`, `post-ADR-NNNN`, `forbidden_dependencies`, etc.) within ±3 lines. Closes rc10 category-sweep I-ε family (rc10) + rc10 post-corrective P1-2 (rc11): operational Markdown runbooks (`ops/**/*.md`) and YAML comment lines were outside the original Rule 98 scope, allowing 4 leaks to survive rc10's prevention wave.**
---

# Rule 98 — Broad-Corpus Deleted-Module-Name Truth

## Motivation

The rc8 post-corrective review's P1-3 finding fixed deleted-module-name leakage in three specific surfaces (ARCHITECTURE.md §4 #59, `McpReplaySurfaceArchTest` Javadoc, `rule-37.md`), and rc9 added Rule 94 to prevent recurrence. But Rule 94's **kernel** said "every active `.md`, `.yaml`, and `*.java` file" while its **implementation** scanned only three narrow surfaces: root `ARCHITECTURE.md`, `docs/governance/rules/*.md` (one level), and `agent-*/src/test/java/**/*{Test,IT}.java`. The rule body explicitly exempted:

- `docs/contracts/openapi-v1.yaml` ("separate update plan; carries x-contract-owner metadata")
- `*/src/test/resources/*` (test fixtures, including pinned contract snapshots)
- `docs/adr/*` (frozen ADR artifacts — legitimate)
- `docs/plans/*`, `docs/runbooks/*`, `docs/quickstart.md`, several other docs subtrees

The `ops/` directory was NOT in the exemption list, but the file-discovery `find` block also did not include it — so Rule 94 silently never scanned `ops/`. The kernel-vs-implementation drift was itself an undiscovered defect.

The rc10 category sweep (defect family I-ε) found ~7 hidden leaks Rule 94 missed in the un-scanned surfaces:

- `spring-ai-ascend-dependencies/module-metadata.yaml:9` description: "Bill of Materials — pins agent-platform, agent-runtime, ..."
- `ops/helm/spring-ai-ascend/values.yaml:7` `repository: springaiascend/agent-platform`
- `ops/helm/spring-ai-ascend/templates/deployment.yaml:18` `- name: agent-platform`
- `ops/helm/spring-ai-ascend/Chart.yaml:9` keyword `agent-platform`
- `docs/contracts/openapi-v1.yaml:287` `x-contract-owner: agent-platform`
- `docs/contracts/openapi-v1.yaml:294` note: "Integration test: agent-platform RunCursorFlowIT..."
- `agent-service/src/test/resources/contracts/openapi-v1-pinned.yaml:265` mirrored the live contract

## Algorithm

Rule 98 reuses Rule 94's word-boundary awk regex (`(^|[^a-zA-Z0-9_-])agent-platform([^a-zA-Z0-9_-]|$)` and the parallel `agent-runtime` pattern with negative-lookahead-style exclusion of `agent-runtime-core`), and Rule 94's ±3-line marker exemption set.

The DIFFERENCE is file-discovery scope. Rule 98 scans:

- `ops/**/*.yaml`, `ops/**/*.yml`, `ops/**/*.tpl` — operational infra (Helm charts, Kubernetes manifests)
- `docs/contracts/*.yaml` — live API contracts (single-level, excludes versioned subdirectories like `docs/contracts/openapi-v1.yaml-pinned/` if they exist)
- `**/module-metadata.yaml` (any depth ≤ 3, excludes `target/`, `.git/`, `docs/archive/`) — per-module metadata that often describes BoM contents, allowed/forbidden deps, etc.

The exemption marker list includes `forbidden_dependencies` so that intentional `forbidden_dependencies: - agent-platform` entries in `agent-bus/module-metadata.yaml`, `agent-client/...`, `agent-evolve/...`, `agent-middleware/...` pass — those lists exist precisely to NAME deleted modules and prevent reintroduction.

## Why not just widen Rule 94's scope

Two reasons:

1. **Audit trail**: Rule 98 has its own enforcer pair (E137 + E138) so the gate log + the rc10 release note both clearly attribute the new coverage to a specific wave + ADR. Modifying Rule 94 in-place would lose that traceability.
2. **Reviewer scope preservation**: Rule 94's reviewer scope was deliberately narrow ("active root architecture, rule cards, and active test Javadocs") per rc8 post-corrective review P1-3 reviewer language. Widening Rule 94 silently would change what the rc8 reviewer originally signed off on. Rule 98 adds a sibling rule that explicitly covers the broader surface, with its own kernel and review attribution.

The kernel-vs-implementation drift in Rule 94 (kernel said "every", implementation scanned three surfaces) is itself worth a future amendment — likely an ADR-0085 follow-up that either (a) narrows Rule 94's kernel to match its implementation, or (b) widens Rule 94's implementation to match its kernel. rc10 chooses option (b) via sibling Rule 98 to minimize scope churn.

## Enforcement

Enforced by E137 (Gate Rule 98 — `broad_corpus_deleted_module_name_truth`). Positive self-test: synthetic `ops/helm/test.yaml` with only `agent-service` references → pass. Negative self-test: synthetic file with bare `agent-platform` reference and no historical marker → fail. Positive: same bare reference but with `pre-Phase-C` marker on adjacent line → pass.

## Activation

Activated 2026-05-19 by the v2.0.0-rc10 wave (rc8 post-corrective review category-sweep follow-up). Enforcer E137 + E138.

## Cross-references

- ADR-0078 — agent-service consolidation (the structural deletion that creates the leakage surface).
- ADR-0084 — rc10 corpus-truth + prevention-widening authority record.
- Rule 94 — `active_corpus_deleted_module_name_truth` (Rule 98's narrower predecessor — sibling rule for the surfaces Rule 94 doesn't cover).
- Rule 87 — `status_yaml_allowed_claim_module_name_truth` (Rule 98's even-narrower predecessor — together they form the layered defense against deleted-module-name leakage: status-yaml → active corpus narrow → broad corpus).
- `docs/reviews/2026-05-18-l0-rc8-post-corrective-architecture-review.en.md` category-sweep follow-up — origin.
