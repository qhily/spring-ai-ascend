---
artifact_type: a2d_delivery_projection
version: "agent-bus-stage5-review-and-stage6-plan"
status: draft
source_commit: "4a2b69ff agent-bus Stage 5: 运行态转发候选方案评审 (MI5 + 切片 0-4)"
source_previous_plan: "docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage4-review-and-stage5-plan.md"
source_review_packet: "docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-candidates.md"
target_module: agent-bus
---

# agent-bus 第五阶段评审与第六阶段计划

## 1. 评审结论

最新提交 `4a2b69ff` 可以接受。

第五阶段本来就是“运行态候选方案评审”，所以本阶段以 Markdown 文档为主是正常的。它不是编码阶段，而是进入编码前的选型输入阶段。若没有 H2/H3 对候选方案的裁决，直接写 broker / queue / outbox 等生产代码反而会违反前几阶段建立的 A2D 流程。

本次提交完成情况如下：

| 目标 | 完成情况 | 评审意见 |
|---|---|---|
| MI5-001 Feature Catalog 映射 | 已完成 | `AB-F11` 已映射到 `SC-012`，`AB-F12` 已映射到 `SC-007..SC-011`。 |
| MI5-002 broker runtime 越界检查 | 已完成 | `AgentBusForwardingDesignContractTest` 已从 `com.huawei.ascend.bus.spi` 扩大到 `com.huawei.ascend.bus`。 |
| MI5-003 `payloadRef` 裁决 | 已完成 | 采用方案 B：`payloadRef` 条件必填；纯控制消息可省略，但仍禁止 payload body / token stream / Task state。 |
| MI5-004 README 后续工作 | 已完成 | Stage 4 语义从待办改为设计态入口，Stage 5 候选评审成为后续入口。 |
| 运行态候选评审包 | 已完成 | 新增 5 个候选、13 项评审维度、最小边界、评分矩阵和 deferred 决策。 |
| L1 预同步 | 已完成 | physical/process/features 已标注候选评审态和 deferred choices。 |

本次提交没有发现以下越界：

- 没有新增运行态转发底座。
- 没有引入 broker / MQ 生产依赖。
- 没有新增 queue / DLQ / replay store。
- 没有修改 Task lifecycle owner。
- 没有绕过 `routeHandle`。
- 没有用具体产品反向定义架构语义。

提交信息记录的验证为：

```text
mvn -pl agent-bus test -> 89 tests green
```

本评审不重复执行本地验证；验证作为后续执行计划记录。

## 2. 关于“为什么大部分是 md”

这是正常的，但只对第五阶段正常。

当前流程的阶段分工是：

| 阶段 | 目标 | 主要产物 |
|---|---|---|
| Stage 3 | 明确“发给谁” | 注册发现 ICD、L1 视图、设计级 harness。 |
| Stage 4 | 明确“怎么发”的 broker-agnostic 语义 | 转发 ICD、L1 视图、设计级 harness。 |
| Stage 5 | 比较“用什么运行态承载” | 候选方案评审包、评分矩阵、trade-off。 |
| Stage 6 | 在已有裁决基础上定义最小实现切片 | 设计到代码的施工计划；必要时开始最小生产代码。 |

因此：

- Stage 5 大部分是 md，是符合计划的。
- 如果 Stage 6 仍然只有泛泛 md，而没有候选裁决、接口边界、测试计划和最小代码切片，那就不够。
- 真正开始写代码前，必须先从 C1/C2/C3/C4/C5 中裁决一个“最小运行态切片”的方向，或者明确 Stage 6 只做“裁决包”，不做代码。

## 3. 当前修改意见

### MI6-001：Stage 5 暴露 trade-off，但没有形成可执行裁决

位置：

- `docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-candidates.md`

问题：

文档明确说“不下选型定论”，同时又说 Stage 6 可以围绕“被选中的候选”定义最小实现切片。这是诚实的，但也意味着 Stage 6 不能直接进入代码实现：因为“被选中的候选”现在还不存在。

建议：

- 在 Stage 6 先增加 H2/H3 裁决切片。
- 裁决至少选择一个“最小运行态切片候选”，并明确是否允许写生产代码。
- 如果人类暂时不裁决，Stage 6 只能继续写“实现计划”，不能写实现。

### MI6-002：候选评审没有给出推荐路径，下一位施工智能体容易原地打转

位置：

- `agent-bus-forwarding-runtime-candidates.md`

问题：

评审包用强/中/弱暴露 trade-off 是对的，但对后续自动化施工来说还缺少“推荐进入 H2/H3 讨论的默认候选”。完全不推荐会导致下一位智能体继续扩展文档，而不是推动裁决。

建议：

补一个非裁决性质的推荐：

| 推荐 | 说明 |
|---|---|
| 默认推荐候选 | C3 database outbox / inbox。 |
| 理由 | durable、审计强、tenant 行级隔离清楚、可能复用现有 DB、比外部 broker 运维轻。 |
| 备选早期实验 | C1 in-memory dispatcher。 |
| 使用条件 | 只适合本地开发或非 durable 原型，不作为生产候选。 |
| 暂不推荐 | C5 hybrid。 |
| 理由 | 复杂度过高，不适合最小切片。 |

这不是架构裁决，只是帮助 H2/H3 快速聚焦。

### MI6-003：Stage 6 的“写代码”边界需要明示

问题：

用户已经提出疑问：“它应该是写代码才对吧？” 这说明当前阶段文档没有足够清楚地区分“候选评审”与“代码施工”。

建议：

Stage 6 文档必须明确：

- 若 H2/H3 裁决 C1：可以写最小 in-memory dispatcher 代码和测试。
- 若 H2/H3 裁决 C3：先写 outbox/inbox L2 技术设计、schema、state machine harness，再决定是否写代码。
- 若 H2/H3 裁决 C4：先写 broker adapter SPI 和 dependency quarantine 设计，不直接引入具体 broker client。
- 若无裁决：不得写生产代码。

### MI6-004：Stage 5 评审包缺少“不可接受候选”条件

问题：

每个候选都有 trade-off，但没有写“什么情况下该候选不可接受”。这会让弱候选也长期保留，影响裁决效率。

建议：

给每个候选补充 rejection criteria：

| 候选 | 不可接受条件示例 |
|---|---|
| C1 | 需要跨进程可靠投递、重启不丢消息、生产审计。 |
| C2 | 需要跨实例一致路由或 durable replay。 |
| C3 | 系统没有可复用 DB，或 DB 压力不能接受 polling/outbox。 |
| C4 | 团队无法承担 broker 运维，或当前阶段禁止新增生产依赖。 |
| C5 | 最小切片阶段，复杂度超过收益。 |

## 4. 第六阶段目标

第六阶段建议聚焦：

`运行态候选裁决与最小实现切片设计`

第六阶段不应默认直接写代码。它应该先产生一个明确裁决：

| 裁决项 | 必须回答 |
|---|---|
| 选择哪个候选作为最小运行态切片 | C1 / C2 / C3 / C4 / C5，或组合。 |
| 是否允许写生产代码 | 是 / 否。 |
| 允许写哪些代码 | SPI、in-memory runtime、outbox schema、adapter shell、harness。 |
| 禁止写哪些代码 | broker client、DLQ store、Task state writer、跨模块大改。 |
| 验收标准 | 哪些测试、哪些文档、哪些漂移检查。 |

## 5. 第六阶段开发切片

### 切片 0：补齐 Stage 5 裁决输入

修改范围：

- `docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-candidates.md`

要求：

- 增加“推荐进入 H2/H3 的候选”章节。
- 增加每个候选的 rejection criteria。
- 明确当前评审仍不等同于最终裁决。

### 切片 1：H2/H3 裁决记录

建议新增：

```text
docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md
```

内容至少包含：

- 采用候选。
- 不采用候选及原因。
- 是否允许进入生产代码施工。
- 最小实现范围。
- 禁止范围。
- 需要通知的 owner。

如果人类没有给出裁决，施工智能体应停在 draft，不进入代码。

### 切片 2：最小实现切片计划

根据裁决分支生成：

#### 若选择 C1：in-memory dispatcher

允许计划：

- 新增最小 `ForwardingDispatcher` 接口。
- 新增 in-memory 实现。
- 新增 envelope carrier 类型或复用设计态字段。
- 增加单模块测试：tenant mismatch、routeHandle missing、backpressure rejected、duplicate suppressed。

禁止：

- durable DLQ。
- broker client。
- 跨模块 runtime 绑定。

#### 若选择 C3：database outbox / inbox

允许计划：

- 先写 L2 技术设计。
- 定义 outbox/inbox state machine。
- 定义 schema 草案和 migration plan。
- 增加 schema/harness 测试计划。

是否写生产代码需单独裁决。

#### 若选择 C4：external broker

允许计划：

- 先写 broker adapter SPI。
- 定义 dependency quarantine。
- 定义产品选择评分矩阵。
- 不直接引入 Kafka/NATS/RocketMQ/RabbitMQ client。

### 切片 3：L1 同步

更新：

- `architecture/docs/L1/agent-bus/README.md`
- `architecture/docs/L1/agent-bus/physical.md`
- `architecture/docs/L1/agent-bus/process.md`
- `architecture/docs/L1/agent-bus/features/README.md`

要求：

- 明确 Stage 6 的裁决结果。
- 如果仍未裁决，标注为“实现等待裁决”。
- 如果允许代码施工，标注最小切片边界。

### 切片 4：验证计划

验证由后续施工智能体或人工执行。

建议执行：

```powershell
.\mvnw.cmd -pl agent-bus test
```

如果进入代码施工，再按改动范围追加：

```powershell
.\mvnw.cmd -pl agent-bus -am test
```

静态检查：

```powershell
rg -n "采用候选|不采用候选|允许写生产代码|禁止范围" docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md -S
rg -n "Kafka|NATS|RocketMQ|RabbitMQ|broker client" agent-bus/pom.xml agent-bus/src/main/java -S
rg -n "Task execution state|payload body|token stream" architecture/docs/L1/agent-bus docs/architecture/l0/05-contracts/human-readable -S
```

## 6. 禁止范围

第六阶段在没有 H2/H3 裁决前不得：

- 写生产 runtime dispatcher。
- 引入 broker / MQ 生产依赖。
- 新增 outbox / inbox / DLQ / replay store。
- 改 Task lifecycle owner。
- 让 `agent-bus` 写 Task execution state。
- 绕过 `routeHandle`。

即使有 H2/H3 裁决，也不得：

- 用某个产品能力反向修改 Stage 4 的 broker-agnostic 语义。
- 把大对象正文或 token stream 放入 forwarding envelope。
- 把注册发现变成 agent 定义仓库。

## 7. 下一阶段之后

如果第六阶段完成裁决并允许代码施工，第七阶段才适合进入：

`最小运行态实现`

第七阶段可以开始写代码，但必须严格受第六阶段裁决约束。届时“主要是 md”就不再足够；需要出现 production code、unit tests、architecture trip-wire 和 L1 同步。
