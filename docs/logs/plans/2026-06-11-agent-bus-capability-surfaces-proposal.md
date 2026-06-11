---
date: 2026-06-11
status: approved
owner_directive: >
  Goal statement 2026-06-11: "agent-bus 可以支持记忆、知识工程、多智能体之间的
  相互异步通信" — the owner directs agent-bus to grow three real capability
  surfaces. This directive is the owner approval for the design below; the
  engine/s2c freeze decision is NOT re-opened.
decision_record: docs/adr/0163-agent-bus-capability-surfaces.yaml
---

# agent-bus capability surfaces — memory, knowledge, async agent messaging

## 1. Placement: new packages OUTSIDE the frozen spi surface

`bus.spi.engine` / `bus.spi.s2c` stay design-frozen exactly as decided
(Option C; `BusSpiFreezeArchTest` keeps forbidding production use). The new
capabilities land in sibling packages — `com.huawei.ascend.bus.memory`,
`bus.knowledge`, `bus.messaging` — each shipping its SPI AND an in-memory
reference implementation plus tests IN THE SAME CHANGE (the zero-impl-SPI
trap is the documented agent-bus failure mode; it is not repeated).

## 2. bus.memory — platform session memory + the ADR-0051 emission path

Authority: ADR-0051 ownership boundary. The platform owns S-side working
memory (conversation windows, trajectory-adjacent session state); business
facts discovered during execution are NOT stored platform-side — they are
emitted as structured events for the C-side to accept or discard.

- `SessionMemoryStore` (SPI): tenant+session scoped append/window/clear of
  `MemoryEntry(role, text, timestamp, attributes)`; `window(tenant, session,
  maxEntries)` returns newest-first bounded view. Reference impl
  `InMemorySessionMemoryStore`: per-(tenant,session) bounded deques (cap
  configurable, oldest-evicted), structural tenant isolation.
- `BusinessFactPublisher` (SPI) + `BusinessFactEvent` record (ADR-0051
  contract materialized: tenantId, sessionId, runId nullable, factType,
  payload map, placeholdersPreserved flag, occurredAt). Reference impl
  `RecordingBusinessFactPublisher` (bounded in-memory log — the platform
  never persists business facts; a real deployment plugs the C-side bridge).

## 3. bus.knowledge — retrieval seam, C-side content ownership

The platform never owns business knowledge content; it owns the SEAM agents
retrieve through.

- `KnowledgeSource` (SPI): `retrieve(KnowledgeQuery(tenantId, query, topK,
  filters)) -> List<KnowledgeFragment(sourceId, content, score, provenance
  map)>`. Tenant isolation structural (query carries tenant; sources must
  filter).
- `KnowledgeRegistry`: named sources per tenant; `CompositeKnowledgeSource`
  fans a query across registered sources and merges by score (stable
  ordering). Reference impl `InMemoryKnowledgeSource` (seeded fragments;
  naive token-overlap scoring — honest about being a reference, not a
  vector store).

## 4. bus.messaging — async inter-agent communication, in-process plane

Cross-process agent-to-agent stays A2A through the service facade
(A2A-NO-REWRITE untouched). bus.messaging is the in-process plane for
agents co-hosted on one runtime, aligned with §4 #28's bounded-buffer
discipline:

- `AgentMessageBus` (SPI): `publish(AgentMessage)`,
  `subscribe(tenantId, topic, AgentMessageHandler) -> Subscription
  (AutoCloseable)`. `AgentMessage(messageId, tenantId, topic, fromAgentId,
  correlationId nullable, traceparent nullable, payload map, occurredAt)`.
- Reference impl `InMemoryAgentMessageBus`: per-(tenant,topic) dispatch on a
  daemon single-thread executor (delivery ordered per topic), bounded
  per-subscriber queue (default 256, DROP_OLDEST with a dropped-message
  counter — never silent), handler exceptions logged + contained (one bad
  subscriber cannot break the topic), tenant-scoped: a subscriber never sees
  another tenant's messages.

## 5. Runtime integration (surgical)

`agent-runtime` already depends on agent-bus. `RuntimeAutoConfiguration`
exposes the three reference impls as `@ConditionalOnMissingBean` beans, and
`AgentExecutionContext` gains optional accessors (nullable getters wired by
the dispatcher when beans exist) so handlers/adapters reach memory,
knowledge and the message bus without new coupling. Heterogeneous engines
(openJiuwen/AgentScope/LangGraph adapters) need no change — the surfaces are
opt-in.

## 6. Example + tests

- Layer-1: per-impl unit tests (bounds, eviction, tenant isolation,
  drop-counter, handler containment, score merge).
- Example e2e: two stub agents on one booted RuntimeApp exchanging an async
  request/reply over bus.messaging, with session memory recording the turn
  and a knowledge lookup answering it.

## 7. Explicitly out of scope

Durable/broker-backed messaging (Kafka etc.), vector knowledge stores,
Graphiti adapter — all need infrastructure this host lacks; the SPIs are the
extension points. The frozen engine/s2c surfaces stay frozen.
