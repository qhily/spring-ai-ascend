---
level: L0
view: governance
status: draft
authority: "L0 consolidated reading entry; source material from archived L0 corpus, architecture workspace facts, and promoted draft material"
source_of_truth: true
---

# L0 Architecture Top-Level Design

## Purpose

This directory is the consolidated L0 architecture fact package for `spring-ai-ascend`.
It explains the system boundary, top-level 4+1 views, module and state
boundaries, cross-cutting constraints, governance rules, and shared vocabulary.

## Authority

The architecture authority root is `architecture/workspace.dsl`.

This L0 document package is a human-readable projection over:

- `architecture/workspace.dsl`, `architecture/features/`, `architecture/views/`,
  `architecture/generated/`, and `architecture/decisions/`.
- `docs/archive/ARCHITECTURE.md`, the archived legacy declarative L0 constraint
  corpus used as historical source material only.
- Draft material under `docs/architecture/l0/`, used only when promoted or
  explicitly marked as a pending decision.

When this package conflicts with `architecture/workspace.dsl`, accepted ADRs,
module metadata, or generated architecture facts, the canonical architecture
source wins until a new ADR changes it.

## Document Map

| File | Role |
|---|---|
| `README.md` | Entry, authority, reading path, and package boundaries. |
| `overview.md` | System goal, audience, runtime path, deployment variants, logical module boundary shape, quality attributes, and top-level risks. |
| `views.md` | L0 4+1 architecture views: logical, development, process, physical, and scenarios. |
| `boundaries.md` | Logical module admission, module responsibilities, downstream artifact treatment, and state ownership. |
| `constraints.md` | Cross-cutting verticals, invariants, and architectural constraints. |
| `governance.md` | Architecture workspace authority, promotion rules, layer update protocol, traceability, and open decisions. |
| `glossary.md` | Shared vocabulary and forbidden conflations. |

Contract and interface details are intentionally not defined in this directory.
Accepted runtime contracts belong in the contract catalog and related contract
documentation. L0 may reference contract categories, but it does not own wire
schemas, route behavior, SPI signatures, or machine-readable contract files.

## Reading Path

1. Read `overview.md` to understand the system shape.
2. Read `views.md` to understand the L0 4+1 view model.
3. Read `boundaries.md` before changing modules, state ownership, or runtime
   responsibility.
4. Read `constraints.md` before changing cross-cutting behavior.
5. Read `governance.md` before promoting draft material or changing multiple
   layers.
6. Read `glossary.md` whenever terms such as Task, Session, Gateway,
   Context Engine, Tool Gateway, C-Side, or S-Side are involved.

## Promotion Rule

Draft material under `docs/architecture/l0/` and review proposals under
`docs/logs/reviews/` can be used in three ways:

- Promote architecture facts into this L0 package or into L1/L2 architecture
  documents after conflict review.
- Promote scope, scenario, feature, harness, or delivery material into the
  version scope system.
- Archive material that is useful history but no longer describes the current
  architecture.

No draft material should be copied verbatim if it conflicts with accepted ADRs,
module metadata, generated facts, or this package's vocabulary.
