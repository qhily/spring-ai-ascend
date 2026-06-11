---
level: L0
view: development
affects_level: L0
affects_view: [development, logical]
status: applied-in-same-commit
authority: "ADR-0161 (agent-service serviceization Stage 0; supersedes ADR-0089)"
---

# L0 correction — `bus.spi.ingress` supersession (frozen-doc edit proposal)

The root L0 (`architecture/docs/L0/ARCHITECTURE.md`, freeze_id
`W1-russell-2026-05-14`) carries several statements that `bus.spi.ingress` /
`IngressGateway` is a shipped or active agent-bus surface. The package has
never existed in the repository — no `com.huawei.ascend.bus.spi.ingress`
directory, no `IngressGateway.java` — making these statements
documented-but-absent claims of the same family the 2026-06-10 core-module
review flagged for the bus engine SPI (finding H6).

ADR-0161 (owner-approved 2026-06-11) supersedes ADR-0089: client-to-platform
fronting is owned by the agent-service serviceization SPI, and
`bus.spi.ingress` leaves the agent-bus freeze scope immediately.

## Edits applied in this commit

| Location | Change |
|---|---|
| §1 W0 shipped subset (line ~77) | drop `bus.spi.ingress` from the list of SPI surfaces agent-bus "ships" |
| §2 ADR-0159 ownership bullets (lines ~148-151) | reword the "pairing with the new IngressGateway SPI" sentence to record supersession |
| §2 module table, agent-bus row (line ~162) | remove `bus.spi.ingress` from "active SPI surfaces"; note supersession by ADR-0161 |
| §2 tree (lines ~183-186) | remove the `ingress/` subtree (never materialized) with a supersession note |
| §2 dependency-direction diagram (line ~247) | "owns ingress + s2c …" → "owns s2c + neutral engine surfaces" |
| §4 #7 SPI purity (line ~346) | "(neutral engine / ingress / s2c)" → "(neutral engine / s2c)" |

Companion non-L0 corrections in the same commit: `agent-bus/module-metadata.yaml`
description, the `agent-runtime/pom.xml` sibling-module comment, and the
`architecture/workspace.dsl` agent-bus container description.
