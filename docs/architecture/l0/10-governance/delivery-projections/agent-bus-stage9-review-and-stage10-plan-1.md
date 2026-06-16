---
artifact_type: delivery_projection
version: agent-bus-stage9-review-and-stage10-plan
status: draft
source_commit: 6c395034
source_stage9_commit: c532b082
source_stage9_plan: docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage8-review-and-stage9-plan.md
source_decision: docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md
target_module: agent-bus
---

# agent-bus Stage 9 评审与 Stage 10 计划

## 0. 结论

Stage 9 可以作为阶段性成果接受：`agent-bus` 已经从 C3 outbox / inbox 的最小骨架推进到 **lease-safe / persistence-ready** 状态。核心并发语义已经落入端口、worker、record 构造器、DDL contract 和 harness：stale worker 不能越权 ACK / RETRY / DLQ / EXPIRE，terminal / retry 状态会清理 lease，record 条件不变量进入 Java + DDL + harness，failure code 也有了 retryable / non-retryable / dedup 分类。

最新提交 `6c395034` 进一步把 L1/L2 文档迁移到 `architecture/L1-High-Level-Design` 和 `architecture/L2-Low-Level-Design`，并把 `agent-runtime` 命名同步到当前实现。这是合理的结构性整理，但也暴露出下一阶段必须收口的一类问题：部分治理文档和相对链接仍指向旧的 `architecture/docs/L1` / `architecture/docs/L2` 路径，L1 frontmatter 标 `active` 但正文和表格仍写“草案”。

Stage 10 不应继续补纯文档。下一阶段应进入 **持久化 adapter 边界 + 调度 tick 边界 + 文档路径基线收口** 的大批次：先裁决 DB / migration 归属；若裁决通过，落最小 JDBC/Flyway adapter；若裁决仍未通过，也必须完成 adapter port、SQL contract、polling/backpressure 参数、集成边界和验证计划，不能退回“继续完善架构稿”。

## 1. Stage 9 完成情况审查

### 1.1 已完成内容

- `ForwardingOutboxPort` 的 `markAcked` / `scheduleRetry` / `moveToDlq` / `markExpired` 全部携带 `leaseOwner`。
- `ForwardingDispatcherWorker` 在 ACK / RETRY / DLQ / EXPIRED 路径传递当前 worker 的 `leaseOwner`。
- 新增 `ForwardingLeaseException`，区分 `RECORD_NOT_FOUND`、`NO_LEASE`、`OWNER_MISMATCH`、`NOT_DISPATCHING`。
- `ForwardingOutboxRecord` / `ForwardingInboxRecord` compact constructor 固化状态条件不变量。
- `ForwardingFailureCode` 增加 retryable / non-retryable / dedup 分类，`ForwardingDeliveryResult.retry(...)` 和 `dlq(...)` 受分类约束。
- `forwarding-persistence.md` 补齐 DDL CHECK、claim SQL、lease-owner guarded state-update SQL。
- contract harness 覆盖 stale worker、lease 生命周期、record invariant、failure-code classification、DDL invariant。
- 未引入 JDBC / Flyway / broker / HTTP transport，`agent-bus` 仍不写 Task execution state。

验收判断：

- Stage 9 的核心技术目标成立，MI9-001..006 基本收口。
- 当前状态可以被称为 persistence-ready，但还不能称为 production persistence implemented。
- 路径 B 的选择正确：DB / migration 归属未确认前不引入生产数据库依赖。
- Stage 10 可以开始处理真实持久化 adapter 的裁决和最小落地，但必须保持 routeHandle opaque、tenant 行级隔离、Task state 禁止写入。

### 1.2 最新结构迁移审查

最新提交 `6c395034` 将 `agent-bus` L1 / L2 文档从旧路径迁移到：

- `architecture/L1-High-Level-Design/agent-bus/`
- `architecture/L2-Low-Level-Design/agent-bus/`

这一步和新的架构目录命名方向一致，也把 L1 文档状态推进到 `active`。但迁移后仍存在几类需要 Stage 10 收口的遗留：

- `agent-bus-forwarding-runtime-decision.md` frontmatter 的 `source_l1` 仍指向旧路径 `architecture/docs/L1/agent-bus/README.md`。
- 该 review packet 中多处 L2 链接仍指向 `architecture/docs/L2/agent-bus/...`。
- L1 `README.md` frontmatter 是 `status: active`，但正文“当前状态”和视图表仍写草案。
- Stage 7 / Stage 8 / Stage 9 计划中的历史路径可以保留为历史记录，但当前可执行计划必须使用新路径。

## 2. 当前修改意见

| 编号 | 问题 | 严重度 | 证据 | 修改意见 |
|---|---|---|---|---|
| MI10-001 | DB / migration 归属仍未裁决，真实持久化 adapter 不能安全落地。 | 高 | Stage 9 明确采用路径 B：不引入 JDBC / Flyway，DDL / SQL 仍为 contract / draft。 | Stage 10 第一项必须做人类裁决：DB 产品、migration 归属、adapter 模块归属、事务边界和回滚策略。没有裁决时不得引入生产 DB 依赖。 |
| MI10-002 | 文档迁移后仍有旧路径引用，会造成后续智能体读错 L1/L2 基线。 | 中 | `agent-bus-forwarding-runtime-decision.md` 仍引用 `architecture/docs/L1` / `architecture/docs/L2`。 | Stage 10 统一当前基线路径到 `architecture/L1-High-Level-Design` 和 `architecture/L2-Low-Level-Design`；历史计划中的旧路径只作为历史记录保留。 |
| MI10-003 | L1 文档状态表述不一致。 | 中 | `architecture/L1-High-Level-Design/agent-bus/README.md` frontmatter 为 `active`，正文和视图表仍写“草案”。 | 明确 L1 是 active baseline 还是 reviewed draft；若 active，表格状态应改为 active / accepted / draft-by-section，不要同时写草案。 |
| MI10-004 | 当前只有 SQL contract，没有生产 adapter 对 SQL 语义的可执行投影。 | 中 | `forwarding-persistence.md` 有 claim / state-update SQL，代码仍只有 port 与 in-memory fake。 | Stage 10 若 DB 裁决通过，新增最小 JDBC adapter；若未通过，至少新增 adapter conformance test contract，确保未来实现必须 obey SQL / lease guard。 |
| MI10-005 | polling cadence、lease TTL、claim limit、backpressure 仍是 deferred，worker 还不能作为运行时 tick 被调度。 | 中 | `ForwardingDispatcherWorker` 是同步 `runOnce`，无 scheduler / backpressure / admission 参数。 | Stage 10 定义 `ForwardingDispatchPolicy` 或等价配置对象，并用 harness 覆盖 limit、leaseUntil、empty tick、failure isolation；真实 scheduler 仍可 deferred。 |
| MI10-006 | delivery binding 仍完全抽象，缺少与 `agent-runtime` 受控调用路径的集成边界。 | 中 | `ForwardingDeliveryPort` 只消费 record；真实 HTTP / gRPC / broker binding deferred。 | Stage 10 只定义受控调用 adapter 边界和禁止项，不直接接真实 transport；必须确认不绕过 routeHandle、不解包 physical endpoint、不写 Task state。 |

## 3. Stage 10 目标

Stage 10 的目标是把 Stage 9 的 persistence-ready 底座推进为 **可接真实运行时的持久化 adapter 边界**：

> 完成 DB / migration 归属裁决；在允许范围内交付最小持久化 adapter 或 adapter contract harness；定义 dispatch tick / backpressure / lease policy；修正文档路径基线和 L1 状态；为后续真实 delivery binding 与 agent-runtime 受控调用建立明确边界。

Stage 10 仍然是大批次，但必须按“裁决 → adapter 边界 → tick policy → harness → 文档基线”的顺序推进。

## 4. Stage 10 开发切片

### 切片 1：DB / migration 归属裁决

在 `agent-bus-forwarding-runtime-decision.md` 和 `forwarding-persistence.md` 中补齐 Stage 10 裁决：

- DB 产品：Postgres / existing shared DB / deferred。
- migration 归属：`agent-bus` 自有、共享 schema 模块、`agent-runtime` 受控路径，或 deferred。
- adapter 归属：保留在 `agent-bus`，还是放到独立 infrastructure adapter 模块。
- transaction owner：claim + state update 由哪个 adapter / transaction manager 保证。
- rollback 策略：migration 回滚、feature flag、adapter disable。
- RLS 策略：若使用 Postgres，是否启用 tenant RLS；若不启用，必须有等价 tenant guard。

DoD：

- 裁决结果是 A/B/C 之一：
  - A：允许 `agent-bus` 内最小 JDBC adapter + migration 草案。
  - B：允许独立 adapter 模块，`agent-bus` 保持纯 SPI/runtime。
  - C：继续 deferred，但必须交付 adapter contract harness，不得接生产 DB。
- 未裁决时不得修改 `pom.xml` 增加 JDBC / Flyway / ORM 生产依赖。

### 切片 2：文档路径与状态基线收口

同步当前路径：

- `architecture/L1-High-Level-Design/agent-bus/README.md`
- `architecture/L1-High-Level-Design/agent-bus/*.md`
- `architecture/L2-Low-Level-Design/agent-bus/forwarding-outbox-inbox.md`
- `architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md`
- `docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md`

必须完成：

- 当前可执行路径全部使用 `architecture/L1-High-Level-Design` / `architecture/L2-Low-Level-Design`。
- L1 `status: active` 与正文一致；若部分视图仍是草案，表格按 section 标注，不要把整个 L1 同时称为 active 和草案。
- Stage 9 以前的历史计划可保留旧路径，但 Stage 10 计划和当前 review packet 不得继续引用旧路径作为当前事实源。

DoD：

- `rg -n "architecture/docs/L1|architecture/docs/L2" docs/architecture/l0/10-governance/review-packets architecture/L1-High-Level-Design architecture/L2-Low-Level-Design` 只允许命中历史说明或迁移备注。

### 切片 3：最小持久化 adapter 或 adapter contract harness

根据切片 1 裁决执行。

路径 A：允许落最小 JDBC adapter。

- 新增 adapter 实现，建议包或模块由裁决确定。
- 实现 `ForwardingOutboxPort` / `ForwardingOutboxClaimPort` 的最小 SQL 投影。
- claim 使用 `FOR UPDATE SKIP LOCKED` 或裁决的等价并发原语。
- state update 必须带 lease-owner guard。
- adapter 不接真实 delivery binding。
- adapter 不写 Task execution state。

路径 B/C：不允许落生产 DB adapter。

- 新增 adapter contract harness 或 SQL conformance test，锁定未来 adapter 必须满足：
  - tenant-scoped claim；
  - terminal 不可 claim；
  - stale / foreign / expired owner mutation 失败；
  - ACK / DLQ / EXPIRED / RETRY 清 lease；
  - DDL CHECK 与 Java record invariant 一致。
- 不新增生产 DB 依赖。

DoD：

- 无论 A/B/C，必须新增或更新测试 / harness，不能只改文档。
- 若 A 路径新增 adapter，必须有 negative test 覆盖 lease-owner guard。

### 切片 4：dispatch tick policy 与 backpressure 参数

新增或明确一个 policy 对象，建议命名：

- `ForwardingDispatchPolicy`
- 或 `ForwardingDispatcherWorkerOptions`

至少包含：

- `claimLimit`
- `leaseTtlMillis`
- `maxAttempts`
- `retryBackoff` 或 `nextAttemptAt` 计算策略入口
- empty tick 行为
- per-tenant tick 语义

边界：

- 不在 Stage 10 引入真实 scheduler 线程池。
- 不引入 mailbox / admission / W2 workflow primitives。
- 不把 backpressure 扩大成跨模块平台能力；本阶段只约束 forwarding worker 的 claim 和 retry 行为。

DoD：

- `runOnce` 或等价入口不再由调用方散传一组易错 primitive，而是使用 policy / options 承载。
- harness 覆盖 `claimLimit <= 0`、lease ttl 非法、maxAttempts 到达后的 DLQ / EXPIRE 行为。

### 切片 5：agent-runtime 受控调用边界

定义真实 delivery binding 的下一阶段边界，但不直接接 transport：

- `ForwardingDeliveryPort` 的生产 adapter 将来如何通过 `routeHandle` 调用目标 service。
- 哪些信息来自 discovery metadata / gateway audit context。
- 禁止从 `routeHandle` 反解 physical endpoint。
- 禁止 `agent-bus` 写 Task execution state。
- 失败映射到 `ForwardingFailureCode` 的规则。

DoD：

- L2 或 ICD 中有明确的 delivery binding boundary。
- 若新增 adapter skeleton，只能是接口 / contract / fake，不接 HTTP / gRPC / broker client。
- 必须同步 `agent-runtime` owner 需要确认的冲突点。

### 切片 6：验证与后置投影计划

验证交给施工智能体执行，不要求计划编写者本轮运行。

施工智能体必须在 Stage 10 完成后提交以下证据：

```powershell
.\mvnw.cmd -pl agent-bus test
rg -n "TaskExecution|TaskStatus|payloadBody|payload_body|physicalEndpoint|Kafka|RabbitMQ|RocketMQ|NATS" agent-bus
rg -n "architecture/docs/L1|architecture/docs/L2" docs/architecture/l0/10-governance/review-packets architecture/L1-High-Level-Design architecture/L2-Low-Level-Design
```

若选择路径 A 并新增 DB adapter，还必须补充：

```powershell
rg -n "java.sql|javax.sql|Jdbc|Flyway|liquibase" agent-bus
```

并解释命中项是否在裁决允许范围内。

## 5. Stage 10 不能接受的结果

- 未确认 DB / migration 归属就引入 JDBC / Flyway / ORM 生产依赖。
- 只修文档路径，不推进 adapter contract、policy、harness 或代码。
- 真实 adapter 状态变更不校验 lease owner。
- 把 `routeHandle` 解包成 physical endpoint。
- 让 `agent-bus` 写 Task execution state。
- 把 polling / backpressure 扩大成 W2 workflow primitives。
- L1 frontmatter 与正文状态继续冲突。
- 当前事实源继续指向旧 `architecture/docs/L1` / `architecture/docs/L2` 路径。

## 6. 给施工智能体的提示

这轮可以一次做大，但不要把“真实 DB adapter”放在裁决之前。建议执行顺序：

1. 收口 DB / migration / adapter 归属裁决。
2. 修正 L1/L2 当前路径和 active/draft 状态。
3. 根据裁决选择 adapter 实现或 adapter contract harness。
4. 引入 dispatch policy / options，减少散传 primitive。
5. 补 delivery binding boundary，不接真实 transport。
6. 更新 tests / harness / ICD / schema / L1 / L2 / review packet。
7. 执行验证命令并把结果写入 Stage 10 完成记录。

如果 DB / migration 仍未裁决，Stage 10 仍必须完成 2、3 的 contract harness 路径、4、5、6，不应降级为纯文档修补。
