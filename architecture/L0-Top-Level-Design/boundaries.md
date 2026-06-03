---
level: L0
view: development
status: draft
authority: "Consolidated from archived L0 module layout, generated modules DSL, and docs/architecture/l0 module/state drafts"
source_of_truth: true
---

# L0 Boundaries

## Purpose

This document defines L0 logical module admission, logical module responsibility
boundaries, downstream artifact treatment, and state ownership rules.

It intentionally combines module boundaries and state ownership because most
high-risk architecture conflicts are writer-boundary conflicts.

## Module Admission Rules

L0 admits only top-level logical modules. These modules correspond to future L1
architecture domains and must not be inferred from reactor artifacts, Java
packaging, starter mechanics, or individual runtime process boundaries.

L0 distinguishes three concerns:

| Concern | Meaning |
|---|---|
| L0 logical module | A top-level domain boundary that groups responsibilities by architecture meaning and becomes a future L1 domain. |
| Runtime unit | A service, adapter, gateway, registry, bus, sandbox, memory service, skill service, or other deployable/operational unit inside a logical module. |
| Development/deployment artifact | A BoM, starter, adapter scaffold, generated module fact, or packaging unit owned by the appropriate L1/L2 development or deployment view. |

Generated module facts remain authoritative for reactor identity and module
metadata. They do not decide L0 logical module admission.

## L0 Logical Module Classification

| Logical Module | L0 Boundary Treatment |
|---|---|
| `agent-client` | Client-side integration and local capability boundary. |
| `agent-service` | Server-side agent service boundary and Task lifecycle owner. |
| `agent-execution-engine` | Execution engine domain and finer-grained execution state owner below Task. |
| `agent-bus` | Access and interaction domain, including platform-centralized control and governance surfaces. |
| `agent-middleware` | Agent middleware foundation domain for selectable and integrable intelligent middleware services. |
| `agent-evolve` | Evolution-plane domain for governed learning, evaluation, optimization, and export loops. |

`spring-ai-ascend-dependencies`, `spring-ai-ascend-graphmemory-starter`, and
similar artifacts are not L0 logical modules. Dependency BoMs belong in the
development view of the owning domain or build governance. Starters belong in
the deployment/integration view of the domain they package or adapt.

## Responsibility Cards

### `agent-service`

Owns:

- HTTP-facing service boundary.
- Tenant, auth, idempotency, and trace entry behavior.
- Task execution lifecycle and Task hierarchy state.
- Service-side reference adapters.
- Query and external realtime stream surfaces such as SSE.
- Same-service parent/child execution relationship and join behavior.

Does not own:

- Bus physical channels.
- Model, skill, memory, vector, prompt, or advisor global SPI semantics.
- Customer business facts or customer data-source permission models.
- Cross-boundary A2A private connections that bypass bus governance.

### `agent-execution-engine`

Owns:

- Engine adapter SPI and engine registry/envelope surfaces where accepted.
- Execution dispatch, planner, and orchestration behavior assigned to the engine
  boundary.
- Finer-grained execution state below the Task boundary, such as workflow node
  execution state and ReAct loop state.
- Conversion of execution results into state-transition intent, tool intent,
  context request, suspend request, child-work intent, or terminal result.

Does not own:

- HTTP ingress.
- Direct writes to runtime lifecycle state outside the sanctioned owner path.
- Business tool/provider internals.
- Default remote-service boundary to `agent-service`.

### `agent-middleware`

Owns:

- The agent middleware foundation domain.
- Selectable and integrable services such as memory, knowledge, sandbox, skill,
  tool, model, retrieval, prompt, advisor, and hook services.
- Middleware SPI boundaries and the policy, capacity, audit, and trace evidence
  shapes around those services.

Does not own:

- Runtime lifecycle state.
- Customer business state.
- Direct provider telemetry as the only observability sink.
- Cross-boundary A2A control transport.

### `agent-bus`

Owns:

- The access and interaction domain.
- Runtime units such as registry, event bus, access gateway, permission center,
  S2C callback, A2A/federation, control channel, data-reference envelope, and
  rhythm channel surfaces when assigned by lower-level design.
- Platform-centralized control, permission, interaction, and governance surfaces
  for cross-boundary collaboration.

Does not own:

- Same-service multi-agent coordination.
- Runtime lifecycle state.
- Large payload transport.
- Token-by-token external stream.
- Microservice gateway business orchestration.

### `agent-client`

Owns:

- SDK packaging and developer-facing request/response convenience.
- Client-side cursor, callback, and service stream consumption.
- Local capability endpoint for local tools, context, memory, retrieval, and
  approval UI.

Does not own:

- Server-side lifecycle state.
- Platform audit or trace mutation.
- Direct dependency on service, engine, or middleware internals.

### `agent-evolve`

Owns:

- Evolution-plane boundary.
- Governed export contract and future Java adapter shell for ML pipelines.

Does not own:

- Main request execution path.
- Runtime lifecycle mutation.
- Business data extraction outside export governance.

## Capability Aggregates Are Not Modules

The following names may appear in scenarios, capability maps, and contracts, but
they are not accepted as independent reactor modules by L0:

- Gateway.
- Workflow.
- Context Engine.
- Tool Gateway.
- Runtime Governance.
- Observability.
- Capability Placement.
- A2A / Federation.

Each aggregate must map to one or more L0 logical modules plus concrete L1/L2
runtime units and contracts before implementation.

## Downstream Artifact Rule

L0 logical modules are not inferred from build or framework mechanics.

- A dependency BoM such as `spring-ai-ascend-dependencies` is a development-view
  or build-governance artifact for version alignment. It does not become a
  logical module.
- A Java starter such as `spring-ai-ascend-graphmemory-starter` is an
  integration/deployment packaging mechanism for auto-configuration or adapter
  bootstrap. It belongs under the logical module whose capability it packages.
- A runtime unit may be split, merged, or packaged differently across deployment
  variants without changing the L0 logical module boundary.

## State Ownership Rules

Every state must have:

- One semantic owner.
- A bounded writer path.
- Known readers.
- Forbidden writers.
- Replay and audit expectations when relevant.

Any new writer or second lifecycle owner is an L0 architecture change.

## Core State Matrix

| State | Owner | Allowed Writers | Forbidden Writers | Status |
|---|---|---|---|---|
| Task execution state | `agent-service` Task lifecycle owner | `agent-service` controlled lifecycle entry | Gateway, bus, engine adapter direct writes, middleware, client, provider adapters | accepted |
| Task hierarchy | `agent-service` relationship owner plus observability | Service parent/child creation or accepted federation result | Bus, engine adapter, remote service direct lifecycle mutation | accepted |
| Engine-internal execution state | `agent-execution-engine` | Engine execution loop, workflow node executor, ReAct loop executor, or sanctioned engine adapter path | Gateway, bus, middleware, client, provider adapters, direct service lifecycle writers | accepted |
| Client invocation reference | `agent-client` local handle plus `agent-service` query/reference surface | `agent-service` creates authoritative mapping; client stores local reference | Any writer treating it as independent server lifecycle state | accepted |
| Session state | `agent-service` session/context shell | Session owner and approved context projection paths | Memory owner, tool gateway, business agent direct platform mutation | candidate_promote |
| Memory / knowledge state | `agent-middleware` memory SPI and external memory provider boundary | Memory store writer or configured adapter | Runtime lifecycle state owner; hidden engine context builder | accepted_direction |
| Workflow checkpoint | Checkpointer SPI implementation under runtime governance | Orchestrator/checkpointer sanctioned path | Gateway, tool gateway, client | accepted_direction |
| Context package | Context capability across service and middleware | Context projector / retrieval / memory pipeline | Gateway, lifecycle state store | candidate_promote |
| Tool call record | Middleware governance plus service integration | Skill wrapper / runtime middleware / audit writer | Business agent direct external call bypassing governance | candidate_promote |
| Approval state | Service + bus callback governance | Approval callback handler / S2C transport path | Tool implementation direct write | candidate_promote |
| Trace / span / event | Telemetry vertical | TraceContext, hook chain, runtime event emitter | Provider adapter direct sink bypass | accepted_direction |
| Audit record | Platform audit writer | Append-only audit writer | Business code overwriting platform records | accepted_direction |
| Business state | External business system | Business system owner | Agent runtime platform | accepted_direction |
| Tenant / policy state | Runtime governance / identity and policy owner | Auth/policy owner | Skill implementation bypass | accepted_direction |

## Task Vocabulary Rule

For V1, Task is the unified server-side authoritative execution lifecycle state.
This Task aligns with the A2A protocol task semantics: it can be created or
bound by a client-to-server request, or by an `agent-service` request to another
`agent-service` through an A2A client.

`agent-service` owns Task-level lifecycle state, Task hierarchy, parent/child
relationships, joins, terminal states, and query surfaces. `agent-execution-engine`
owns finer-grained execution state below the Task boundary, including workflow
node execution state and ReAct loop state.

Historical Run-based terms such as Run, RunRepository, RunStatus, RunContext, or
run tree may appear in archived documents or implementation-history references.
They are not L0 canonical lifecycle vocabulary and must not introduce a second
server-side state owner.

## Boundary Conflict Escalation

Open an L0 decision item when a change:

- Adds a lifecycle-state writer.
- Moves a neutral SPI between bus, engine, service, or middleware.
- Treats a capability aggregate as a new module.
- Moves business facts into platform state.
- Makes bus carry large payloads or token streams.
- Changes whether same-service multi-agent coordination is service-owned or
  bus-owned.
