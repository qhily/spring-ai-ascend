---
phase: AI-3
trigger: AI-2 架构影响分析完成，影响矩阵已形成
human_review_density: heavy
---

# AI-3：生成版本架构信封

## 应该做什么

1. 根据版本意图和架构影响矩阵，生成版本架构信封草案
2. 明确 AI 可以自动推进的范围和禁止触碰的边界
3. 定义第一批代码 / harness 入口、法律层映射、验证证据和升级条件
4. 提交给 H1 人类确认
5. 确认后进入 AI-4

## 输入

| 来源 | 路径 |
|---|---|
| 版本意图 | `10-governance/version-intents/<version>.md` |
| 架构影响矩阵 | 版本意图文件的附录 |
| 现有基线约束 | `CLAUDE.md`、`07-invariants/` |

## 输出

| 产出 | 归档位置 | 状态 |
|---|---|---|
| 版本架构信封 | `10-governance/architecture-envelopes/<version>.md` | draft → H1 确认后 accepted |

## 版本架构信封模板

```yaml
version: <version>
status: draft | accepted
source_intent: 10-governance/version-intents/<version>.md

accepted_goals:
  - 本版本接受的目标
accepted_non_goals:
  - 明确不做或后续处理的目标

working_assumptions:
  - 当前允许 AI 暂时采用的假设；被代码或测试推翻后必须回灌
open_decisions:
  - id: OD-001
    question: 尚未裁决的问题
    default_path: 无裁决时是否允许推进、按哪个保守路径推进
    escalation_owner: 需要谁裁决

module_boundaries:
  writable:
    - module: 可写模块
      paths: []
      purpose: []
  read_only:
    - module: 只读模块
      reason: ""
  forbidden:
    - module: 禁止触碰模块
      reason: ""

state_ownership:
  unchanged: []
  allowed_new_state: []
  forbidden: []

contract_policy:
  allowed: []
  forbidden: []
  migration_required_when: []

dependency_policy:
  allowed: []
  forbidden: []
  purity_rules: []

legal_layer_policy:
  deny_by_default: true
  required_guards:
    - boundary: 需要守护的边界或不变量
      guard_type: ArchUnit / contract-test / wire-probe / schema-check / drift-check / rg / CI-gate / manual-review
      guard_location: 守护测试或脚本路径；人工裁决则写 owner
      liveness_check: 如何证明守护不是空检查
      fail_closed: true
  manual_only_exceptions: []

data_and_persistence_policy:
  allowed: []
  forbidden: []
  decision_needed: []

security_and_tenant_policy:
  required: []
  forbidden: []

risk_and_absorption_policy:
  risk_level: L1 / L2 / L3
  understanding_depth: []
  human_owner: []
  backup_reviewer: []
  absorption_budget:
    max_parallel_slices: 1
    stop_when: []

first_implementation_slices:
  - id: S1
    goal: 第一批应写出的代码或 harness
    code_outputs: []
    harness_outputs: []
    docs_outputs: []
    legal_layer_outputs: []
    stop_when: []

harness_and_verification:
  required_tests: []
  drift_checks: []
  evidence_required: []
  guard_liveness_required: []

automation_projection_policy:
  graphify:
    - 可以从 accepted 架构产物生成或刷新的结构图、依赖图、任务图
    - 可以从代码反向抽取用于漂移检查的结构事实
  openapi_swagger:
    - 可以从 accepted HTTP/API 契约生成的接口文档、stub、mock、contract test
    - 不得用生成结果反向改变契约语义
  codegen:
    - 可以生成的代码骨架、DTO、client、test fixture
    - 生成后允许 AI 自动修改的文件范围
drift_check_required:
  - 编码完成后必须检查的边界，例如 changed files、模块依赖、契约字段、状态 owner、测试覆盖
implementation_feedback_policy:
  must_record_when:
    - 代码发现架构信封缺口
    - harness 暴露新状态或失败路径
    - schema / code / docs 发生漂移
  allowed_feedback_outputs:
    - review packet finding
    - delivery projection 下一阶段修改意见
    - ICD / schema / L1/L2 同步
  forbidden_feedback_outputs:
    - 借回灌绕过 H1/H2 禁止范围
escalation_conditions:
  - AI 必须停下来请人类裁决的条件
```

完整模板和字段解释见 [`../a2d-artifact-templates.md`](../a2d-artifact-templates.md)。

## 自动推进范围定义方法

H1 确认的自动推进范围必须回答以下问题：

| 维度 | 必须定义什么 | 示例 |
|---|---|---|
| 目标范围 | 本版本要完成什么，不完成什么 | “支持 Tool Gateway 新增 `approvalRequired` 只读字段；不改变工具调用状态机。” |
| 模块范围 | 哪些模块可以改，哪些模块只读，哪些模块禁止触碰 | “允许改 `agent-service` 的 tool-call adapter 和 tests；`agent-bus` 只读；不得改 `gateway` 路由。” |
| 文件范围 | 哪些目录可写、可生成、只读 | “可写 `agent-service/src/**`、`docs/architecture/l0/05-contracts/**`；只读 `06-state/**`；禁止改 `00-overview/**`。” |
| 契约范围 | 哪些契约可增量变更，哪些语义不可变 | “允许 OpenAPI 增加 optional response field；不得新增 required field，不得改变 error code 语义。” |
| 状态范围 | 是否允许新增状态、改变 owner/writer、改变生命周期 | “不允许改变 Run/Task owner；只允许补充状态读取说明。” |
| 自动化工具范围 | 哪些工具可以生成什么，生成物是否可直接提交 | “可由 Swagger 生成 client stub 和 contract tests；生成后必须由 AI 对照 ICD 删除无关字段。” |
| 验证范围 | 自动实现后必须跑哪些测试和漂移检查 | “必须跑模块 unit、contract test、changed-file scope check，并更新 verification matrix。” |
| 升级条件 | 哪些情况必须停下来请人类裁决 | “发现需要改状态 owner、破坏兼容、跨出 allowed files、测试计划无法覆盖失败路径。” |

自动推进范围不是“AI 可以随便改的清单”，而是一个带禁止项、验证项、升级项和第一批实现入口的工作合同。允许项、禁止项、验证项和升级项必须同时出现；只有 allowed 没有 forbidden/escalation 的边界视为无效边界。

架构信封不要求最初的 4+1 架构稿完整到能一次性开发完成。它只需要约束第一批施工，并规定实现发现新事实时如何回灌。

架构信封默认 deny-by-default：未列入 writable / allowed / generated 的路径、依赖、契约变化和生成物默认禁止。黑名单只能作为补充说明，不能替代白名单。

关键 forbidden / invariant 必须映射到法律层。机器检查优先；无法机器检查的内容必须写清人工 owner、复审周期和触发条件。每个守护测试必须有 liveness check 或 negative case，防止空集合绿灯。

## 自动推进范围示例

### 示例 1：安全的自动推进范围

适用于 Level 0 / Level 1 的增量工作。

```yaml
accepted_goals:
  - 为 Tool Gateway 的工具调用响应增加 optional 字段 approvalRequired
accepted_non_goals:
  - 不改变工具调用状态机
  - 不改变审批流 owner
allowed_module_changes:
  - module: agent-service
    writable:
      - agent-service/src/main/java/**/tool/**
      - agent-service/src/test/java/**/tool/**
    purpose:
      - 读取 optional 字段
      - 补充单元测试和契约测试
  - module: docs-architecture
    writable:
      - docs/architecture/l0/05-contracts/**
      - docs/architecture/l0/09-verification/verification-matrix.md
forbidden_module_changes:
  - agent-bus 的 channel 语义
  - gateway 的路由和鉴权逻辑
  - 06-state 中的 owner/writer
allowed_contract_changes:
  - 在 machine-readable contract 中新增 optional response field
  - 从 ICD 派生 OpenAPI/Swagger 文档和 contract test
forbidden_contract_changes:
  - 新增 required field
  - 改变 error code、retry owner、timeout owner
state_ownership_policy:
  - 不允许改变任何状态 owner/writer
automation_projection_policy:
  openapi_swagger:
    - 允许生成 client stub、mock 和 contract test
  graphify:
    - 允许反向抽取 changed dependency graph 用于漂移检查
drift_check_required:
  - changed files 必须落在 writable 范围内
  - 生成的 OpenAPI 字段必须能追溯到 ICD
  - contract test 必须覆盖字段缺失时的兼容行为
escalation_conditions:
  - 实现需要修改 agent-bus 或 gateway
  - optional 字段无法兼容旧客户端
  - 发现审批状态 owner 需要改变
```

### 示例 2：必须人工裁决的范围

以下变更不得放入自动推进范围，必须进入 H1/H2 人类裁决：

```yaml
requested_change:
  - 将 Tool Gateway 的审批状态从 agent-service 迁移到 agent-bus
reason:
  - 改变状态 owner
  - 改变跨模块控制权
  - 影响 suspend/resume 失败路径
required_path:
  - Level 3 change
  - 更新 State Ownership Matrix
  - 更新相关 ADR / CR
  - 更新 ICD、scenario、harness、verification matrix
  - H2 人类集中审核后才能进入自动化交付层
```

## 默认升级条件

- 改变状态 owner、writer 或跨模块控制权
- 引入破坏兼容的契约变化
- 绕过已确认模块边界或依赖方向
- 需求与 `CLAUDE.md`、正式 ADR 或治理规则冲突
- 关键风险无法进入验证矩阵
- 实现结果偏离版本架构信封

## 何时停下问人

- 版本架构信封已生成，提交 H1 确认
- 0→1 场景下，人类需要先选择架构方向再确认边界

## 反模式

- 把边界设得过宽，把 Level 3 变更也放进自动推进范围
- 把边界设得过窄，连 Level 0 变更都需要人工确认
- 升级条件写得模糊，导致 AI 不知道什么时候该停下
- 只布置“完善架构稿”，没有第一批代码 / harness / schema / contract test 输出
- 把实现中暴露的问题当作“文档没写完”，而不是转成实现反馈、contract test 或升级项
- 只写 forbidden 黑名单，不写 allowed 白名单和默认拒绝策略
- 守护测试没有 liveness check，导致什么都没检查也通过
