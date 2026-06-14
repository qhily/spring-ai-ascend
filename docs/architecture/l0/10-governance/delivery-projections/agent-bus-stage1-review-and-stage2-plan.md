---
artifact_type: a2d_delivery_projection
version: "agent-bus-stage1-review-and-stage2-plan"
status: draft
source_commit: "0d43fd39 test(agent-bus): add Stage 1 harness — ingress contracts + arch boundary"
source_stage1_plan: "docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage1-harness.md"
source_l1: "architecture/docs/L1/agent-bus/README.md"
target_module: agent-bus
---

# agent-bus Stage 1 评审与 Stage 2 计划

## 1. Stage 1 评审结论

结论：`接受，带后续修改意见`

最新提交 `0d43fd39` 基本完成了 Stage 1 目标：

- 新增模块边界 harness。
- 新增 SPI 纯度 harness。
- 新增 `IngressEnvelope` 契约测试。
- 新增 `IngressResponse` 契约测试。
- 没有修改 production code。
- 没有触碰 S2C `tenantId` 迁移。
- 没有实现 broker、runtime bus、mailbox、backpressure、tick、DLQ、ordering。

本地验证状态：

- 已尝试执行 `.\mvnw.cmd -pl agent-bus test`。
- 未能运行，原因是当前环境没有正确配置 `JAVA_HOME`。
- 后续施工智能体需要在可用 Java 环境中补跑测试，并记录 Java/Maven 版本与测试结果。

## 2. 当前修改意见

### MI-001：SPI 纯度 harness 需要补齐 HTTP framework 覆盖

当前 `AgentBusSpiPurityTest` 已覆盖：

- Spring
- Reactor
- Jackson
- Micrometer
- OpenTelemetry
- Kafka
- NATS

但 Stage 1 计划中写明 SPI 包不得引入 `HTTP framework`，当前测试没有覆盖常见 HTTP / network framework 包。

建议补充：

- `jakarta.servlet..`
- `javax.servlet..`
- `jakarta.ws.rs..`
- `javax.ws.rs..`
- `org.apache.http..`
- `okhttp3..`
- `io.netty..`
- `io.vertx..`

处理建议：

- 作为 Stage 1 follow-up 小修补。
- 只修改测试，不修改 production code。

### MI-002：模块依赖边界还缺少 POM / module metadata 漂移检查

当前 `AgentBusDependencyBoundaryTest` 能检查 production class 是否依赖 sibling module package。

它不能发现下面这种情况：

- `agent-bus/pom.xml` 新增了未使用的 production dependency。
- `agent-bus/module-metadata.yaml` 中的 `forbidden_dependencies` 与 POM 或 L1 文档漂移。
- test-scope dependency 被误写成 compile/runtime scope。

处理建议：

- 增加轻量 drift check，读取 `agent-bus/pom.xml` 和 `agent-bus/module-metadata.yaml`。
- 检查 forbidden sibling module 不出现在 production dependency 中。
- 检查 ArchUnit 仍是 test-scope。

这可以作为 Stage 1 follow-up，也可以并入 Stage 2 的验证准备。

### MI-003：`IngressResponse.accepted(..., null)` 的行为需要架构裁决

当前 `IngressResponseTest` 记录了现状：`IngressResponse.accepted(requestId, null)` 不会失败。

这和 ingress 契约中的 cursor 规则存在一个细节张力：

- 契约意图：`RUN_CREATE` 被接受时应返回 cursor。
- 当前 `IngressResponse` 本身不知道原始 `requestType`，所以构造器无法单独判断“accepted 是否必须有 cursor”。

处理建议：

- 不在 Stage 1 中直接改 production code。
- 后续需要裁决：
  - 方案 A：保持 `IngressResponse` 低上下文，只在 gateway / handler 层验证 `RUN_CREATE + ACCEPTED` 必须有 cursor。
  - 方案 B：让 response 携带足够上下文，或提供 requestType-aware factory。

建议优先采用方案 A：在后续 ingress gateway harness 中增加上下文级断言。

### MI-004：ArchUnit 测试应避免“空导入误通过”

当前 ArchUnit 测试通过 `importPackages("com.huawei.ascend.bus")` 和 `importPackages("com.huawei.ascend.bus.spi")` 导入生产类。

风险较低，但后续可以补一个小断言：

- 导入的 production classes 不为空。
- 导入范围只包含 production classes，不包含 test classes。

处理建议：

- 作为测试质量小修补，不阻塞 Stage 1 接受。

## 3. Stage 2 目标

Stage 2 建议聚焦：`S2C tenant 契约迁移 harness`

原因：

- H2 已接受 `S2cCallbackEnvelope` 需要增加 `tenantId`。
- 当前 Java record 和 YAML 契约仍没有该字段。
- 部分 L1 文档和模板已经把 `S2cCallbackEnvelope.tenant_id` 写成目标态或事实态。
- 这是明确的架构事实冲突，应优先消除。

Stage 2 不处理：

- 类 MQ 转发底座实现。
- agent/service/capability 注册发现实现。
- Federation runtime。
- Reflection typed envelope。
- EnginePort terminal event harness。
- Task 生命周期所有权变化。

## 4. Stage 2 冲突通知

施工前必须通知并确认以下冲突方：

| 通知编号 | 冲突方 | 需要确认的问题 |
|---|---|---|
| CN-001 | `agent-bus` 契约与代码 owner | `s2c-callback.v1.yaml` 和 `S2cCallbackEnvelope` 如何增加 `tenantId`。 |
| CN-002 | `agent-bus` S2C 测试 owner | `S2cCallbackEnvelopeLibraryTest` 如何更新正向/负向用例。 |
| CN-003 | `agent-service` / runtime owner | S2C envelope 构造点、schema validation、审计字段和 Run 上下文的 tenant 来源。 |
| CN-004 | `agent-execution-engine` owner | `SuspendSignal.forClientCallback(...)` 的测试夹具和注释是否需要同步。 |
| CN-005 | `agent-client` / edge owner | client callback handler、schema、审计和权限判断是否消费 `tenantId`。 |
| CN-006 | L1 文档与模板 owner | 哪些目标态文档需要改成已实现事实，哪些仍应标为迁移中。 |
| CN-007 | contract catalog / 治理模板 owner | `preferred fix / deferred` 状态需要升级为已接受迁移。 |

## 5. Stage 2 开发切片

### Slice 1：契约源更新

候选文件：

- `docs/contracts/s2c-callback.v1.yaml`
- `docs/contracts/contract-catalog.md`
- `docs/governance/templates/contract-catalog.md.j2`

要求：

- 在 request schema 中增加 required `tenantId` / `tenant_id`。
- 明确字段命名风格与现有 YAML 一致。
- 保留兼容说明：registry 绑定可以作为兼容路径，但不能替代 envelope 内 tenant scope。

### Slice 2：Java record 更新

候选文件：

- `agent-bus/src/main/java/com/huawei/ascend/bus/spi/s2c/S2cCallbackEnvelope.java`

要求：

- 增加 `String tenantId` 字段。
- compact constructor 中校验非 null、非 blank。
- Javadoc 说明 `tenantId` 是跨边界 envelope 的 required tenant scope。
- 不引入非 Java 标准库依赖。

### Slice 3：测试更新

候选文件：

- `agent-bus/src/test/java/com/huawei/ascend/bus/spi/s2c/S2cCallbackEnvelopeLibraryTest.java`

要求：

- 所有正向构造补充 `tenantId`。
- 增加 `tenantId == null` 的 negative test。
- 增加 `tenantId` blank 的 negative test。
- 保留 trace、idempotency、requestAttributes 等既有测试。

### Slice 4：调用方和文档同步

候选范围：

- `agent-service` / runtime 中的 S2C envelope 构造点。
- `agent-execution-engine` 中与 `SuspendSignal.forClientCallback(...)` 相关的测试夹具。
- `architecture/L1-High-Level-Design/agent-service/**`
- `docs/governance/templates/l1-agent-service-*.md.j2`

要求：

- 只做 S2C tenant 迁移所需的最小同步。
- 不改变 Task lifecycle、suspend/resume 所有权。
- 不引入 runtime bus、broker 或 registry 实现。

## 6. Stage 2 Harness 断言

| Assertion ID | 必须成立 |
|---|---|
| S2C-TENANT-001 | `S2cCallbackEnvelope` 必须携带 required `tenantId`。 |
| S2C-TENANT-002 | `tenantId == null` 必须失败。 |
| S2C-TENANT-003 | blank `tenantId` 必须失败。 |
| S2C-TENANT-004 | 既有 S2C required fields 仍然被校验。 |
| S2C-TENANT-005 | registry 绑定不能替代 envelope 内 tenant scope，只能作为兼容路径。 |
| S2C-TENANT-006 | 迁移不改变 `agent-service` 对 Task lifecycle 的所有权。 |

## 7. Stage 2 验证命令

首选：

```powershell
.\mvnw.cmd -pl agent-bus test
```

如果修改触碰 `agent-service` 或 `agent-execution-engine` 测试夹具：

```powershell
.\mvnw.cmd -pl agent-bus,agent-service,agent-execution-engine -am test
```

如果本地环境缺少 Java，必须记录：

- `JAVA_HOME` 状态。
- 未运行的命令。
- 需要由哪个环境补跑。

## 8. Stage 2 禁止范围

Stage 2 不得：

- 实现 agent registry / discovery runtime。
- 实现类 MQ 转发底座。
- 引入 Kafka、NATS 或其他 broker runtime。
- 修改 Task execution state owner。
- 改变 `EnginePort` 语义。
- 把 `agent-bus` 变成 agent 定义仓库。

## 9. Stage 3 / Stage 4 预告

Stage 2 完成后，再推进：

| 阶段 | 主题 | 前置问题 |
|---|---|---|
| Stage 3 | Agent 注册与发现设计/harness | registry owner、tenant 隔离、health、version、route key、一致性策略。 |
| Stage 4 | 类 MQ 转发底座设计/harness | 队列/主题模型、ack/retry、DLQ/replay、ordering/fairness、backpressure、broker 选择。 |

Stage 3 和 Stage 4 都应先生成 H2 评审包，不应直接进入 production runtime 实现。
