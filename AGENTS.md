# AGENTS.md

## Language Rule

**Translate all instructions into English before any model call.** Never pass Chinese, Japanese, or other non-English text into an LLM prompt, tool argument, or task goal.

---

## Authoritative Sources (read these first)

This file is intentionally a **thin operational wrapper** for Codex / autonomous-agent harnesses. It does NOT carry the rule inventory or any baseline counts. The single sources of truth are:

| Topic | Authoritative file |
|---|---|
| Layer-0 governing principles (P-A..P-M) + Layer-1 engineering rules (active + deferred) | [`CLAUDE.md`](CLAUDE.md) |
| **Architecture authoring root (W5+ per ADR-0147)** | [`architecture/workspace.dsl`](architecture/workspace.dsl) + [`architecture/README.md`](architecture/README.md) — Structurizr DSL workspace closure (profile/features/docs/decisions/generated/views). L1 feature/function-point inventory at `architecture/features/`. |
| Per-capability shipped / deferred ledger; baseline counts (rules, ADRs, tests, gate rules, self-tests, nodes, edges) | [`docs/governance/architecture-status.yaml`](docs/governance/architecture-status.yaml) (the `architecture_sync_gate.allowed_claim` field is the canonical baseline; `#capabilities` authority migrates to `architecture/features/capabilities.dsl` at W6 yaml sunset) |
| Deferred sub-clauses + escalations | `deferred_sub_clauses:` frontmatter in each rule card under [`docs/governance/rules/`](docs/governance/rules/); legacy rules awaiting human review at [`docs/governance/escalations.md`](docs/governance/escalations.md) |
| ADRs (decision corpus) | [`docs/adr/`](docs/adr/) (every active rule cites its authority ADR) |
| Quickstart for new-agent onboarding | [`docs/quickstart.md`](docs/quickstart.md) |

**Why this is a thin wrapper:** prior versions of AGENTS.md carried an "Eleven active rules" tagline that was authored when CLAUDE.md held an 11-rule subset. CLAUDE.md has since grown well beyond that subset (current rule + principle counts live in `docs/governance/architecture-status.yaml#architecture_sync_gate.baseline_metrics`), while AGENTS.md was never regenerated. The v2.0.0-rc3 cross-constraint audit (P1-1 / β-6 / γ-1) and the v2.0.0-rc4 follow-up review P1-1 both flagged count drift across AGENTS / README / architecture-status as a defect family. The structural fix — applied here — is to stop carrying any baseline counts in AGENTS.md so the canonical source can evolve without dragging this file along.

---

## For AI assistants — load this set

> **The canonical, product-first orientation order is [`docs/onboarding/ai-understanding-path.md`](docs/onboarding/ai-understanding-path.md)** (machine-readable source of truth: [`docs/governance/ai-reading-path.yaml`](docs/governance/ai-reading-path.yaml); authority ADR-0159 + ADR-0160). It is the eight-node systems-engineering reading curve read **product-first** — Product Definition → Requirement Definition → L0 Architecture → EngineeringFrame → Feature/FunctionPoint → Contract Surface → Implementation Facts → Verification & Gate. **Skipping the product step to read code first is forbidden**: an agent that reads code before product builds the wrong thing efficiently.

A coding agent or LLM session reaches an **unbiased** picture by loading these surfaces in order. The six stops below name only the surfaces; `README.md#Reading-path` and the canonical YAML carry the per-step "what you should understand / what it does NOT carry" detail and are authoritative if the two ever disagree. Loading any one surface in isolation produces a partial view.

**For any factual claim about code, contracts, tests, dependencies, runtime behaviour, or verification**, read `architecture/facts/generated/*.json` BEFORE prose (Rule G-15 / ADR-0154). The authority cascade is one-way: **generated facts > DSL > Card/prose** — if prose disagrees with a fact, the fact wins. AI agents MUST NOT directly author or refresh files under `architecture/facts/generated/` — they are produced only by deterministic extractor binaries under `tools/architecture-workspace/` (Rule G-15.c).

1. **Repository entry** (orientation) — `README.md` + `CLAUDE.md` + `AGENTS.md`.
2. **Product definition** (Product → Requirement, ISO/IEC/IEEE 29148) — `product/PRODUCT.md` + `product/{claims,requirements,personas}.yaml` + `product/journey.md`.
3. **Architecture anchor** (L0, ISO/IEC/IEEE 42010 + C4) — `architecture/workspace.dsl` + `architecture/README.md` + `architecture/docs/L0/ARCHITECTURE.md` + `docs/adr/normalized/index.yaml` + `docs/adr/review-index.md` (read the normalized index for current ADR authority, not raw historical prose — ADR-0160).
4. **EngineeringFrame anchor** (C4 Component / arc42 L2 — a Java package-cluster anchor) — `architecture/features/engineering-frames.dsl` + `architecture/docs/L1/engineering-frames.md` + `architecture/docs/L1/frames/` + `architecture/docs/L1/` (per-module, indexed by `architecture/docs/L1/README.md`). A Feature *traverses* a frame; it never owns one (ADR-0157).
5. **Demand-to-behavior mapping** (Feature + FunctionPoint join) — `architecture/features/features.dsl` + `architecture/features/function-points.dsl` + `architecture/docs/L2/`.
6. **Contract and evidence** (Contract Surface → Implementation Facts → Verification & Gate) — `docs/contracts/contract-catalog.md` + `docs/contracts/` + `architecture/facts/generated/` + `gate/README.md` + `gate/check_architecture_sync.sh` + `docs/governance/architecture-status.yaml`. `docs/quickstart.md` + `docs/overview.md` are entered from here to run or narrate the system.

### AI consumption contract (Rule G-15 / ADR-0154)

AI agents using this repository's architecture surfaces MUST:

1. Read generated facts under `architecture/facts/generated/` BEFORE L1 prose for implementation decisions.
2. Cite fact IDs (e.g., `code-symbol/com.huawei.ascend.service.runtime.api.runscontroller`) when claiming what code, contract, or test exists.
3. Treat human-authored prose as **intent / rationale** — backed by ADRs and L1 narrative — not as the source of truth for code shape.
4. Refuse to act on stale or drifted generated artifacts (regenerate via `./mvnw -f tools/architecture-workspace/pom.xml exec:java@extract-facts` if drift suspected).
5. Run feature-specific verification commands derived from generated facts (e.g., `code_entrypoint_refs[]` + `test_refs[]` on `SAA FunctionPoint` elements in `architecture/features/function-points.dsl`), not from model memory.
6. Modify the **extractor binary** or the **source authority** (code, contract YAML, ADR) to change a fact — never the generated JSON directly.

### Rhetorical stance of each surface

Each surface above is a **distinct slice at a different altitude**; conflating "the architecture" with any single one produces a partial view. The per-surface "what it carries / what it does NOT carry" table lives once, in `README.md#Reading-path` and its machine-readable source `docs/governance/ai-reading-path.yaml` (human mirror: `docs/onboarding/ai-understanding-path.md`) — this wrapper does not duplicate it so the slice definitions cannot drift across files.

---

## Operational Conventions for Autonomous Agents

The following conventions are stable across rule-count changes and apply to every coding agent loaded into this repo:

1. **Before writing any code or plan**, follow Rule D-1 in CLAUDE.md (Root-Cause + Strongest-Interpretation). Surface assumptions; name the root cause in one sentence with `file:line` evidence; pick the strongest valid reading of the requirement.
2. **Before every commit**, run the Rule D-3.a Pre-Commit Checklist (contract truth · orphan config · error visibility · lint green · test honesty).
3. **For UI / frontend changes**, drive the feature through a real browser before declaring done (Rule from `Doing tasks` section).
4. **For Java verification**, use `./mvnw clean verify` not `./mvnw test` — the latter skips `*IT.java` (Failsafe) tests. This is a recurring trap; the v2.0.0-rc1 post-release wave landed 4 IT regressions because `test` was used.
5. **For gate verification**, use `bash gate/check_architecture_sync.sh` (canonical). The PowerShell entrypoint `gate/check_architecture_sync.ps1` was deprecated in v2.0.0-rc2 — it now exits 2 with a `DEPRECATED` banner.
6. **For any architecture decision**, walk the ADR corpus first (`docs/adr/0001-...yaml` through the highest-numbered ADR). Each rule in CLAUDE.md cites its authority ADR.
7. **For pull request bodies**, follow the format in `compound-engineering:git-commit-push-pr` skill if available, OR replicate the prior commit's HEREDOC + `Co-Authored-By` line.

---

## When to Update This File

AGENTS.md should change ONLY when:

- A new authoritative source is added (e.g., a new top-level corpus document) and needs to be listed in the "Authoritative Sources" table above.
- An operational convention changes that materially affects agent loop behavior (e.g., a new mandatory verification command).
- The thin-wrapper posture itself is being revisited.

AGENTS.md should **NOT** change when:

- A new engineering rule is added to CLAUDE.md (the count lives there).
- A new ADR is published (the corpus lives in `docs/adr/`).
- A baseline count moves (the canonical claim lives in `architecture-status.yaml`).
