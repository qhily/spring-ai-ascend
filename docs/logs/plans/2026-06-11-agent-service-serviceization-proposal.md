---
date: 2026-06-11
status: approved
approved_by: owner (2026-06-11)
owner_answers:
  - "Q1 (Spring edge): agent-service runs in a Java/Spring environment, but by design principle the module stays INDEPENDENT of Spring — the SPI core is Spring-free; the Spring edge lives outside the module (separate starter)."
  - "Q2 (validator): take the recommended option — extract JwtTenantValidator from runtime.boot for reuse; the small agent-runtime refactor is accepted."
  - "Q3 (ingress freeze scope): immediately — the superseding ADR takes bus.spi.ingress out of the agent-bus freeze scope now, no waiting for the W2 gate."
scope: agent-service registration/discovery serviceization SPI
relates_to:
  - docs/logs/plans/2026-06-11-agent-bus-spi-decision.md
  - ADR-0159 (agent-runtime consolidation / agent-service re-founding)
  - ADR-0089 (edge-plane ingress gateway mandate — bus.spi.ingress, never materialized)
  - ADR-0101 (polymorphic deployment topology, mode_a / mode_b)
  - ADR-0040 (W1 tenant cross-check, as implemented in agent-runtime boot)
---

# Proposal — agent-service Serviceization: Promote the Proven Gateway Shapes into the Registration/Discovery SPI

## 0. Problem statement and root cause (Rule D-1)

`agent-service` is today an intentional skeleton: one placeholder
`package-info.java` reserving the namespace
(`agent-service/src/main/java/com/huawei/ascend/service/spi/package-info.java:4-6`:
*"Gateway facade examples live under examples/agent-runtime-a2a-llm-e2e until a
dedicated service module decision is made"*). The L0 architecture-of-record
says the module "will drive agent-runtime-hosted Agent instances via
registration/discovery (deferred; single placeholder SPI today)"
(`architecture/docs/L0/ARCHITECTURE.md:161`).

Meanwhile the **deferred design has already been built and proven — in the
wrong module**. `examples/agent-runtime-a2a-llm-e2e/src/main/java/com/huawei/ascend/examples/a2a/gateway/`
contains a complete, tested gateway facade (~1,930 lines across 38 files):

| Concern | Example artifact | Evidence it works |
|---|---|---|
| Runtime registration + lease | `api/RuntimeRegistrationApi`, `core/InMemoryRuntimeRegistry` | `InMemoryRuntimeRegistryTest`, `RuntimeRegistryHttpE2eTest` |
| Agent discovery + card aggregation | `api/AgentDiscoveryApi` (`getAgentCard` / `listAgents` / `resolveRoute`) | same tests; multi-agent, multi-turn routing asserted |
| Route grants (signed routing capability) | `api/RouteGrantService`, `core/HmacRouteGrantService`, `core/InMemoryRouteGrantCache` | `HmacRouteGrantServiceTest`, `InMemoryRouteGrantCacheTest` |
| A2A byte-level pass-through forwarding | `core/RuntimeA2aGateway` (blocking + streaming) | `RuntimeRegistryPingPongE2eTest` |
| Interaction telemetry | `api/AgentInteractionTelemetry`, `core/InMemoryAgentInteractionTelemetry` | recorded per forward in `http/A2aGatewayController` |
| HTTP edge | 5 controllers + `config/RuntimeRegistryConfiguration` | `RuntimeRegistryHttpE2eTest` (random-port Spring Boot) |

**Root cause of the current state:** ADR-0159 deliberately deferred the
serviceization SPI to avoid stubbing it early ("design-phase repo — not stubbed
early", `architecture/docs/L0/ARCHITECTURE.md:136-137`), and the example was
used as the design laboratory. The lab experiment succeeded; the deferral
condition ("until a dedicated service module decision is made") is now
satisfiable. The risk of NOT deciding is the agent-bus failure mode in reverse:
agent-bus shipped SPIs with zero implementations (see
`docs/logs/plans/2026-06-11-agent-bus-spi-decision.md`); agent-service is
shipping implementations with zero SPI — proven code that cannot be reused
because it lives under `examples/` with `examples`-rooted package names.

**Strongest reading of the requirement:** not "design a registry SPI from
scratch" but **promote the example's proven shapes into
`com.huawei.ascend.service.spi`, with implementations and tests moving
together** — interface + reference impl + test land in the same wave, so
agent-service never enters the zero-impl-SPI trap.

## 1. Recommended path (one sentence)

Promote the three proven seams — **RuntimeRegistry**, **AgentDirectory**,
**RouteGrantService** — plus their value types and in-memory reference
implementations from the example into `agent-service`, keep the Spring HTTP
edge example-side for one stage, reuse the W1 JWT cross-check model at the
service ingress, preserve the byte-level A2A no-rewrite invariant, and record
that this **supersedes** the never-materialized `bus.spi.ingress.IngressGateway`
for client→server traffic.

---

## 2. (a) The minimal SPI set

Three interfaces, one package family under the already-reserved namespace
(`agent-service/module-metadata.yaml:15-16` declares
`com.huawei.ascend.service.spi` as the SPI package). Signatures are lifted
from the example's proven shapes, with two deliberate renames (`*Api` →
service-vocabulary names) and one narrowing (telemetry stays out, §3).

Dependency feasibility: `agent-service` already depends on `agent-runtime`
(`agent-service/pom.xml:29-32`), and `agent-runtime` depends on
`org.a2aproject.sdk` (`agent-runtime/pom.xml:160`) and exposes `AgentCard` in
its own public SPI (`agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/spi/AgentCardProvider.java:3,16`)
— so the promoted types may reference `org.a2aproject.sdk.spec.AgentCard`
without any new dependency edge. Dependency direction stays
`agent-service → agent-runtime` per §4 #1
(`architecture/docs/L0/ARCHITECTURE.md:296-301`).

### 2.1 `com.huawei.ascend.service.spi.registry` — runtime registration

```java
public interface RuntimeRegistry {                    // from api/RuntimeRegistrationApi
    RuntimeRegistrationResult register(RuntimeAgentRegistration registration);
    RuntimeLeaseResult renew(RuntimeLeaseRenewal renewal);
    RuntimeDeregisterResult deregister(RuntimeInstanceId runtimeInstanceId);
}
```

Promoted value types (verbatim record shapes from `gateway/model/`):

```java
public record RuntimeAgentRegistration(
        RuntimeInstanceId runtimeInstanceId, String tenantId, String agentId,
        AgentCard agentCard, URI a2aEndpoint, URI healthEndpoint, String version,
        Duration ttl, RuntimeCapacitySnapshot capacitySnapshot,
        Map<String, Object> metadata) { ... }

public record RuntimeLeaseRenewal(
        RuntimeInstanceId runtimeInstanceId, RuntimeState state, Duration ttl,
        SlaSnapshot slaSnapshot, RuntimeCapacitySnapshot capacitySnapshot,
        Map<String, Object> metadata) { ... }

public enum RuntimeState { COLD, REGISTERING, READY, AT_CAPACITY, DRAINING, UNREACHABLE, DEREGISTERED }
```

The lease/TTL state model (register → READY; expired lease → UNREACHABLE;
renewal carries state + capacity; `AT_CAPACITY` derived, not stored) is exactly
`InMemoryRuntimeRegistry.effectiveState/effectiveRouteState`
(`.../gateway/core/InMemoryRuntimeRegistry.java:187-211`) — it survived e2e
testing and should be promoted unchanged.

### 2.2 `com.huawei.ascend.service.spi.discovery` — agent discovery / card aggregation

```java
public interface AgentDirectory {                     // from api/AgentDiscoveryApi
    AgentCard getAgentCard(String agentId, String tenantId);
    List<AgentCardSummary> listAgents(String tenantId);
    RuntimeRoute resolveRoute(String agentId, String tenantId, RoutingContext routingContext);
}
```

With `RuntimeRoute(agentId, runtimeInstanceId, a2aEndpoint, state,
lastHeartbeatAt, slaSnapshot, capacitySnapshot)` and
`RoutingContext(sessionId, correlationId, metadata)` promoted as-is. Every
method is tenant-scoped by signature — there is no tenant-free overload; this
is a deliberate invariant carried over from the example
(`InMemoryRuntimeRegistry.eligibleRoutes` filters on `tenantId` before
`agentId`, `.../InMemoryRuntimeRegistry.java:147-156`).

The capacity-aware route selection (`routeScore()` = max of task-load,
LLM-load, estimatedLoad; tie-break by p95 first-token then freshest heartbeat;
`RuntimeCapacitySnapshot.routeScore`, `.../model/RuntimeCapacitySnapshot.java:46-50`)
promotes with the reference impl, not into the SPI — it is policy, and an
implementation detail behind `resolveRoute`.

### 2.3 `com.huawei.ascend.service.spi.routing` — route grants

```java
public interface RouteGrantService {                  // same name as example api/
    RouteGrant resolveGrant(RouteGrantRequest request);
    GrantValidationResult validate(RouteGrant grant, InboundA2aContext context);
}
```

With `RouteGrant(grantId, tenantId, sourceAgentId, targetAgentId,
targetRuntimeId, a2aEndpoint, allowedMethods, policyVersion, issuedAt,
expiresAt, signature)` promoted verbatim. The HMAC-SHA256 canonical-string
signing + constant-time validation in `HmacRouteGrantService`
(`.../gateway/core/HmacRouteGrantService.java:104-127`) promotes as the
reference implementation. The validation order (expiry → tenant → source →
target → method → signature, `:79-102`) is behavior worth pinning in tests.

### 2.4 Reference implementations promote WITH the SPI

`InMemoryRuntimeRegistry` (implements both `RuntimeRegistry` and
`AgentDirectory`), `HmacRouteGrantService`, and `InMemoryRouteGrantCache` move
into `com.huawei.ascend.service.core` in the same commit as the interfaces,
together with their unit tests. This is the explicit anti-pattern guard
learned from the agent-bus review (21 SPI files, zero implementations,
contract defects discovered only on review — see
`2026-06-11-agent-bus-spi-decision.md` "Option A … cons"). **No interface
lands without a green implementation.**

## 3. (b) What stays example-only

| Stays in `examples/.../gateway/` | Why |
|---|---|
| All 5 `http/` controllers + `config/RuntimeRegistryConfiguration` (Stage 1) | agent-service has no Spring web dependency today (`agent-service/pom.xml:27-44` — only agent-runtime + test deps); adding spring-boot-starter-web is a Stage-2 decision, not a prerequisite for the SPI |
| `AgentInteractionTelemetry` + `InMemoryAgentInteractionTelemetry` + `TelemetryController` | Telemetry is a cross-cutting concern; agent-runtime already standardizes on Micrometer/Prometheus (`agent-runtime/pom.xml:129`). Promoting a bespoke event-record telemetry SPI would create a second observability truth. The example keeps it as demo instrumentation; the production answer is Micrometer meters + structured logs in the promoted impls |
| `RuntimeA2aGateway` HTTP forwarder — promote the **class** (it is pure `java.net.http`, zero Spring) but mark it `service.core` internal, not SPI | Forwarding is mechanism; the SPI surface is registry/directory/grants. Consumers compose forwarding behind their own edge |
| `GatewayHealthSnapshot` / `GatewayHealthController` | Health aggregation shape is deployment-specific; revisit with the Stage-2 Spring edge |
| The `X-Agent-Examples-*` header names | Promoted code renames to `X-Ascend-*` (`Runtime-Instance`, `Route-Grant-Id`, `Route-Grant-Signature`, `Source-Agent`, `Tenant`); the example keeps its names until it rewires onto the promoted types |

The example module then **deletes its copies** and imports
`com.huawei.ascend.service.{spi,core}` — it already depends on the reactor
modules (`examples/agent-runtime-a2a-llm-e2e/pom.xml:36`), so this only adds
the `agent-service` dependency. The example shrinks to: Spring wiring +
controllers + the e2e scenario drivers.

## 4. (c) Multi-tenant and auth posture — reuse the W1 JWT cross-check model

Two trust legs, each already prototyped:

**Leg 1 — client → agent-service (ingress).** Reuse the W1 cross-check model
shipped in agent-runtime: `Authorization: Bearer <HS256 JWT>` whose
`tenant_id` claim must verify and, when an `X-Tenant-Id` header is present,
must match it; validated tenant published as a request attribute that the
controller prefers over raw parameters
(`agent-runtime/src/main/java/com/huawei/ascend/runtime/boot/A2aTenantAuthFilter.java:13-27,58-69`;
`JwtTenantValidator` rejects non-HS256 `alg` confusion explicitly,
`.../boot/JwtTenantValidator.java:53-56`). Two reuse options:

- **Recommended:** extract `JwtTenantValidator` (currently package-private in
  `runtime.boot`) into a small public support class in agent-runtime
  (e.g. `runtime.access` support), consumed by both the runtime filter and the
  future agent-service edge. One validator, one set of security tests.
- Fallback (if the owner wants zero agent-runtime churn): agent-service ships
  its own copy of the ~90-line validator at Stage 2. Accept the duplication
  consciously; it is bounded and the cross-check semantics are pinned by tests
  on both sides.

The same `agent-runtime.access.a2a.jwt.{enabled,hmac-secret,clock-skew-seconds}`
property shape (`.../boot/RuntimeAccessProperties.java:29-45`) is mirrored as
`agent-service.access.jwt.*`. Disabled ⇒ W0 header-attribution behavior, same
opt-in posture as the runtime.

**Leg 2 — agent-service → agent-runtime (egress).** Two mechanisms compose:

1. **Route grants** (service-internal capability tokens): minted per forward by
   `RouteGrantService`, attached as `X-Ascend-Route-Grant-*` headers exactly as
   the example controller does (`.../http/A2aGatewayController.java:77-80`).
   These prove *the gateway authorized this hop*; runtimes MAY validate them
   when they share the grant secret (mode_b co-deployment).
2. **The client's JWT passes through.** `RuntimeA2aGateway.copyHeaders` forwards
   all non-hop-by-hop headers (`.../core/RuntimeA2aGateway.java:24-35,136-147`)
   — `Authorization` is forwardable, so a JWT-enabled runtime
   (`A2aTenantAuthFilter` active on `/a2a`) re-validates the same token
   end-to-end. **The tenant check therefore holds at both hops with one
   credential**, which is exactly the cross-check model's intent. The proposal
   pins this with an e2e test (§7) rather than leaving it emergent.

Tenant isolation inside the service is structural: every SPI method takes
`tenantId`, registry candidate filtering is tenant-first, and grants embed and
re-validate `tenantId` (`HmacRouteGrantService.validate:85-87`). Per ADR-0101
both deployment modes are declared for agent-service
(`agent-service/module-metadata.yaml:22-24`): in `platform_centric` (mode_a)
the registry is multi-tenant-shared; in `business_centric` (mode_b) it
co-deploys with the runtimes on the business side and the tenant set is
typically one — same code, no mode switch in the SPI.

## 5. (d) Fronting N agent-runtime instances — the pass-through / no-rewrite answer

The example settles this question and the proposal ratifies its answer as an
invariant:

> **A2A-NO-REWRITE:** agent-service forwards A2A payloads as opaque bytes. It
> never parses, validates, or rewrites the JSON-RPC body. Routing keys
> (tenantId, agentId, sessionId, correlationId) come exclusively from URL,
> query, and headers.

Evidence this works: `RuntimeA2aGateway.forward/forwardStreaming` POST raw
`byte[]` to the resolved runtime's `a2aEndpoint`, strip only hop-by-hop
headers, add the `X-…-Runtime-Instance` header, and stream the response body
back unparsed (`.../core/RuntimeA2aGateway.java:51-119`). The runtime side
terminates the protocol at `A2aJsonRpcController` on `/a2a`
(`agent-runtime/src/main/java/com/huawei/ascend/runtime/boot/A2aJsonRpcController.java:63,94`
— both JSON and `text/event-stream` mappings), so streaming SSE passes through
the gateway unbuffered (`A2aGatewayController.streamAndRecord` copies 8 KB
chunks as they arrive, `.../http/A2aGatewayController.java:147-153`).

Consequences:

- **Scale-out:** N runtimes register `(tenantId, agentId, instanceId, endpoint,
  ttl, capacity)`; `resolveRoute` picks by READY-state + `routeScore`. Adding a
  runtime is a registration, not a config change.
- **Session affinity is NOT yet guaranteed.** Today multi-turn stickiness in
  `RuntimeRegistryHttpE2eTest` emerges from deterministic ordering, not from a
  session→instance pin. `RoutingContext.sessionId` is already in the SPI
  signature precisely so a sticky implementation can land later **without an
  SPI change**. Declared a Stage-3 implementation concern, not an SPI concern.
- **The one permitted rewrite — agent cards.** A card fetched via
  `getAgentCard` may advertise the runtime's direct endpoint; serving it
  verbatim leaks the back-end topology and invites clients to bypass the
  gateway. Stage 1 keeps pass-through (current behavior, honest about scope);
  Stage 3 adds card endpoint masking at the `AgentDirectory` implementation
  (rewrite the card's URL field to the gateway-fronted route). This is a
  *discovery-metadata* rewrite, not an A2A-payload rewrite, so the invariant
  stands.

## 6. (e) Relationship to the frozen `bus.spi.ingress` / IngressGateway idea

Facts first: ADR-0089 mandated `bus.spi.ingress.IngressGateway` as the C2S
control surface, but **the package was never created** — `agent-bus` contains
only `engine` and `s2c` under `bus/spi/`
(verified: `agent-bus/src/main/java/com/huawei/ascend/bus/spi/` holds
`engine/`, `s2c/`, `package-info.java`). The L0 document still claims it
ships/is active in four places (`architecture/docs/L0/ARCHITECTURE.md:77, 149,
162, 184`) — the same false-claim family the agent-bus review flagged for
`s2c` (`2026-06-11-agent-bus-spi-decision.md`, trigger paragraph).

**Position: serviceization SUPERSEDES IngressGateway for client→server
traffic; it does not revive it.** Rationale:

1. ADR-0089's own alternatives analysis already concluded client-to-server
   ingress "is not internal — it is the platform boundary"
   (`docs/adr/0089-edge-plane-ingress-gateway-mandate.yaml:114-115`). ADR-0159
   then created a module whose entire charter is that platform boundary:
   the serviceization facade. The facade's `AgentDirectory.resolveRoute` +
   forwarder IS the ingress; a second neutral `IngressGateway` SPI in the bus
   plane would be a parallel abstraction with no consumer — the exact trap the
   bus decision document describes.
2. The bus decision's recommended Option C (freeze + ArchUnit fence on
   `bus.spi..`) is *strengthened*, not contradicted: this proposal adds the
   same fence to agent-service (§7) and gives the W2 planning gate a concrete
   disposition for the ingress third of the frozen surface — **retire
   `bus.spi.ingress` from the L0 text** (it cannot be frozen; it never
   existed). `bus.spi.engine` / `bus.spi.s2c` remain governed by that
   document's own A/B/C decision, untouched here.
3. Required companion edit: a superseding ADR (Stage 0) amending ADR-0089 —
   C2S ingress authority moves from the bus plane to the serviceization
   facade; L0 lines 77/149/162/184 corrected from "active/ships" to
   "superseded by agent-service serviceization (ADR-XXXX)". S2C callback
   placement in agent-bus is unaffected.

## 7. (f) Test strategy (Rule D-4 — three layers, honest assertions)

**Layer 1 — unit (agent-service, surefire).** Direct promotions of the
existing example tests, re-rooted: `InMemoryRuntimeRegistryTest` (lease expiry
→ UNREACHABLE, AT_CAPACITY derivation, tenant filtering, deterministic
ordering, error-code-per-state from `errorCodeForState`),
`HmacRouteGrantServiceTest` (sign/validate round-trip, each rejection branch
in validation order, signature tamper), `InMemoryRouteGrantCacheTest`. Add:
clock-skew edge tests with injected `Clock` (both impls already take `Clock` —
`InMemoryRuntimeRegistry.java:38`, `HmacRouteGrantService.java:35`).

**Layer 2 — module integration (agent-service ITs, failsafe — the module
already configures failsafe, `agent-service/pom.xml:48-51`).**
ArchUnit (the module already has archunit-junit5, `agent-service/pom.xml:39-43`):
(a) `com.huawei.ascend.service..` MUST NOT import `com.huawei.ascend.bus.spi..`
(aligning with the freeze fence proposed for agent-runtime in the bus decision
doc); (b) MUST NOT import `com.huawei.ascend.runtime..` other than
`runtime.engine.spi..` types it legitimately references; (c) `service.spi..`
MUST NOT depend on Spring (SPI purity, Rule R-D family). Plus a
registry-contract test class written against the `RuntimeRegistry` +
`AgentDirectory` *interfaces* (parameterized over implementations), so a
future Postgres registry inherits the contract suite for free.

**Layer 3 — cross-module e2e (stays in the example module).**
`RuntimeRegistryHttpE2eTest` / `RuntimeRegistryPingPongE2eTest` keep running,
now against promoted types — they become the proof that promotion changed
nothing behaviorally. Add one new scenario: **JWT-enabled end-to-end** — mint
an HS256 token, call the gateway, assert (i) gateway cross-check rejects a
mismatched `X-Tenant-Id` with 403, (ii) the same `Authorization` header
arrives at a `LocalA2aRuntimeHost` with `jwt.enabled=true` and passes
`A2aTenantAuthFilter` — pinning the §4 Leg-2 pass-through claim with evidence
instead of inference.

Per Rule D-3.a, the promotion commits touch dependency wiring (example pom
gains agent-service) ⇒ smoke + lint required before commit.

## 8. (g) Staged landing plan

| Stage | Content | Exit criterion |
|---|---|---|
| **0 — ratify (design-mode, ~1 day)** | ADR: serviceization SPI un-deferred; supersedes ADR-0089 C2S ingress; A2A-NO-REWRITE invariant; telemetry exclusion. L0 §2 row + lines 77/149/162/184 corrected. `agent-service/module-metadata.yaml` description + `spi_packages` updated (registry/discovery/routing). Fresh L1 note for agent-service (see §10 — do not resurrect the retired 4+1 tree) | ADR accepted; gate green on metadata parity |
| **1 — promote core (impl-mode, ~2–3 days)** | `service.spi.{registry,discovery,routing}` interfaces + value records; `service.core` reference impls (`InMemoryRuntimeRegistry`, `HmacRouteGrantService`, `InMemoryRouteGrantCache`, `RuntimeA2aGateway`); Layer-1 tests + Layer-2 ArchUnit/contract tests. No Spring added to agent-service. Example rewires to the promoted types and deletes its copies; e2e suite green unchanged | `mvnw verify` green for agent-service + example; example contains no duplicate of a promoted type |
| **2 — Spring edge + auth (impl-mode, ~2–3 days)** | Decision point: add `spring-boot-starter-web` to agent-service and promote controllers + `RuntimeRegistryConfiguration` as an autoconfiguration, OR keep the edge example-side longer. Either way: JWT ingress filter reusing the extracted `JwtTenantValidator`; `agent-service.access.jwt.*` properties; the new JWT e2e scenario | JWT e2e green; cross-check rejections asserted at both hops |
| **3 — production posture (W2+, sized at the W2 gate)** | Pluggable persistent registry (Postgres, RLS-ready), session affinity in `resolveRoute`, agent-card endpoint masking, grant-secret rotation/Vault sourcing, capacity-snapshot push from runtime heartbeats | per W2 plan |

Stages 0–2 are independent PRs; Stage 1 is valuable alone (reusable library)
even if Stage 2's Spring question is answered "not yet".

## 9. Alternatives considered (briefly)

- **A. Status quo — keep everything example-only until W2.** Lowest immediate
  cost, but the proven design keeps rotting under `examples/` package names,
  every consumer must copy-paste, and the "dedicated service module decision"
  comment ages into a false claim. Rejected: the deferral's stated condition
  is met.
- **B. Home the SPI in agent-bus instead (`bus.spi.ingress` revival).**
  Superficially honors ADR-0089. Rejected: repeats the zero-impl trap the bus
  review just documented; contradicts ADR-0159's ownership split (serviceization
  is agent-service's charter, `architecture/docs/L0/ARCHITECTURE.md:161`); and
  ADR-0089's own analysis places client ingress at the platform boundary, not
  the internal bus plane.
- **C. Adopt an off-the-shelf gateway/registry (Spring Cloud Gateway + Eureka,
  or Envoy + xDS).** Rejected for W1–W2: heavyweight for a library-shaped
  product, weak fit for the tenant-scoped agent-card/route-grant model, and the
  financial-vertical on-prem (mode_b) deployments favor a self-contained
  facade. The SPI proposed here does not preclude an Envoy-fronted
  implementation later — `AgentDirectory`/`RouteGrantService` are exactly the
  control-plane hooks such an integration would need.

## 10. The retired five-layer L1 design — salvage vs drop

`architecture/docs/L1/agent-service/README.md` is explicit that its 4+1 tree
is the **former runtime's** design, retained only "pending physical migration
to `architecture/docs/L1/agent-runtime/`" (`README.md:21`), and that the
engine/hook vocabulary is retired with no surviving Java types (`README.md:14`).

**Drop (belongs to agent-runtime's L1 migration, not to serviceization):**
the 5-layer decomposition, `EngineRegistry`/`ExecutorAdapter`/hook surfaces,
internal event queue (design_only per ADR-0141), Run-aggregate ownership
(ADR-0142 — now physically agent-runtime's `runtime.run` kernel), the
taskflow queue/control notes (`README.md:51-53` — PR #102 runtime work).

**Salvage (genuinely about the facade):** the scenarios inventory
(`scenarios.md` AS-SC01..AS-SC24, `README.md:44`) as acceptance-scenario seeds
for registration/discovery/routing; the deployment-loci analysis (mode_a/mode_b
per ADR-0101, already encoded at `agent-service/module-metadata.yaml:22-24`);
the tenant-isolation red-lines from `logical.md` §9. The Stage-0 L1 note for
agent-service is written **fresh** around the three SPI surfaces, citing the
salvaged scenarios — it must not be authored by editing the retired tree
(that tree's destiny is migration to agent-runtime, per its own banner).

## 11. Open questions for the owner

1. Stage 2's Spring edge: autoconfiguration inside agent-service, or a separate
   starter module? (Affects whether agent-service stays Spring-free.)
2. `JwtTenantValidator` extraction from `runtime.boot` (recommended) vs bounded
   duplication — does the owner accept the small agent-runtime refactor?
3. Should the superseding ADR also take the `bus.spi.ingress` line-item out of
   the agent-bus freeze scope immediately, or wait for the W2 gate decision on
   the whole bus surface?
