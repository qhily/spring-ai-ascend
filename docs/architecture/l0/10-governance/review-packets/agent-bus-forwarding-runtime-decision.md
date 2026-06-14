---
artifact_type: a2d_review_packet
version: "agent-bus-forwarding-runtime-decision"
status: draft
source_plan: "docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage5-review-and-stage6-plan.md"
source_candidates: "docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-candidates.md"
source_icd: "docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding.md"
source_l1: "architecture/docs/L1/agent-bus/README.md"
target_module: agent-bus
---

# agent-bus 运行态转发候选方案裁决（Stage 6 H2/H3）

## 0. 文档状态与裁决前提

**当前状态：draft — 等待 H2/H3 裁决。**

本文档是 Stage 6（运行态候选裁决与最小实现切片设计）的裁决记录入口。Stage 5 评审包（[`candidates`](agent-bus-forwarding-runtime-candidates.md)）已暴露 5 候选的 trade-off、推荐方向与 rejection criteria，但**未下裁决**。Stage 6 计划（§4、§6）明确：在 H2/H3 正式裁决前，施工智能体不得写生产 runtime 代码，本文档停在 draft。

裁决权威方：**H2/H3**（架构治理 / 人类决策）。本文档在 H2/H3 给出裁决前，只承载「待裁决字段 + 推荐输入 + 禁止范围」，不构成裁决。

回应「Stage 6 是不是应该写代码」的疑问（Stage 6 计划 MI6-003）：Stage 6 的职责是**先裁决、再（可能）施工**，不是默认写代码。是否写生产代码是裁决项之一（见 §4），在裁决为「否」或「未裁决」时，本阶段产出的是裁决记录与分支式实现计划，不是生产代码。真正开始写代码的最早时机是 Stage 7（Stage 6 计划 §7）。

## 1. 待裁决项

| 裁决项 | 当前状态 | 必须回答 |
|---|---|---|
| 采用候选 | **待 H2/H3 裁决** | C1 / C2 / C3 / C4 / C5，或组合。 |
| 是否允许写生产代码 | **否（无裁决）** | 是 / 否；若否，本阶段仅产出实现计划。 |
| 最小实现范围 | 待裁决（分支式候选范围见 §5） | SPI / in-memory runtime / outbox schema / adapter shell / harness。 |
| 验收标准 | 待裁决 | 哪些测试、哪些文档、哪些漂移检查。 |

## 2. 采用候选（待裁决）

**当前：无裁决。**

Stage 5 评审包给出的**非裁决性质推荐**（[`candidates §6.2`](agent-bus-forwarding-runtime-candidates.md)）作为 H2/H3 聚焦输入：

| 角色 | 候选 | 说明 |
|---|---|---|
| 默认推荐（非裁决） | C3 database outbox / inbox | durable、审计强、tenant 行级隔离清楚、可能复用现有 DB、运维轻。 |
| 备选早期实验 | C1 in-memory dispatcher | 仅本地开发 / 非 durable 原型，不作为生产候选。 |
| 暂不推荐 | C5 hybrid | 复杂度过高，不适合最小切片。 |

> 以上是推荐输入，**不是采用裁决**。采用哪个候选由 H2/H3 在此章节正式填入。

## 3. 不采用候选及原因（待裁决）

**当前：无裁决。** H2/H3 应据 [`candidates §6.2 rejection criteria`](agent-bus-forwarding-runtime-candidates.md) 判定每个候选的不可接受条件是否命中：

| 候选 | 不可接受条件（命中即不采用） |
|---|---|
| C1 | 需要跨进程可靠投递、重启不丢消息、或生产审计。 |
| C2 | 需要跨实例一致路由或 durable replay。 |
| C3 | 系统没有可复用 DB，或 DB 压力不能接受 polling / outbox。 |
| C4 | 团队无法承担 broker 运维，或当前阶段禁止新增生产依赖。 |
| C5 | 处于最小切片阶段，复杂度超过收益。 |

H2/H3 裁决后，在此章节明确「不采用 X，因为命中 rejection criteria Y」。

## 4. 是否允许写生产代码

**当前裁决：否 —— 无 H2/H3 裁决，本阶段不得写生产 runtime 代码。**

依 Stage 6 计划 §6：在裁决前不得写生产 runtime dispatcher、不得引入 broker / MQ 生产依赖、不得新增 outbox / inbox / DLQ / replay store。

允许的「非生产代码」产物（本阶段 draft 可产出）：

- 裁决记录文档（本文档）。
- 分支式最小实现切片**计划**（§5），不展开生产实现。
- L1 同步（标注「实现等待裁决」）。

裁决后按分支激活（见 §5）：

| 若裁决 | 允许写生产代码 |
|---|---|
| C1 in-memory dispatcher | 最小 `ForwardingDispatcher` 接口 + in-memory 实现 + 单模块测试（见 §5.1）。 |
| C3 database outbox / inbox | **先**写 L2 技术设计 / schema / state machine harness；是否写生产代码需**单独**裁决（见 §5.2）。 |
| C4 external broker | **先**写 broker adapter SPI + dependency quarantine 设计；不直接引入具体 broker client（见 §5.3）。 |
| 无裁决 / 未裁决 | **不得**写生产代码。 |

## 5. 最小实现范围（分支式候选计划，待裁决激活）

以下三个分支对应 Stage 6 计划切片 2 的最小实现计划，**全部待 H2/H3 裁决后激活**；裁决前不展开生产实现。

### 5.1 若采用 C1：in-memory dispatcher

允许计划：

- 新增最小 `ForwardingDispatcher` 接口。
- 新增 in-memory 实现。
- 新增 envelope carrier 类型或复用设计态字段。
- 单模块测试：tenant mismatch、routeHandle missing、backpressure rejected、duplicate suppressed。

禁止：

- durable DLQ。
- broker client。
- 跨模块 runtime 绑定。

### 5.2 若采用 C3：database outbox / inbox

允许计划：

- 先写 L2 技术设计。
- 定义 outbox / inbox state machine。
- 定义 schema 草案和 migration plan。
- 增加 schema / harness 测试计划。

是否写生产代码需**单独**裁决。

### 5.3 若采用 C4：external broker

允许计划：

- 先写 broker adapter SPI。
- 定义 dependency quarantine。
- 定义产品选择评分矩阵。
- 不直接引入 Kafka / NATS / RocketMQ / RabbitMQ client。

### 5.4 若未裁决

全部分支停在计划态，不产出生产代码；L1 标注「实现等待裁决」。

## 6. 禁止范围

### 6.1 无 H2/H3 裁决前（当前状态适用）

第六阶段在无裁决前**不得**（Stage 6 计划 §6）：

- 写生产 runtime dispatcher。
- 引入 broker / MQ 生产依赖。
- 新增 outbox / inbox / DLQ / replay store。
- 改 Task lifecycle owner。
- 让 `agent-bus` 写 Task execution state。
- 绕过 `routeHandle`。

### 6.2 即使有 H2/H3 裁决，也始终不得

- 用某个产品能力反向修改 Stage 4 的 broker-agnostic 语义。
- 把大对象正文或 token stream 放入 forwarding envelope（`payloadRef` 条件必填，MI5-003 方案 B）。
- 把注册发现变成 agent 定义仓库。
- 让 `agent-bus` 写 Task execution state。
- 跨 tenant fallback（R-C.c）。

## 7. 需要通知的 owner

| Owner | 通知事项 |
|---|---|
| H2/H3 | 本文等待裁决：采用候选 / 是否允许写生产代码 / 验收标准。 |
| agent-bus 模块负责人 | Stage 6 停在 draft，裁决前不写生产代码；裁决后按 §5 分支激活。 |
| `agent-runtime`（当前实现落点：`agent-service`）owner | 转发底座不改变 Task lifecycle owner；runtime 构造点 / outbox owner（若 C3）落点需协调，但 Task execution state 仍归 runtime。 |
| `agent-core`（当前实现落点：`agent-execution-engine`）owner | 转发底座不触及 engine 执行边界，无 owner 变更。 |

## 8. 后续

- H2/H3 裁决后：把本文档从 draft 升为裁决，填入采用候选 / 不采用候选 / 是否允许生产代码 / 验收标准，并据 §5 激活对应分支。
- 若裁决允许生产代码：进入 **Stage 7 最小运行态实现**，受本裁决约束（Stage 6 计划 §7）。
- 若裁决 C3 且需 schema：补 L2 技术设计与 migration plan，再单独裁决是否写生产代码。

相关文档：

- Stage 5 候选评审：[`agent-bus-forwarding-runtime-candidates`](agent-bus-forwarding-runtime-candidates.md)。
- Stage 6 计划：[`agent-bus-stage5-review-and-stage6-plan`](../delivery-projections/agent-bus-stage5-review-and-stage6-plan.md)。
- 设计态契约：[`ICD-Agent-Bus-Forwarding`](../../05-contracts/human-readable/ICD-agent-bus-forwarding.md)（HD4）。
- L1 入口：[`agent-bus L1 README`](../../../../architecture/docs/L1/agent-bus/README.md)。
