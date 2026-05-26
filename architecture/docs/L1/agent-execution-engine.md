# agent-execution-engine — L1 Narrative

**Authority:** ADR-0147 (Structurizr Workspace Authority); narrative authored under W2 of the migration.

agent-execution-engine owns engine adapter selection and orchestration SPIs on the Compute & Control plane.

## Surfaces

- **EngineRegistry** — `EngineRegistry.resolve(envelope)` returns a typed `ExecutorAdapter` based on `EngineEnvelope.engineType` (Rule R-M.a; `docs/contracts/engine-envelope.v1.yaml`; ADR-0140).
- **Engine envelope contract** — `EngineEnvelope` is the canonical dispatch payload; pattern-matching on `ExecutorDefinition` subtypes outside the registry is forbidden.
- **Engine matching** — Mismatch raises `EngineMatchingException` → Run FAILED with reason `engine_mismatch` (Rule R-M.b).

Function point: `FP-ENGINE-DISPATCH`. Test: `EngineRegistryTest`.

## Cross-references

`agent-execution-engine/module-metadata.yaml`, `docs/contracts/engine-envelope.v1.yaml`, `agent-execution-engine/ARCHITECTURE.md` (legacy).
