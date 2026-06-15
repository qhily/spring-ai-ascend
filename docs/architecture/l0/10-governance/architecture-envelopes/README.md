---
level: L1
view: governance
status: draft
---

# Architecture Envelopes

## 目的

保存 H1 确认后的版本架构信封，定义 AI 可以自动推进的范围、禁止触碰的边界、兼容性策略、验证要求、第一批代码 / harness 入口和升级条件。

架构信封不是完整架构稿。它的职责是把本轮工作约束住，让 AI 尽早进入可验证的代码骨架、测试、schema 或 contract harness；实现过程中发现的新事实再回灌到 L1/L2、ICD、schema、评审包或下一阶段交付视图。

## 适用读者

架构负责人、模块负责人、评审者、AI agent。

## 维护规则

- 每个版本使用 `<version>.md` 建档。
- 边界文件必须引用对应 `version-intents/<version>.md`。
- H1 确认后状态可以从 `draft` 提升为 `accepted`。
- 触发升级条件后，AI 必须停止当前自动推进并提交裁决项。
- 架构信封必须同时包含 allowed、forbidden、verification 和 escalation；只有 allowed 没有禁止项和升级项的边界视为无效边界。
- 架构信封必须定义第一批代码 / harness 入口；不得只写“完善架构稿”。
- “完善架构稿”只有在绑定到具体冲突、契约、状态、schema、依赖或 harness 缺口时才是有效任务。
- 架构信封采用 deny-by-default：未列入 writable / allowed / generated 的路径、依赖、契约变化和生成物默认禁止。
- 每个关键 forbidden / invariant 必须映射到法律层：机器检查优先；无法机器检查时必须指定人工 owner、触发条件和复审方式。
- 每个守护测试必须有 liveness check 或 negative case，证明它不是空集合 / 空断言绿灯。
- 高风险切片必须声明风险等级、理解深度、人类 owner、备份 reviewer 和吸收预算。

## 最小模板

```yaml
version: <version>
status: draft | accepted
source_intent: 10-governance/version-intents/<version>.md

accepted_goals: []
accepted_non_goals: []

working_assumptions: []
open_decisions:
  - id: OD-001
    question: ""
    default_path: ""
    escalation_owner: ""

module_boundaries:
  writable: []
  read_only: []
  forbidden: []

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
    - boundary: ""
      guard_type: ArchUnit / contract-test / wire-probe / schema-check / drift-check / rg / CI-gate / manual-review
      guard_location: ""
      liveness_check: ""
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
    goal: ""
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
  graphify: {}
  openapi_swagger: {}
  schema_codegen: {}

implementation_feedback_policy:
  must_record_when: []
  allowed_feedback_outputs: []
  forbidden_feedback_outputs: []

escalation_conditions: []
```

完整模板和字段解释见 [`../a2d-artifact-templates.md`](../a2d-artifact-templates.md) 的“架构信封模板”。

## 验收口径

- 可以没有完整 4+1 设计，但必须有可执行的边界。
- 可以有未决问题，但必须说明默认推进路径或停止条件。
- 可以只允许代码骨架和 harness，不必一次允许完整生产实现。
- 不得把构建工件、测试替身、BOM、starter 等当成 L0 架构模块。
- 不得让 graphify、OpenAPI/Swagger、schema 或 codegen 生成物反向修改架构语义。
