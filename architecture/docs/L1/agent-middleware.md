# agent-middleware — L1 Narrative

**Authority:** ADR-0147 (Structurizr Workspace Authority); narrative authored under W2 of the migration.

agent-middleware owns the `RuntimeMiddleware` SPI and `HookPoint` event dispatch (Rule R-M.c; `docs/contracts/engine-hooks.v1.yaml`; ADR-0073). Cross-cutting policies (model gateway, tool authz, memory governance, tenant policy, quota, observability, sandbox routing, checkpoint, failure handling) MUST be expressed as `RuntimeMiddleware` listening on canonical `HookPoint` events — not as patches to engine code.

Function point: `FP-HOOK-DISPATCH`.

## Cross-references

`agent-middleware/module-metadata.yaml`, `docs/contracts/engine-hooks.v1.yaml`, `agent-middleware/ARCHITECTURE.md` (legacy).
