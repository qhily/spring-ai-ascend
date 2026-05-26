# agent-service — L1 Narrative

**Authority:** ADR-0147 (Structurizr Workspace Authority); narrative authored under W2 of the migration.

agent-service is the northbound facade and runtime orchestrator on the Compute & Control plane.

## Surfaces

- **HTTP Run API** — `POST /v1/runs` (create), `POST /v1/runs/{runId}/cancel` (cancel), `GET /v1/runs/{runId}` (status), `GET /v1/runs` (list). Function points: `FP-CREATE-RUN`, `FP-CANCEL-RUN`, `FP-GET-RUN-STATUS`, `FP-LIST-RUNS`.
- **Run aggregate** — `Run` entity + `RunStateMachine` DFA + `RunRepository.updateIfNotTerminal` CAS (Rule R-C.2.b; ADR-0118; ADR-0142).
- **SuspendSignal protocol** — `Run` suspends via `SuspendSignal.forX(...)` checked variants; `ResumeDispatcher` resumes (ADR-0137; sealed `SuspendReason` per ADR-0146). Function points: `FP-SUSPEND-RESUME`, `FP-CHILD-RUN-SPAWN`.
- **Tenant cross-check** — JWT `tenant` claim cross-checked vs `IngressEnvelope.tenantId` (Rule R-J; ADR-0056). Function point: `FP-TENANT-CROSS-CHECK`.
- **Durable idempotency** — `IdempotencyHeaderFilter` + `IdempotencyStore` (PG-backed; W0 SQL schema; ADR-0027). Function point: `FP-IDEMPOTENCY-CLAIM`.
- **Posture boot guard** — `PostureBootGuard` validates `@RequiredConfig` at startup; research/prod fail-closed (ADR-0058). Function point: `FP-POSTURE-BOOT-GUARD`.

## SPI packages (current; cross-checked against `agent-service/module-metadata.yaml#spi_packages`)

- `com.huawei.ascend.service.runtime.memory.spi`
- `com.huawei.ascend.service.runtime.resilience.spi`
- `com.huawei.ascend.service.runtime.runs.spi`
- `com.huawei.ascend.service.engine.spi`
- `com.huawei.ascend.service.session.spi`
- `com.huawei.ascend.service.task.spi`
- `com.huawei.ascend.service.agent.spi`

## Cross-references

`architecture/features/function-points.dsl`, `architecture/features/verification.dsl`, `agent-service/ARCHITECTURE.md` (legacy 4+1 view set), `docs/L1/agent-service/{logical,process,physical,development,scenarios}.md`.
