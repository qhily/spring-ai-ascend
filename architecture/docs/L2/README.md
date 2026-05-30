---
level: L2
view: scenarios
status: scaffold
authority: "ADR-0068 (Layered 4+1 + Architecture Graph)"
---

# L2 — Technical Detailed Designs

This directory holds **L2** documents in the Layered 4+1 corpus defined by Rule 33 / ADR-0068.

## Scope

L2 documents are **per-feature** technical-detail designs that bind specific implementation classes, packages, sequences, and physical placements to the L0 / L1 contracts above. Each L2 document:

- Targets one feature or one use case (not a module, not the whole system).
- May omit views that are not relevant to the feature (Rule 33).
- Must declare `level: L2` + `view: {logical|development|process|physical|scenarios}` front-matter.
- Must link upward to the L1 module document(s) it specialises and the ADRs it implements.
- Lives here under `docs/L2/<feature-slug>/<view>.md` or `docs/L2/<feature-slug>.md`.

## Naming conventions

```
docs/L2/run-http-contract/logical.md         # L2, logical view, run HTTP contract
docs/L2/run-http-contract/process.md         # L2, process view, idempotency body lifetime
docs/L2/telemetry-vertical/development.md    # L2, development view, package layout
docs/L2/fp-create-run/README.md              # L2, per-FunctionPoint spec (one entry verb)
```

A single-file form (`docs/L2/<feature>.md`) is permitted when only one view is
in scope. A per-FunctionPoint spec uses the lowercase `fp-<name>/README.md`
directory form, where `fp-<name>` is the kebab-lowercase of the FunctionPoint
`saa.id` (`FP-CREATE-RUN` → `fp-create-run`). The lowercase slug is load-bearing:
the readiness gate (`gate/lib/check_feature_readiness.py`) probes the lowercased
slug, and a case-sensitive CI filesystem will not resolve an upper-cased
directory.

## Gate behaviour

- Gate Rule 37 (`architecture_artefact_front_matter`): every file in this tree (excluding this README) must declare `level: L2` + `view:` front-matter.
- Gate Rule 38 (`architecture_graph_well_formed`): every L2 file must link upward to at least one L1 or L0 node (resolves through the `relates_to:` or `extends:` edges in the L2 front-matter).
- Gate Rule 39 (`review_proposal_front_matter`): proposals in `docs/logs/reviews/` touching L2 files must declare `affects_level: L2`.

## Current contents

The L2 corpus has two shapes of document, both per-detail and never per-module:

**Feature / vertical sinks** — multi-view technical-detail designs keyed by a
feature or use-case slug, carrying the wire matrix, body-lifetime, sequence, and
package-layout detail drained out of L0 / L1 prose by the layer-purity verdict
(Rule 145 / E194-E195):

| Slug | Detail it homes |
|---|---|
| `run-http-contract` | the authenticated `POST /v1/runs` status-code matrix, request/response field shapes, filter-chain ordering, idempotency body-lifetime, and the cancel-vs-complete race resolution |
| `telemetry-vertical` | the hook-outcome telemetry vertical (logical + process views) |
| `engine-port-boundary` | the neutral transport-agnostic `EnginePort` Service↔Engine boundary (development / process / physical / scenarios views, ADR-0158) |

**Per-FunctionPoint specs** — one `fp-<name>/README.md` per shipped
FunctionPoint, the method-level detail home (entry method, runtime sequence,
error matrix, contract + test evidence) for one entry verb. Each is a READABLE
INTERPRETATION layer (ADR-0161 / Rule 146): it invents no FunctionPoint ID,
frame ID, operation ID, status code, or method name — every identity is copied
from the authoring DSL (`architecture/features/function-points.dsl`,
`engineering-frames.dsl`) and every fact is cited from the generated facts.
Authored from [`_function-point-template.md`](_function-point-template.md):

| Slug | Owning EngineeringFrame |
|---|---|
| `fp-create-run` | `EF-ACCESS-ADMISSION` |
| `fp-get-run-status` | `EF-ACCESS-ADMISSION` |
| `fp-idempotency-claim` | `EF-ACCESS-ADMISSION` |
| `fp-tenant-cross-check` | `EF-ACCESS-ADMISSION` |
| `fp-posture-boot-guard` | `EF-ACCESS-ADMISSION` |
| `fp-cancel-run` | `EF-TASK-CONTROL` |
| `fp-suspend-resume` | `EF-TASK-CONTROL` |
| `fp-child-run-spawn` | `EF-TASK-CONTROL` |
| `fp-run-state-transition` | `EF-SESSION-TASK-STATE` |
| `fp-ingress-envelope` | `EF-INGRESS-GATEWAY` |
| `fp-s2c-callback` | `EF-S2C-TRANSPORT` |
| `fp-engine-dispatch` | `EF-ENGINE-REGISTRY` |
| `fp-hook-dispatch` | `EF-HOOK-SURFACE` |
| `fp-graph-memory-store` | `EF-GRAPHMEMORY-AUTOCONFIG` |

The owning-frame column is the `anchors` edge in
`architecture/features/engineering-frames.dsl`; it is the authority, this table
a readable view of it. The four `design_only` A2A / MQ FunctionPoints
(`FP-A2A-MESSAGE-SEND`, `FP-A2A-TASKS-CANCEL`, `FP-A2A-TASKS-RESUBSCRIBE`,
`FP-MQ-INBOUND`) carry no L2 spec yet — they have no generated facts to cite.

## Authority

- Rule 33 — Layered 4+1 Discipline (`CLAUDE.md`)
- Rule 34 — Architecture-Graph Truth (`CLAUDE.md`)
- ADR-0068 — Layered 4+1 + Architecture Graph as Twin Sources of Truth (`docs/adr/0068-layered-4plus1-and-architecture-graph.yaml`)
