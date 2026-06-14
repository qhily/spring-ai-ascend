---
artifact_type: a2d_delivery_projection
version: "agent-bus-stage4-review-and-stage5-plan"
status: draft
source_commit: "8042e13a agent-bus Stage 4: 类 MQ 转发设计态契约 (MI4 + 切片 0-4)"
source_previous_plan: "docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage3-close-review-and-stage4-plan.md"
source_l1: "architecture/docs/L1/agent-bus/README.md"
target_module: agent-bus
---

# agent-bus 第四阶段评审与第五阶段计划

## 1. 评审结论

最新提交 `8042e13a` 可以接受。

第四阶段主体目标已经完成：它回答了“怎么发”的设计态语义，并且没有越过运行态实现边界。

| 目标 | 完成情况 | 评审意见 |
|---|---|---|
| 前置文档整理 | 基本完成 | README 已拆分阶段记录与后续工作；Runtime/Core 命名基本同步；注册发现已改为 Stage 3 已形成 ICD 与 harness。 |
| 类 MQ 转发 ICD | 已完成 | 新增 `ICD-Agent-Bus-Forwarding`，覆盖 envelope、route handle、ack、retry、timeout、DLQ/replay、ordering、backpressure、failure modes 和禁止范围。 |
| L1 视图同步 | 基本完成 | README、logical、process、physical、features、scenarios 已引用转发语义；仍有少量 feature mapping 漂移。 |
| 设计级 harness | 已完成 | 新增 `AgentBusForwardingDesignContractTest`，保护 tenant、routeHandle、payloadRef、failure modes、broker-agnostic 边界。 |
| Ingress cursor 规则 | 已完成 | 新增 `IngressCursorRuleDesignContractTest`，把 `RUN_CREATE + ACCEPTED` cursor 规则固定为 gateway/handler 层设计态约束。 |

本次提交没有发现以下越界：

- 没有新增 broker / MQ 运行态绑定。
- 没有新增 queue / mailbox / DLQ / replay 运行态存储。
- 没有改变 Task lifecycle owner。
- 没有让 `agent-bus` 写 Task execution state。
- 没有绕过 Stage 3 route handle 直接使用物理 endpoint。
- 没有修改 Maven module 名或目录名。

提交信息记录的验证为：

```text
mvn -pl agent-bus test -> 89 tests green
```

本评审不重复执行本地验证；验证项作为后续执行计划的一部分记录。

## 2. 当前修改意见

### MI5-001：Feature Catalog 的 Stage 4 / Stage 3 场景映射仍是旧状态

位置：

- `architecture/docs/L1/agent-bus/features/README.md`

问题：

Feature 与视图映射里：

- `AB-F11 MQ-like Forwarding Substrate` 的进程视图仍写 “后续 Stage”，场景视图仍写 “待补”。
- `AB-F12 Agent Registry / Discovery` 的进程视图仍写 “后续 Stage”，场景视图仍写 “待补”。

但当前事实已经是：

- Stage 4 已新增 `SC-012` runtime-to-runtime 异步控制消息转发。
- Stage 3 已新增 `SC-007` 到 `SC-011` 注册、发现、health、version、tenant isolation 场景。

建议：

- `AB-F11` 的进程视图映射改为 Stage 4 forwarding / `SC-012`，场景视图改为 `SC-012`。
- `AB-F12` 的进程视图映射改为 Stage 3 registry/discovery，场景视图改为 `SC-007..SC-011`。

影响：

- 文档索引漂移，不影响代码或契约主体。
- 建议作为第五阶段切片 0 前置修正。

### MI5-002：Stage 4 运行态越界 trip-wire 范围偏窄

位置：

- `agent-bus/src/test/java/com/huawei/ascend/bus/architecture/AgentBusForwardingDesignContractTest.java`

问题：

当前 `stage4_adds_no_broker_runtime_package` 只导入：

```java
new ClassFileImporter().importPackages("com.huawei.ascend.bus.spi")
```

这能阻止 `com.huawei.ascend.bus.spi.broker` / `.queue` / `.dlq` 等 SPI 下包名出现，但不能捕捉：

- `com.huawei.ascend.bus.broker`
- `com.huawei.ascend.bus.runtime.queue`
- `com.huawei.ascend.bus.forwarding.dlq`
- 其他非 `spi` 生产包里的运行态实现。

建议：

- 将 ArchUnit 导入范围扩大为 `com.huawei.ascend.bus`。
- 或增加源码路径扫描，检查 `agent-bus/src/main/java/com/huawei/ascend/bus/**` 下是否出现 broker / queue / mailbox / dlq / replay runtime 包。
- 同时继续依赖 module metadata / dependency boundary 测试，防止新增 broker 相关生产依赖。

影响：

- 不阻塞接受本提交。
- 对第五阶段前的安全边界很重要，因为下一阶段会讨论运行态候选方案。

### MI5-003：`payloadRef` 是否必须对所有 forwarding envelope 必填需要裁决

位置：

- `docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding.md`
- `architecture/docs/L1/agent-bus/scenarios.md`
- `AgentBusForwardingDesignContractTest`

问题：

当前 ICD 把 `payloadRef` 放入 forwarding envelope required fields，并强调 envelope 不携带 payload body。这符合控制/数据分离，但有一个设计细节需要确认：

- 有些 runtime-to-runtime 控制消息可能只有控制语义，没有外部大载荷。
- 如果 `payloadRef` 对所有消息必填，需要定义“空 payload reference”或 “no-payload reference” 的表达。
- 如果 `payloadRef` 只在有载荷时必填，应把 required fields 改成 required/conditional required 两层。

建议裁决：

| 方案 | 说明 |
|---|---|
| 方案 A | `payloadRef` 始终必填；无载荷时使用明确的 empty/no-payload reference。 |
| 方案 B | `payloadRef` 条件必填；有外部数据或大载荷时必须出现，纯控制消息可省略。 |

我倾向方案 B：更贴合控制消息，但必须保留“不得携带 payload body / token stream / Task state”的强约束。

影响：

- 这是第五阶段进入运行态候选评审前应先裁决的契约细节。
- 不阻塞第四阶段接受，因为当前是设计态。

### MI5-004：README 后续工作仍把 Stage 4 设计态契约列成待办

位置：

- `architecture/docs/L1/agent-bus/README.md`

问题：

“后续工作”里仍列：

```text
Stage 4 类 MQ 转发语义：见 ICD-Agent-Bus-Forwarding
```

但 Stage 4 设计态语义已经完成。这里更适合作为“已完成阶段记录”或“当前设计态入口”，而不是后续待办。

建议：

- 把 Stage 4 ICD 链接保留在“类 MQ 转发契约（Stage 4 设计态）”章节。
- 从“后续工作”移除该条，或改成“Stage 5 运行态候选方案评审”。

影响：

- 可读性问题，不影响契约主体。

## 3. 第五阶段目标

第五阶段建议聚焦：

`运行态候选方案评审`

第四阶段已经建立 broker-agnostic 转发语义。第五阶段不应立即写代码，而应比较运行态实现候选，形成 H2/H3 级别的选型输入。

第五阶段要回答：

- 哪类运行态承载 Stage 4 的 forwarding semantics。
- 哪些能力必须由 bus runtime 提供，哪些可以由底层 broker / 数据库 / runtime-local queue 提供。
- route handle 如何绑定到实际投递目标。
- DLQ / replay / ordering / backpressure / timeout 的最小实现边界。
- tenant isolation、observability、audit、cost 和 capacity 如何落到候选方案。

## 4. 第五阶段开发切片

### 切片 0：前置文档与 harness 小修

范围：

- 修正 MI5-001。
- 修正 MI5-002。
- 修正 MI5-003 的裁决记录。
- 修正 MI5-004。

验收：

```powershell
rg -n "AB-F11|AB-F12|后续 Stage|待补" architecture/docs/L1/agent-bus/features/README.md -S
rg -n "importPackages\\(\"com\\.huawei\\.ascend\\.bus\\.spi\"\\)" agent-bus/src/test/java/com/huawei/ascend/bus/architecture/AgentBusForwardingDesignContractTest.java -S
```

剩余命中必须有合理解释。

### 切片 1：运行态候选方案评审包

建议新增：

```text
docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-candidates.md
```

候选至少包含：

| 候选 | 说明 |
|---|---|
| in-memory dispatcher | 单进程/单实例早期实现，低复杂度，弱 durable。 |
| runtime-local queue | 每个 runtime 内部队列，适合同实例或近端协作，但跨实例能力弱。 |
| database outbox / inbox | durable、易审计，吞吐/延迟和 ordering 需要评估。 |
| 外部 broker | Kafka / NATS / RocketMQ / RabbitMQ 等，适合高吞吐和解耦，但运维复杂度高。 |
| hybrid | control event + durable outbox + broker dispatch 的组合。 |

评审维度至少包含：

- 是否满足 Stage 4 forwarding envelope。
- route handle 绑定方式。
- tenant isolation。
- ack / retry / timeout。
- DLQ / replay。
- ordering。
- backpressure。
- durability。
- observability / audit。
- local dev 复杂度。
- production 运维复杂度。
- 对 Spring Boot / Java 21 / current Maven modules 的影响。
- 是否需要新增 production dependency。

### 切片 2：运行态最小边界定义

不选产品前，也要定义最小运行态边界：

| 边界 | 必须定义 |
|---|---|
| sender | 谁构造 forwarding envelope，谁负责 idempotencyKey。 |
| dispatcher | 谁做 admission / routeHandle resolve / delivery ack。 |
| receiver | 谁消费 envelope，如何把 outcome 回到 runtime owner。 |
| store | 是否需要 durable outbox / inbox / DLQ；若需要，owner 是谁。 |
| observer | trace / audit / metrics / cost 如何关联 tenant、routeHandle、correlationId。 |
| backpressure | 拒绝、延迟、降速分别如何表达。 |

输出可以是评审包章节，不要求代码。

### 切片 3：候选方案评分矩阵

建议新增评分表：

| 维度 | 权重 | in-memory | runtime-local queue | DB outbox/inbox | external broker | hybrid |
|---|---:|---:|---:|---:|---:|---:|
| tenant isolation | 高 | 待评估 | 待评估 | 待评估 | 待评估 | 待评估 |
| durability | 高 | 待评估 | 待评估 | 待评估 | 待评估 | 待评估 |
| backpressure | 高 | 待评估 | 待评估 | 待评估 | 待评估 | 待评估 |
| local dev simplicity | 中 | 待评估 | 待评估 | 待评估 | 待评估 | 待评估 |
| production ops | 高 | 待评估 | 待评估 | 待评估 | 待评估 | 待评估 |
| ordering / replay | 中 | 待评估 | 待评估 | 待评估 | 待评估 | 待评估 |

评分不必一次定论，但必须暴露 trade-off。

### 切片 4：L1 物理视图预同步

更新：

- `architecture/docs/L1/agent-bus/physical.md`
- `architecture/docs/L1/agent-bus/process.md`
- `architecture/docs/L1/agent-bus/features/README.md`

要求：

- 明确第五阶段仍是候选评审，不是运行态落地。
- 明确哪些 runtime choice deferred。
- 不把任何 broker 产品写成已接受事实。

## 5. 禁止范围

第五阶段不得：

- 直接引入 broker / MQ 生产依赖。
- 新增运行态 queue / DLQ / replay store。
- 修改 Maven module 名或目录名。
- 改变 Task lifecycle owner。
- 让 `agent-bus` 写 Task execution state。
- 绕过 `routeHandle` 直接绑定物理 endpoint。
- 把某个产品能力反向写成架构语义。

## 6. 验证计划

验证由后续施工智能体或人工执行，本计划制定者不主动本地运行。

建议执行：

```powershell
.\mvnw.cmd -pl agent-bus test
```

建议静态检查：

```powershell
rg -n "AB-F11|AB-F12|后续 Stage|待补" architecture/docs/L1/agent-bus/features/README.md -S
rg -n "broker runtime|运行态转发底座|Kafka|NATS|RocketMQ|RabbitMQ" architecture/docs/L1/agent-bus docs/architecture/l0/05-contracts/human-readable docs/architecture/l0/10-governance/review-packets -S
rg -n "payloadRef|payload body|token stream|Task execution state|routeHandle" docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding.md architecture/docs/L1/agent-bus -S
```

通过标准：

- Maven 测试通过。
- Stage 4 设计态不被误写为运行态已实现。
- 运行态候选方案评审包能说明每个候选的 trade-off。
- 没有产品选型越过 H2/H3 评审。
- forwarding envelope 的 `payloadRef` 规则已裁决。

## 7. 第五阶段之后

第五阶段完成后，才适合进入：

`Stage 6 最小运行态切片设计`

第六阶段可以围绕被选中的候选方案定义最小实现切片，例如：

- 最小 in-memory dispatcher。
- 最小 outbox/inbox 表与状态机。
- 最小 broker adapter SPI。
- 最小 DLQ/replay harness。

但第六阶段必须先拿到第五阶段的候选方案裁决，不能直接由实现反推架构。
