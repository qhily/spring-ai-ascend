---
level: L1
view: governance
status: draft
---

# A2D 交付物要求与模板

## 目的

本文定义 A2D 各过程的交付物要求。核心原则是：

> 架构文档不是一次性完整蓝图，而是约束信封、决策日志、可验证契约和实现反馈的承载物。

因此，除非处在明确的候选裁决阶段，A2D 的交付视图不得只要求“继续完善架构稿”。每一轮都应推进代码、harness、schema、contract test、漂移检查或明确的决策落位；文档更新只服务于约束、验收和回灌。

A2D 使用三层事实模型：

| 层级 | 载体 | 作用 |
|---|---|---|
| 意图层 | 架构信封、ADR、评审包、L1/L2 文档 | 说明人类想要什么、允许什么、禁止什么。 |
| 法律层 | CI gate、ArchUnit、contract test、wire-level probe、schema check、drift check | 每次提交自动判定是否越界。 |
| 事实层 | graphify、OpenAPI/Swagger、schema projection、代码结构扫描 | 从代码或契约生成现状投影，帮助发现漂移。 |

模板默认采用 **deny-by-default**：没有被架构信封显式列入 allowed / writable / generated 的路径、依赖、契约变化和生成物，默认禁止。黑名单只能作为补充说明，不能替代白名单。

## 流程产物总览

| 阶段 | 产物 | 目的 | 允许的文档深度 | 必须包含代码 / harness 推进吗 |
|---|---|---|---|---|
| H0 / AI-1 | 版本意图 | 说明要做什么、为什么、不能动什么。 | 需求级，不写完整设计。 | 否。 |
| AI-2 | 影响分析 | 找出受影响模块、状态、契约、风险和未知点。 | 影响矩阵级。 | 否。 |
| AI-3 / H1 | 架构信封 | 定义自动推进边界、禁止范围、第一批代码/harness 切片和升级条件。 | 约束级，不追求完整架构。 | 否，但必须定义第一批代码/harness 入口。 |
| AI-4 | 架构事实增量 | 只补本版本必须稳定下来的模块、状态、契约、场景、不变量。 | 必要事实级。 | 视情况；不能替代交付计划。 |
| AI-5 | 评审包 | 给人类集中审阅 4+1 视图、决策、冲突、风险。 | 投影视图级，不另立事实源。 | 否。 |
| AI-6 / H2 | 交付视图 | 把信封和架构事实转成可执行切片。 | 任务级。 | 是，除非本阶段唯一目标是人类裁决；probe 阶段必须至少产出非生产实证或 harness。 |
| AI-7 | 实现与回灌 | 写代码、测试、harness；发现架构缺口并回写。 | 只回写新事实和约束。 | 是。 |
| AI-8 / H3 | 验证与漂移 | 验收实现是否仍在信封内，记录漂移、例外和风险。 | 证据级。 | 是，至少要有验证证据。 |
| AI-9 | 基线归档 | 把已接受结果固化为当前基线。 | 总结级。 | 否。 |

## 1. 版本意图模板

归档位置：`10-governance/version-intents/<version>.md`

```yaml
version_goal:
  - 本版本要达成什么
user_or_business_value:
  - 为什么现在做
functional_requirements:
  - 功能需求
non_functional_requirements:
  - 性能、可用性、隔离、安全、观测、成本等
must_keep:
  - 不能改变的模块边界、状态 owner、公开契约、兼容性要求
allowed_change_scope_hint:
  - 人类预期允许修改的模块、文档、测试范围
risk_budget:
  - 可接受风险与必须升级风险
release_bar:
  - 必须通过的验证或验收标准
open_questions:
  - 尚未澄清的问题
```

验收要求：

- 必须能区分目标、非目标、约束和开放问题。
- 不要求完整架构设计。
- 不得把“想做的实现方案”直接当成已接受边界。

## 2. 架构信封模板

归档位置：`10-governance/architecture-envelopes/<version>.md`

架构信封是 H1 的核心产物。它不是完整架构稿，而是本轮自动推进的工作合同。

```yaml
version: <version>
status: draft | accepted
source_intent: 10-governance/version-intents/<version>.md

accepted_goals:
  - 本版本接受的目标
accepted_non_goals:
  - 本版本明确不做的事情

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
      paths:
        - 可写代码或测试路径
      purpose:
        - 为什么允许写
  read_only:
    - module: 只读模块
      reason: 只能引用事实，不能修改
  forbidden:
    - module: 禁止触碰模块
      reason: 为什么禁止

state_ownership:
  unchanged:
    - state: 状态名
      owner: 当前 owner
      rule: 不允许改变 owner/writer
  allowed_new_state:
    - state: 可新增状态
      owner: owner
      writer: writer
  forbidden:
    - 不得写入或迁移的状态

contract_policy:
  allowed:
    - 可自动处理的兼容增量，例如 optional field、schema 草案、test fixture
  forbidden:
    - required field、error code 语义、timeout owner、retry owner、兼容性破坏
  migration_required_when:
    - 需要 ADR / migration / 人类裁决的条件

dependency_policy:
  allowed:
    - 可新增依赖或可使用的现有依赖
  forbidden:
    - 禁止新增的 framework、DB、broker、跨模块依赖
  purity_rules:
    - 需要 ArchUnit / rg / gate 保护的依赖边界

legal_layer_policy:
  deny_by_default: true
  required_guards:
    - boundary: 需要守护的边界或不变量
      guard_type: ArchUnit / contract-test / wire-probe / schema-check / drift-check / rg / CI-gate / manual-review
      guard_location: 守护测试或脚本路径；人工裁决则写 owner
      liveness_check: 如何证明守护不是空检查，例如 import non-empty、fixture mutation、negative case
      fail_closed: true
  manual_only_exceptions:
    - 无法机器检查的事项，必须写 owner、触发条件和复审周期

data_and_persistence_policy:
  allowed:
    - 可定义的 schema、record、repository port、migration 草案
  forbidden:
    - 未经确认不得引入的真实 DB、DDL 执行、RLS、migration 归属
  decision_needed:
    - DB 产品、migration owner、rollback 策略等

security_and_tenant_policy:
  required:
    - tenantId、auth、audit、PII、secret、isolation 等硬约束
  forbidden:
    - 跨 tenant fallback、明文 secret、绕过 auth 等

risk_and_absorption_policy:
  risk_level: L1 / L2 / L3
  understanding_depth:
    - behavior-commitment / contract-level / implementation-level
  human_owner:
    - 主 owner
  backup_reviewer:
    - 备 owner 或指定反方
  absorption_budget:
    max_parallel_slices: 允许并行吸收的最大切片数
    stop_when:
      - 吸收债到顶、review 积压、验证无法闭环等停止条件

first_implementation_slices:
  - id: S1
    goal: 第一批应写出的代码或 harness
    code_outputs:
      - 预期新增/修改的接口、record、状态机、adapter、测试替身
    harness_outputs:
      - 单测、contract test、ArchUnit、schema check、drift check
    docs_outputs:
      - 只允许同步的文档事实
    legal_layer_outputs:
      - 对应 guard、contract test、wire probe、schema check、drift check
    stop_when:
      - 触发升级条件时停止

harness_and_verification:
  required_tests:
    - 必须覆盖的 happy path / failure path / boundary path
  drift_checks:
    - changed files、依赖方向、契约字段、状态 owner、生成物来源
  evidence_required:
    - 施工智能体必须记录的验证证据
  guard_liveness_required:
    - 每个守护测试必须证明自己确实看到了要守护的对象，避免空集合 / 空断言绿灯

automation_projection_policy:
  graphify:
    allowed:
      - 可生成或反向抽取的图
    forbidden:
      - 不得用图反向改写架构语义
  openapi_swagger:
    allowed:
      - 可从 human ICD 派生的文档、stub、mock、contract test
  schema_codegen:
    allowed:
      - 可生成的 DTO、record、fixture、schema test
    writable_paths:
      - 生成物可写路径

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
  - 改变状态 owner、writer 或跨模块控制权
  - 破坏兼容契约
  - 需要修改 forbidden 模块或路径
  - 需要新增未经确认的 DB / broker / framework 生产依赖
  - harness 无法覆盖关键失败路径
  - 实现发现架构假设不成立
```

验收要求：

- 必须同时有 allowed、forbidden、verification、escalation。
- 必须定义第一批代码或 harness 入口，不能只写“完善架构文档”。
- 必须为关键 forbidden / invariant 给出法律层映射；不能机器检查的，必须写人工 owner 和复审条件。
- 必须使用 deny-by-default 白名单：未列入 allowed / writable / generated 的变化默认禁止。
- 高风险切片必须声明风险等级、理解深度、人类 owner、备份 reviewer 和吸收预算。
- 可以保留 open decisions，但必须说明默认推进路径或停止条件。
- 任何“完善架构稿”任务都必须绑定到具体冲突、契约、状态、schema 或 harness 缺口。

## 3. 评审包模板

归档位置：`10-governance/review-packets/<version>.md`

评审包是 4+1 投影视图，不是新的事实源。

```markdown
# <version> 评审包

## 1. 本轮目标和信封摘要
引用 architecture envelope，说明允许/禁止范围。

## 2. 4+1 投影视图
逻辑、开发、进程、物理、场景视图。每条关键声明必须引用来源事实。

## 3. 决策与未决问题
已决策、默认路径、待人类裁决项。

## 4. 冲突与风险
按严重度列出模块边界、状态 owner、契约兼容、依赖、数据、tenant/security 风险。

## 5. 可进入交付的最小切片
列出可以直接写代码或 harness 的切片。

## 6. 不得进入交付的事项
列出缺少裁决、缺少信封许可、缺少验证手段的事项。
```

验收要求：

- 不得维护另一套架构事实。
- 可以指出架构稿不完整，但必须把缺口转成裁决项或交付切片。
- 不得把“补完所有 4+1 视图”作为进入编码的前置条件。

## 4. 交付视图模板

归档位置：`10-governance/delivery-projections/<version>.md`

交付视图是给施工智能体的工作包。默认必须推进代码或 harness，除非本阶段明确是人类裁决阶段。

```markdown
# <version> 交付视图

## 0. 阶段类型

- 类型：decision-only / probe / code-skeleton / implementation / verification / migration / archive
- 本阶段是否允许只改文档：是/否
- 如果允许，只改文档的裁决理由：

## 1. 当前审查结论

- 可接受内容
- 不可接受内容
- 必须在下一阶段修正的问题

## 2. 当前修改意见

| 编号 | 问题 | 严重度 | 证据 | 修改意见 | 是否阻塞代码 |
|---|---|---|---|---|---|

## 3. 开发切片

每个切片必须包含：

- 目标
- 可写路径
- 代码输出
- 测试/harness 输出
- 文档同步输出
- DoD
- 禁止事项
- 验证证据
- 法律层输出：guard / contract test / wire probe / schema check / drift check
- 风险等级、理解深度、人类 owner、备份 reviewer、吸收预算

## 4. 实现反馈回灌

| 代码或测试发现 | 影响的架构事实 | 回写位置 | 是否需要人类裁决 |
|---|---|---|---|

## 5. Harness 和验证计划

- unit / contract / integration / scenario / regression / ArchUnit / drift check
- 施工智能体必须记录的命令和结果
- 如果验证不跑，必须说明原因
- 每个守护测试必须有 liveness check 或 negative case，防止空检查绿灯
- 高危交互优先使用 wire-level / end-to-end probe，而不是只用 mock 或单元桩

## 6. 自动化投影计划

| 来源事实 | 工具 | 生成物 | 可写范围 | 漂移检查 |
|---|---|---|---|---|

## 7. 下一阶段退出条件

- 可接受结果
- 不可接受结果
- 升级条件
```

验收要求：

- 非 decision-only 阶段必须包含代码输出或 harness 输出。
- probe 阶段可以不进入生产代码，但必须产出可执行实证、测试探针、失败复现或可验证约束。
- 文档同步只能作为代码/harness 的伴随产物，不能替代实现推进。
- 每个任务必须追溯到架构信封或实现反馈。
- 每个“下一阶段计划”必须明确哪些问题是当前代码发现的架构缺口。
- 默认拒绝：新增路径、依赖、生成物、契约变化若没有被信封显式允许，本阶段不得安排施工。

## 5. 实现反馈模板

实现反馈可以写在交付视图、评审包或模块 L2 文档中，但必须结构化。

```markdown
## 实现反馈

| 编号 | 发现来源 | 现象 | 架构影响 | 处理方式 | 状态 |
|---|---|---|---|---|---|
| FB-001 | code / test / harness / review | 发现了什么 | 影响哪个模块/契约/状态/依赖 | 更新文档 / 改代码 / 人类裁决 / 延后 | open / resolved |
```

反馈处理规则：

- 如果只是实现细节，直接改代码和测试。
- 如果改变契约、状态 owner、依赖边界、数据持久化或安全隔离，必须回写架构事实并可能升级。
- 如果反馈来自测试失败，必须优先补 harness / contract test，而不是只补解释性文档。

## 6. 基线说明模板

归档位置：`10-governance/baselines/<version>.md`

```markdown
# <version> 基线说明

## 1. 已接受能力
## 2. 已接受模块边界
## 3. 已接受契约和状态归属
## 4. 已接受代码 / harness / schema 事实
## 5. 已知限制和 deferred 项
## 6. 验证证据
## 7. 后续变更入口
```

验收要求：

- 基线只能记录已经接受的事实。
- deferred 项必须有 owner、触发条件和后续入口。
- 不得把草案或未跑验证的能力写成已生产可用。
