---
level: L2
view: development
feature: run-http-contract
status: active
relates_to:
  - "architecture/docs/L1/agent-service/ARCHITECTURE.md"
  - "architecture/docs/L1/agent-service/features/access-layer.md"
  - "architecture/docs/L2/telemetry-vertical/process.md"
  - "docs/contracts/openapi-v1.yaml"
authority: "ADR-0040 (W1 HTTP contract reconciliation) + ADR-0056 (JWT validation + tenant claim cross-check) + ADR-0057 (durable idempotency) + ADR-0061 (Telemetry Vertical) + ADR-0068 (Layered 4+1)"
---

# `run-http-contract` — Development View (servlet filter-chain composition)

> **Migrated filter-ordering home (active).** This file is the L2 detail sink for
> the **access-edge servlet filter registration order** — the numeric
> `FilterRegistrationBean` order values and chain position the layer-purity verdict
> flagged as L5 (filter-ordering) detail and that the L1 `agent-service`
> `ARCHITECTURE.md` (§2.A `platform/observability`, §2.A `platform/tenant`, §9.2
> risk) no longer enumerates. The structural fact that the Access Layer (Layer 1)
> binds tenant / auth / idempotency / trace stays at L1
> ([`../../L1/agent-service/features/access-layer.md`](../../L1/agent-service/features/access-layer.md)
> AS-L1-F05); the *order in which the filters fire* is the runtime-composition
> detail homed here. No filter class, order value, or header name below is minted
> here — each filter resolves to a `code-symbol/*` fact and the trace-filter wire
> behaviour is owned by the Telemetry Vertical sink (see Authority chain).

## Authority chain (read top-down)

This view is a **readable interpretation layer**. It invents no order value and no
class name; each is sourced in cascade order:

1. **Generated code facts (binding)** — each filter class is a
   `code-symbol/com-huawei-ascend-service-platform-*` fact in
   [`../../../facts/generated/code-symbols.json`](../../../facts/generated/code-symbols.json);
   the registration order is a property of the shipped `FilterRegistrationBean`
   wiring, not of this prose.
2. **Telemetry Vertical sink (trace-filter behaviour)** — the `TraceExtractFilter`
   parse/emit behaviour, the W3C `traceparent` / `traceresponse` grammar, and the
   MDC slice lifetime are owned by
   [`../telemetry-vertical/process.md`](../telemetry-vertical/process.md) §1–§2.
   This view cites the trace filter only for its *chain position*, never its wire
   shape.
3. **L1 module design (structural parent)** — the agent-service Access Layer
   (Layer 1) where the filters live:
   [`../../L1/agent-service/features/access-layer.md`](../../L1/agent-service/features/access-layer.md)
   (AS-L1-F05 tenant / auth / trace / idempotency binding).
4. **Contract surface** — the headers each filter validates are declared as
   `parameters` (`TenantIdHeader`, `IdempotencyKeyHeader`, `bearerAuth`) in
   [`../../../../docs/contracts/openapi-v1.yaml`](../../../../docs/contracts/openapi-v1.yaml).

## 1. Edge filter classes (each resolves to a code fact)

| Filter | Concern | Code-symbol fact id |
|---|---|---|
| `TraceExtractFilter` | W3C trace context originate / propagate (Telemetry Vertical) | `code-symbol/com-huawei-ascend-service-platform-observability-traceextractfilter` |
| `JwtTenantClaimCrossCheck` | JWT `tenant_id` claim vs `X-Tenant-Id` header cross-check | `code-symbol/com-huawei-ascend-service-platform-tenant-jwttenantclaimcrosscheck` |
| `TenantContextFilter` | `X-Tenant-Id` UUID binding + MDC `tenant_id` | `code-symbol/com-huawei-ascend-service-platform-tenant-tenantcontextfilter` |
| `IdempotencyHeaderFilter` | `Idempotency-Key` validation + claim/replay | `code-symbol/com-huawei-ascend-service-platform-idempotency-idempotencyheaderfilter` |

`TenantContextHolder` (request-scoped `ThreadLocal`) and the metric-side
`TenantTagMeterFilter`
(`code-symbol/com-huawei-ascend-service-platform-observability-tenanttagmeterfilter`)
are registered alongside but are not part of the inbound ordering chain
(the meter filter is a `MeterFilter`, not a servlet filter).

## 2. Registration order (the L5 detail migrated out of L1)

The filters are registered with explicit `FilterRegistrationBean` order so the
chain position is deterministic across Spring Security 6 / Boot 4. Lower order
fires earlier:

| Order | Filter | Why it sits here |
|---|---|---|
| 10 | `TraceExtractFilter` | First, so a `trace_id` exists before any other filter logs or rejects (the Telemetry Vertical originates trace context at the very edge). |
| 15 | `JwtTenantClaimCrossCheck` | After Spring Security's `BearerTokenAuthenticationFilter` (the JWT must already be parsed) and before tenant binding, so a claim/header mismatch is rejected before a `TenantContext` is built. |
| 20 | `TenantContextFilter` | Binds the validated `X-Tenant-Id` into the request scope + MDC after the cross-check has admitted it. |
| (after 20) | `IdempotencyHeaderFilter` | Last in the inbound chain (default Spring Security ordering after the tenant filters), so the idempotency claim is recorded under a bound tenant. |

`run_id` is NOT written by any filter in this chain: it is populated in the MDC by
the run-materialising controller *after* the chain completes (the run is created
inside the controller, not in a filter). The MDC slice and its lifetime are owned
by [`../telemetry-vertical/process.md`](../telemetry-vertical/process.md) §2; this
view records only that `run_id` is out of the filter-ordering scope.

## 3. Why the order is load-bearing (not incidental)

- **Trace before everything** — if `TraceExtractFilter` did not run first, an
  early rejection (bad JWT, tenant mismatch) would be logged without a
  `trace_id`, breaking correlation for exactly the requests most worth tracing.
- **Cross-check before bind** — building a `TenantContext` for a request whose
  JWT claim disagrees with its header would leak a half-bound tenant scope into
  downstream filters; the cross-check gates the bind.
- **Idempotency under a bound tenant** — the `(tenantId, key, bodyHash)` claim
  triple (see [`process.md`](process.md) §1) requires the tenant to be bound
  first, so the claim is tenant-scoped and the same `Idempotency-Key` used by two
  tenants never collides.

## 4. Cross-references

- Wire contract (status matrix, request / response shapes):
  [`logical.md`](logical.md).
- Idempotency body-lifetime + cancel-CAS realization sequences:
  [`process.md`](process.md).
- Trace-filter wire behaviour + MDC slice lifetime (the trace filter's *content*,
  not its chain position): [`../telemetry-vertical/process.md`](../telemetry-vertical/process.md) §1–§2.
- Structural parent (where the Access Layer filters live at L1):
  [`../../L1/agent-service/features/access-layer.md`](../../L1/agent-service/features/access-layer.md)
  (AS-L1-F05).
- Header authority: `parameters` in
  [`../../../../docs/contracts/openapi-v1.yaml`](../../../../docs/contracts/openapi-v1.yaml).
- Sink index: [`README.md`](README.md).
