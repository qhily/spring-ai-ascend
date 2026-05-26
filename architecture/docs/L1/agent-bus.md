# agent-bus — L1 Narrative

**Authority:** ADR-0147 (Structurizr Workspace Authority); narrative authored under W2 of the migration.

agent-bus owns cross-plane control surfaces in BOTH directions on the Bus & State Hub plane (Rule R-I):

- **C2S ingress** via `IngressGateway` (`com.huawei.ascend.bus.spi.ingress`) per ADR-0089 + Rule R-I.1.
- **S2C callback** via `S2cCallbackTransport` (`com.huawei.ascend.bus.spi.s2c`) per ADR-0088.
- **Three-track channel isolation** — `control`, `data`, `rhythm` (Rule R-E + `docs/governance/bus-channels.yaml`).

Workflow primitives (`Mailbox`, `AdmissionDecision`, `BackpressureSignal`, `SleepDeclaration`, `WakeupPulse`, `TickEngine`) are deferred to W2 per ADR-0050; the package `com.huawei.ascend.bus.spi` is the SPI roll-up parent.

## Function points (W2 seed; expand in subsequent ADRs)

- `FP-INGRESS-ENVELOPE` — `IngressEnvelope` routing.
- `FP-S2C-CALLBACK` — `S2cCallbackEnvelope` round-trip.

Cross-references: `architecture/features/function-points.dsl`, `agent-bus/module-metadata.yaml`, `agent-bus/ARCHITECTURE.md` (legacy).
