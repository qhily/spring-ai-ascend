---
level: L1
view: scenarios
module: agent-service
status: active
authority: "ADR-0143 (rc55 — canonical 4+1 source moved here) + ADR-0138 (rc53 — 5-layer L1 ratification) + ADR-0139 (rc53 — Fast/Slow Path narrowed semantics) + ADR-0141 (rc55 — Internal Event Queue design_only) + ADR-0145 (rc55 — sealed RunEvent hierarchy)"
---

# agent-service — Scenarios View

> **Altitude discipline (L1).** Each scenario below is a **value-axis
> journey**: the actor, the layers traversed, the path discriminator,
> the contracts touched, and the failure modes named at the boundary
> level. Wire-level realisation — HTTP status codes, route verbs, SQL
> CAS clauses, method descriptors, per-variant RunEvent field sets, and
> test-class inventories — is **L2 / contract / verification** material.
> Each scenario points at the route contract (`openapi-v1.yaml`), the
> engine / S2C / RunEvent contracts, and the process-flow + L2 zones that
> own those details. The per-variant RunEvent emission shapes are the
> single source of truth in
> [`run-event.v1.yaml`](../../../../docs/contracts/run-event.v1.yaml);
> the per-scenario sequence detail lives in [`process.md`](process.md).

## 0. Scenario taxonomy

The five scenarios cover the full agent-service execution surface
end-to-end. Every shipped route, every documented suspension path, and
every RunEvent emission point traces to one or more of S1-S5 — this is
the L1↔code grounding requirement per Rule G-1.1.a applied at the
scenarios layer.

| ID | Title | Layer-traversal | Path discriminator | Conceptual route |
|---|---|---|---|---|
| S1 | Standard Synchronous Intake | 1→2→3→4→5a | Fast-Path eligible | run-creation route (Fast-Path branch) |
| S2 | Long-Horizon ReAct With Tool Calls | 1→2→3→4↔5a (loop) | Slow-Path required | run-creation route (Slow-Path branch) |
| S3 | A2A Peer Collaboration | 1→2→4→5a + outbound A2A | parent suspends; child Run on peer | run-creation route (parent) + peer ingress |
| S4 | S2C Client Callback | 5a→4→3→client→3→4→5a | server suspends, client resolves | run-resume route (W2-shipped) |
| S5 | Cancel During Execution | 1→2→4 | re-auth + single-writer transition | run-cancel route |

The concrete route verbs, paths, and status codes are owned by
[`openapi-v1.yaml`](../../../../docs/contracts). Layer numbering follows
ADR-0138 / 0140 / 0141 / 0144: 1 Access · 2 Session & Task Manager · 3
Internal Event Queue (design_only per ADR-0141) · 4 Task-Centric Control
· 5a Engine Dispatch & Execution · 5b Translation & Tool-Intercept.

---

## 0.1 Expanded scenario inventory (AS-SC01..AS-SC24) — anchored to S1-S5

> The 24 clusters below anchor to canonical S1-S5 and do NOT add new
> canonical authority. They are an enterprise-scenario decomposition for
> downstream design grounding — absorbed from PR #79 per the post-merge
> audit Wave 3 plan. Each row's **Canonical anchor** ties back to one of
> S1..S5; the **Covered clusters** column in [`features/*.md`](features/)
> references these AS-SC IDs. The closures are stated at business /
> journey altitude; concrete error shapes are contract material.

| Scenario cluster ID | Canonical anchor | Business scenario | Normal closure | Exception closure |
| --- | --- | --- | --- | --- |
| AS-SC01 | S1 | Short synchronous request | Client creates a Run; Access Layer binds tenant / idempotency / trace; Run completes quickly and returns a result. | Schema invalid, idempotency conflict, engine mismatch, cross-tenant collapse. |
| AS-SC02 | S1 / S2 | Non-streaming long-task polling | Access Layer returns a Task Cursor; client polls Run / Task status. | Client polling disconnect does not cancel the Run; query re-auth required; terminal status idempotently retrievable. |
| AS-SC03 | S1 / S2 | Streaming access | Client requests streaming state / step events; Access Layer owns only the stream boundary, while Run state stays in Session & Task Manager. | After stream disconnect, client recovers through cursor / offset / runId; disconnect must not cancel the Run. |
| AS-SC04 | S1 / S2 / physical | Direct-access boundary | Client may connect directly to the Access Layer; it must not connect directly to engine adapters, RunRepository, middleware, or bus channels. Mode B keeps the same service boundary. | Direct-to-engine, direct-to-queue, or missing tenant binding is rejected or unreachable. |
| AS-SC05 | S1 / S2 | Multi-protocol ingress convergence | HTTP, future gRPC, future A2A, and future MQ ingress converge into the same Run / Task / Session create + control semantics. | Protocol field differences must not create different state machines; unsupported protocol returns a boundary error. |
| AS-SC06 | S1-S5 | Ingress idempotency + duplicate submit | Same tenant + idempotency identity + request hash returns the same create result or an explainable conflict. | Body drift, duplicate submit, and late retry must not create duplicate Runs. |
| AS-SC07 | S1 / S2 | Context recovery after session disconnect | Client re-enters with sessionId / runId / taskId; Session & Task Manager restores Session projection + visible Run / Task state. | Missing session, tenant mismatch, projection lag, and stale cursor have deterministic responses. |
| AS-SC08 | S2 | Context compaction after overflow | Translation & Tool-Intercept produces a controlled context window from Session projection; Session & Task Manager preserves the boundary between original context state and compacted projection. | Compression loss, prompt overflow, memory mutation race, and cross-tenant memory read are blocked or explicitly failed. |
| AS-SC09 | S2 | Long-task continuation | Task Cursor returns first; Run continues under control / data / rhythm events + ticks; client can query or subscribe to progress. | Timeout, heartbeat loss, queue lag, and executor crash enter SuspendSignal / RunEvent / retry / dead-letter closure. |
| AS-SC10 | S2 | Mid-task execution-locus switch | Run upgrades Fast→Slow or moves across Mode A / Mode B, instance, or worker; checkpoint / parentNodeKey / RunEvent provide recovery anchors. | Locus changes, incompatible snapshots, and lost resume payloads cannot bypass Layer 2's single-writer transition. |
| AS-SC11 | S2 | Rollback to prior state + retry after failure | Run uses attempt / parentNodeKey / checkpoint reference / RunEvent history to express retry boundaries; retry is a controlled attempt of the same Run or an explicit child Run. | Non-idempotent tool side effect, terminal Run, missing checkpoint, and exhausted retry budget become deterministic failure or human-intervention states. |
| AS-SC12 | S2 / S5 | Cancel + completion race | When cancel / complete / fail / expire race, only Layer 2's single-writer transition decides the winner. | The loser re-reads post-transition state; same-terminal is idempotent success; different-terminal is an illegal transition. |
| AS-SC13 | S4 | Client-hosted skill invocation | When the engine needs local files, UI confirmation, browser capability, or private tools, it suspends via the S2C variant; the envelope asks the client to execute and then resume. | Client timeout, callback-id mismatch, invalid response schema, and resume re-auth failure prevent the engine from continuing privately. |
| AS-SC14 | S4 | Client skill authorization + capability declaration | Access Layer receives / publishes client capability; Task-Centric Control applies policy / quota / sandbox / audit before invocation; Translation & Tool-Intercept only shapes the tool call. | Claimed-but-unavailable client capability, over-permission, and unverifiable results become suspend failure or controlled retry. |
| AS-SC15 | S3 / S4 | Third-party Agent invocation | Task-Centric Control spawns a child Run or outbound invocation; Access Layer / IngressGateway handles peer / third-party protocol; Engine Dispatch & Execution executes only through adapters. | Peer unreachable, remote auth failure, remote error envelope, and child terminal failure preserve parentRunId / traceId / tenantId. |
| AS-SC16 | S3 / S4 | Same third-party Agent recovery after interruption | Third-party dispatch records the remote handle (remoteAgentId / remoteThreadId / callbackId), adapter profile, and parentRunId; next entry attempts to resume the original remote invocation first. | Missing remote handle, remote terminal state, adapter version drift, and lost remote state must explicitly become retry / failure / human handling; silently creating a new Agent is not allowed. |
| AS-SC17 | S3 | Agent delegates sub-agent | Parent Run creates a child Run; child inherits tenant / trace / policy envelope and returns results to the parent when terminal. | Child timeout, child cancel, child failed, and parent cancelled must decide cascade / detach / fail / resume. |
| AS-SC18 | S3 | Multi-Agent / peer aggregation | Multiple child or peer Runs return in parallel or sequence; Task-Centric Control performs join / aggregation / conflict classification. | Partial failure, late result, duplicate child completion, and invalid aggregation schema remain auditable. |
| AS-SC19 | S1-S4 | Model configuration ownership | Model provider / id / options / streaming / structured-output / cost-quota profile are governable service-local profiles; execution uses a resolved snapshot. | Request body overriding governance config, unsupported option, profile drift, and quota exceeded are controlled. |
| AS-SC20 | S3 / S4 | Third-party Agent adapter configuration ownership | Adapter / endpoint / auth mode / capability / resume-handle schema / timeout-retry policy live at the adapter / agent registry boundary. | Missing adapter, capability mismatch, resume-schema drift, and wrong credential scope block execution. |
| AS-SC21 | S1 / S4 | Client information + capability configuration ownership | Client identity / type / streaming support / callback transport / client-hosted skill list / permission posture are determined by Access Layer + Agent / Skill registry inputs. | Stale client capability, unavailable callback transport, and permission mismatch fail before invocation. |
| AS-SC22 | S2 / S4 | Tool / sandbox / skill configuration ownership | Tool schema / skill capacity / sandbox policy / tool allowlist / memory access policy take effect across RuntimeMiddleware + Translation & Tool-Intercept. | Tool escape, over-wide sandbox grant, capacity exhausted, and policy bypass produce audit + controlled failure. |
| AS-SC23 | S1-S5 | Observability + audit | Every ingress / state transition / suspend-resume / child Run / S2C callback / third-party invocation / terminal transition produces traceable evidence. | Anonymous event, missing tenant identity, lost terminal event, and over-cap payload are caught by gate or runtime contracts. |
| AS-SC24 | S1-S5 | Configuration snapshot + runtime drift | Run creation records necessary configuration snapshots / references; resume / retry uses the original snapshot unless explicit policy allows upgrade. | Hot config update changing behaviour mid-Run, adapter profile drift, and model option drift are detectable. |

---

## 1. S1 — Standard Synchronous Intake (Fast-Path eligible)

| Field | Value |
|---|---|
| **Actor** | Web / App client creating a Run via the run-creation route. |
| **Layers traversed** | Access → Session & Task Manager → Task-Centric Control → Engine Dispatch & Execution (5a). |
| **Run.mode** | `GRAPH` (deterministic short chain) OR `AGENT_LOOP` with a low estimated step count. |
| **Path discriminator** | DualTrackRouter predicate `(design_only — W2, ADR-0112)` — Fast-Path eligible iff: short estimated wall-clock, no external-input / S2C-callback / A2A-collaboration wait, and no expected resume on a different deployment locus. The threshold set is the §5.4 L2 Boundary Contract. |
| **Persistence shape** | Run + Task metadata persisted under RLS at create + at terminal; **no intermediate compute checkpoint** (Fast-Path narrowed semantics per ADR-0139 — metadata persistence remains mandatory). Idempotency dedup persisted at the Access Layer per ADR-0057. |
| **Contracts touched** | `openapi-v1.yaml`, `engine-envelope.v1.yaml`, `ingress-envelope.v1.yaml` `(design_only)`. |
| **RunEvent emissions** | Creation → state-transition(s) → terminal, per the S1 row of [`run-event.v1.yaml`](../../../../docs/contracts/run-event.v1.yaml) (`RunCreatedEvent`, `RunStateTransitionEvent`, `TerminalTransitionEvent`); per-variant field shapes are owned by that contract. |
| **Boundary contract** | If wall-clock exceeds the Fast-Path bound mid-execution, Layer 5a throws `SuspendSignal` and Layer 4 transitions the Run to SUSPENDED via Layer 2's `RunRepository` SPI — S1 has implicitly upgraded to S2. |
| **Failure modes** | (a) cross-tenant request — refused at the authorization boundary (Rule R-J.b; response-code posture per `openapi-v1.yaml` + ADR-0108). (b) idempotency collision — conflict vs body-drift per ADR-0057. (c) engine-envelope schema violation — `EngineMatchingException` → Run FAILED with `engine_mismatch` per Rule R-M.b. |
| **Grounding** | Process flow [`process.md`](process.md) §P1; route contract `openapi-v1.yaml`. Verification (the green tests asserting this shape) is owned by the verification layer + `architecture/facts/generated/tests.json`, not enumerated here. |

---

## 2. S2 — Long-Horizon ReAct With Tool Calls (Slow-Path)

| Field | Value |
|---|---|
| **Actor** | Web / App client requesting a multi-tool agent run. |
| **Layers traversed** | Access → Session & Task Manager → Internal Event Queue `(design_only — ADR-0141)` → Task-Centric Control ↔ Engine Dispatch & Execution (5a) (loop with `HookPoint.before_tool` / `after_tool` middleware events dispatched into Layer 4 per ADR-0140) ↔ Translation & Tool-Intercept (5b) `(design_only for most consumers)`. |
| **Run.mode** | `AGENT_LOOP`. |
| **Path discriminator** | DualTrackRouter `(design_only — W2, ADR-0112)` chooses Slow-Path when multi-step / tool calls / external-input-or-callback are expected. |
| **Persistence shape** | Run + Task records under RLS; **Checkpointer snapshots intermediate state** at each tool-call boundary (Checkpointer SPI per ADR-0021; in-memory ref impl shipped W0; durable backend W2). Resume is a SUSPENDED→RUNNING transition via Layer 2's `RunRepository` SPI (Layer 4 delegates; never writes Run state directly — ADR-0142). |
| **Contracts touched** | `engine-envelope.v1.yaml`, `engine-hooks.v1.yaml`, `model-invocation.v1.yaml` `(design_only)`, `memory-store.v1.yaml` `(design_only)`. |
| **RunEvent emissions** | Creation → state-transition → repeated suspend/resume around each tool-call boundary → terminal, per the S2 rows of [`run-event.v1.yaml`](../../../../docs/contracts/run-event.v1.yaml). |
| **Boundary contract** | Each tool call is bracketed by `HookPoint.before_tool` / `after_tool` events dispatched through the Layer 4 RuntimeMiddleware chain (Rule R-M.c — RuntimeMiddleware lives EXCLUSIVELY in Layer 4 per ADR-0140; Layer 5a does NOT invoke it directly). Skill-capacity arbitration per Rule R-K + `skill-capacity.yaml`. Neither path may violate Rule R-G (reactive I/O) / Rule R-H (no `Thread.sleep`) / Rule R-J.a (RLS). |
| **Failure modes** | (a) tool-execution timeout — Run stays RUNNING until the tool returns or the middleware throws SuspendSignal. (b) resume on a different deployment locus (Mode B→A) — state recovered from Checkpointer; tenant re-validated (Rule R-J.b; resume re-auth widening W2-deferred). (c) middleware short-circuit (Rule R-M.c) — Run continues without invoking the tool. |
| **Grounding** | Process flow [`process.md`](process.md) §P1 (Slow-Path branch); SuspendSignal flow [`logical.md`](logical.md) §5. |

---

## 3. S3 — A2A Peer Collaboration

| Field | Value |
|---|---|
| **Actor** | Agent A (this instance) delegates a sub-task to Agent B (peer); A's Run suspends until B returns. |
| **Layers traversed** | Access Layer (A2A Client outbound + A2A Server inbound on the peer side, `(design_only — W3+ when SDK lands)`) → Task-Centric Control suspends the parent Run via Layer 2 → Engine Dispatch & Execution (5a) dispatches the child Run to the peer via the ingress envelope over the three-track `control` channel per Rule R-E. |
| **Run.mode** | Parent: `GRAPH` or `AGENT_LOOP`; child Run on the peer: independent. |
| **Contracts touched** | `a2a-envelope.v1.yaml` `(design_only; no a2a-java SDK runtime dep per ADR-0100 §rejected-framing #1)`, `ingress-envelope.v1.yaml` `(design_only)`, `engine-envelope.v1.yaml` (runtime_enforced). |
| **RunEvent emissions** | Parent side: creation → running → child-run spawn → suspend → (peer executes) → child-run completion → resume → terminal, per the S3 rows of [`run-event.v1.yaml`](../../../../docs/contracts/run-event.v1.yaml). The peer emits its own independent RunEvent sequence (correlated via `parentRunId`). |
| **Boundary contract** | The parent suspends via the child-run `SuspendSignal` variant; the peer Run owns its own Run aggregate through its own `RunRepository` SPI. Correlation is by `parentRunId` + `traceId` (Run aggregate fields — see the Run fact in [`logical.md`](logical.md) §2). Both sides honour the Layer 2 single-owner contract (ADR-0142). |
| **Failure modes** | (a) peer unreachable — the child-run suspend reason times out; Run transitions FAILED with `peer_unreachable`. (b) peer returns an error envelope — the parent resumes and decides recovery (retry per ADR-0118 OR FAILED via Layer 2). (c) cross-tenant peer call — refused at the peer's A2A Server per Rule R-I.1 (W3+). |
| **Grounding** | Process flow [`process.md`](process.md) §P4; A2A SDK integration is W3+ scope. |

---

## 4. S4 — S2C Client Callback (server suspends, asks client for capability)

| Field | Value |
|---|---|
| **Actor** | A server-side Run needs a client-side capability (user confirmation, browser cookie, local-file access); it suspends via the S2C `SuspendSignal` variant and the client resolves via the run-resume route (W2-shipped). |
| **Layers traversed** | Engine Dispatch & Execution (5a — executor throws the S2C variant) → Task-Centric Control (catches; suspends the Run via Layer 2; persists a Checkpointer snapshot) → Internal Event Queue `(design_only — ADR-0141)` publishes the callback envelope on the `control` channel → client → `data` channel carries the response → Resume (Layer 4 transitions the Run RUNNING via Layer 2). |
| **Run.mode** | Inherits from the parent execution. |
| **Contracts touched** | `s2c-callback.v1.yaml` (runtime_enforced per Rule R-M.d), `engine-envelope.v1.yaml`, `engine-hooks.v1.yaml`. |
| **RunEvent emissions** | Creation → running → S2C callback requested (`OPT_IN` export — client-bound) + suspend → (client resolves) → S2C callback completed (`OPT_IN`) + resume → terminal, per the S4 rows of [`run-event.v1.yaml`](../../../../docs/contracts/run-event.v1.yaml). |
| **Boundary contract** | The RUNNING→SUSPENDED and resume transitions are delegated to Layer 2's `RunRepository` SPI (ADR-0142). On resume, the S2C `SuspendSignal` is unwound and the executor continues with the resolved response injected as the resume payload. Callbacks consume the `s2c.client.callback` skill capacity (Rule R-M.d + `skill-capacity.yaml`). The envelope + response field shapes + validation rule are the single source of truth in [`s2c-callback.v1.yaml`](../../../../docs/contracts/s2c-callback.v1.yaml). |
| **Failure modes** | (a) client times out — suspend reason expires; Run transitions FAILED. (b) skill capacity exhausted (Rule R-K) — caller suspended with the rate-limited reason `(W2-deferred scheduler admission per Rule R-K.c)`. (c) response envelope schema-invalid — validation against the S2C contract fails → Run FAILED with `s2c_response_invalid`. |
| **Grounding** | Process flow [`process.md`](process.md) §P5; S2C integration is W2-shipped. |

---

## 5. S5 — Cancel During Execution (cancel re-auth + cancel race)

| Field | Value |
|---|---|
| **Actor** | Client cancels a Run via the run-cancel route; the Run may be RUNNING, SUSPENDED, PENDING, or already terminal. |
| **Layers traversed** | Access Layer (cancel route) → Session & Task Manager (Layer 2 Run load + tenant guard) → state-machine validation invoked atomically inside Layer 2's transition per ADR-0142 (Layer 4 holds a typed reference but does NOT write directly). |
| **Persistence shape** | The cancel is a **single-writer transition** delegated to Layer 2's `RunRepository` SPI. The atomic primitive backing it (the CAS realisation) is the §5.3-delegated Postgres RLS Boundary Contract in [`development.md`](development.md). |
| **Authorization** | The cancel route re-validates that the requesting tenant matches the Run's tenant; cross-tenant access is refused at the authorization boundary (Rule R-J.b). The precise response-code posture per wave (and the WARN-audit MDC widening) is owned by `openapi-v1.yaml` + ADR-0108. |
| **Contracts touched** | `openapi-v1.yaml` (cancel route shape + error-envelope shape per Rule R-F + enforcer E8). |
| **RunEvent emissions** | Cancel requested → state-transition → terminal (winner path), per the S5 rows of [`run-event.v1.yaml`](../../../../docs/contracts/run-event.v1.yaml). |
| **Outcomes** | (a) active → CANCELLED: success. (b) same-status terminal: idempotent (no transition; a cancel-requested audit signal is still emitted). (c) different terminal: illegal transition; no state change; a rejection-audit signal is emitted. (d) concurrent cancel-vs-complete race: Layer 2 admits one writer; the loser re-reads the post-transition state and the route contract decides its response — see [`process.md`](process.md) §P6 for the loser flow. |
| **Failure modes structurally closed** | The cancel-vs-complete race (`F-nonatomic-run-status-write`, 5 prior recurrences) is **structurally closed** at the aggregate-ownership level: Layer 2 is the single writer (ADR-0142), and the `RunRepository` SPI is the only sanctioned transition path. Any new Run-state write path introduces a recurrence risk and is gated by that discipline. |
| **Grounding** | Process flows [`process.md`](process.md) §P3 (winner) + §P6 (loser); route contract `openapi-v1.yaml`. |

---

### S6 (cross-reference) — Weather Clarification (PR 92 v1.2 baseline)

A complete end-to-end HITL-plus-tool scenario walking through the M1-M6
module decomposition and demonstrating: MQ ingress → access-intent
normalisation → native ReAct first round → interrupt-registered control
event → SUSPENDED → callback → RESUMING → tool call → second LLM round →
COMPLETED. Source: the M1-M6 design draft review log; authority ADR-0155.

---

## 6. Cross-scenario invariants

The following invariants hold across ALL S1-S5 — the "red lines" the
rc55 audit identified (rc55 sibling sweep), restated as scenario-view
obligations:

1. **No tenant-identity-less data flow.** Every RunEvent variant
   declares tenant identity (Rule R-C.2.a); every persistence write is
   RLS-bound (Rule R-J.a); every cross-layer call propagates tenant
   identity from the persisted Run (NOT a platform-side ThreadLocal —
   Rule R-C.e).
2. **No Run state write outside the `RunRepository` single-writer
   transition.** Layer 4 holds a typed reference + delegates; Layer 2
   owns the transition; Layer 5a NEVER writes Run state. The create-only
   path is grandfathered per the ADR-0118 source-guard discipline.
3. **No single-tier internal queue with mode-based durability.** Layer
   3's binding (when its code home lands per ADR-0141) MUST route by
   intent into the three physical channels (`control` / `data` /
   `rhythm`) declared in `bus-channels.yaml`; each channel chooses its
   durability tier independently.
4. **Neither Fast-Path NOR Slow-Path may violate Rule R-G** (reactive
   I/O), **Rule R-H** (no `Thread.sleep`), or **Rule R-J.a** (RLS on
   tenant-scoped tables). The Fast-Path narrowed semantics per ADR-0139
   mean "no mandatory checkpoint/snapshot" — NOT "no mandatory
   persistence"; metadata persistence remains mandatory.

These four red lines are gate-closure criteria for every wave touching
the scenarios surface; any draft that violates one is rejected.

---

## 7. Cross-references

- Process View: each Sk scenario has a sibling Pk flow in
  [`process.md`](process.md) (S5 splits into P3 winner + P6 loser).
- Logical View: the 5-layer model + 5a/5b split + Run aggregate
  single-owner + the RunEvent hierarchy fact live in
  [`logical.md`](logical.md).
- Physical View: 5-plane deployment + persistence-plane tenancy posture
  + 3-track bus + sandbox boundary live in [`physical.md`](physical.md).
- Development View: package tree + Layer↔Package matrix + the 5 L2
  Boundary Contracts (the home for the realisation detail this view
  delegates) live in [`development.md`](development.md).
- SPI Appendix: active SPI interfaces with 4-way parity (Rule G-1.1.b)
  live in [`spi-appendix.md`](spi-appendix.md).
- Contracts: [`openapi-v1.yaml`](../../../../docs/contracts) (route
  shapes + status codes), [`run-event.v1.yaml`](../../../../docs/contracts/run-event.v1.yaml)
  (RunEvent variants + fields), [`s2c-callback.v1.yaml`](../../../../docs/contracts/s2c-callback.v1.yaml),
  [`engine-envelope.v1.yaml`](../../../../docs/contracts/engine-envelope.v1.yaml).
- Module-root grounding: [`ARCHITECTURE.md`](ARCHITECTURE.md) carries
  shipped-state implementation detail + dependencies + wave plan.
- Historical: the rc53 review file §14 is the original authoring of
  S1-S5; demoted to a historical authoring record per ADR-0143.
