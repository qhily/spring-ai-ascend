# agent-client — L1 Narrative

**Authority:** ADR-0147 (Structurizr Workspace Authority); narrative authored under W2 of the migration.

agent-client is the edge-plane client SDK surface. Per Rule R-I.1, modules whose `deployment_plane` is `edge` MUST NOT directly link to compute_control HTTP routes — they go through `IngressGateway` on agent-bus.

W1 ships skeleton only; the SDK proper lands in W3+ per ADR-0049.

## Cross-references

`agent-client/module-metadata.yaml`, `agent-client/ARCHITECTURE.md` (legacy).
