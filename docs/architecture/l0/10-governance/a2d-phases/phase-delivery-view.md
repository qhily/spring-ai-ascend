---
phase: AI-6
trigger: AI-5 评审包已生成，与 AI-5 同时准备 H2 输入
human_review_density: light
---

# AI-6：生成交付视图

## 应该做什么

把架构信封、评审结论和必要架构事实转化为可执行的开发计划和验证计划。

1. 判定本阶段类型：decision-only / probe / code-skeleton / implementation / verification / migration / archive
2. 根据架构信封和模块设计包拆分开发切片
3. 为每个切片生成实现任务草案
4. 定义 DoD（Definition of Done）
5. 生成 harness 计划
6. 生成验证矩阵增量
7. 生成自动化投影计划
8. 记录实现反馈回灌项
9. 汇总为交付视图

## 输入

| 来源 | 路径 |
|---|---|
| 模块设计包 | `04-modules/<module>/` |
| 契约草案 | `05-contracts/` |
| 场景断言 | `02-scenarios/` |
| 不变量 | `07-invariants/` |
| 现有验证矩阵 | `09-verification/verification-matrix.md` |
| 版本架构信封 | `10-governance/architecture-envelopes/<version>.md` |
| 可用自动化工具 | graphify、OpenAPI/Swagger、schema、stub/mock、contract test、codegen 的项目约定 |

## 输出

| 产出 | 归档位置 | 状态 |
|---|---|---|
| 交付视图 | `10-governance/delivery-projections/<version>.md` | draft |
| Harness 设计更新 | `08-harness/` 或 `04-modules/<module>/harness-design.md` | draft |
| 验证矩阵增量 | `09-verification/verification-matrix.md` | draft |
| 自动化投影计划 | `10-governance/delivery-projections/<version>.md` | draft |

## 交付视图模板

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
每个切片包含：目标、可写路径、代码输出、测试/harness 输出、文档同步输出、法律层输出、风险等级、owner、吸收预算、DoD、禁止事项、验证证据。

## 4. 实现反馈回灌
| 代码或测试发现 | 影响的架构事实 | 回写位置 | 是否需要人类裁决 |
|---|---|---|---|

## 5. Harness 和验证计划
需要新增或修改的测试：unit / contract / integration / scenario / regression / ArchUnit / wire-level probe / drift check。每个守护测试必须有 liveness check 或 negative case，防止空集合 / 空断言绿灯。

## 6. 自动化投影计划
每个投影包含：来源架构事实、工具、生成物路径、可写范围、验证方式、漂移检查方式。

示例：

| 来源 | 工具 | 生成物 | 可自动提交吗 | 验证 |
|---|---|---|---|---|
| ICD-Tool-Gateway | OpenAPI/Swagger | client stub、mock、contract test | 是，但必须落在版本架构信封的 writable 范围内 | contract test + ICD semantic check |
| 模块责任卡 + 代码依赖 | graphify | dependency graph drift report | 否，只作为证据 | changed dependency graph 与模块责任卡比对 |

## 7. 下一阶段退出条件
- 可接受结果
- 不可接受结果
- 升级条件
```

完整模板和字段解释见 [`../a2d-artifact-templates.md`](../a2d-artifact-templates.md)。

## 退出条件

- 每个切片能追溯到场景、能力、模块或契约
- 每个任务有 DoD 和验证方式
- harness 计划覆盖核心状态流转和跨模块契约
- 自动化投影计划只引用 accepted 架构事实，并声明生成物路径和漂移检查方式
- 验证矩阵增量覆盖所有变更
- 非 decision-only 阶段必须包含代码输出、测试输出、harness 输出、schema 输出、wire-level probe、drift check 或 adapter skeleton 输出中的至少一项
- probe 阶段可以不进入生产代码，但必须产出可执行实证、失败复现、测试探针或可验证约束
- 文档同步只能作为约束同步、决策记录或实现反馈回灌，不能替代实现推进

## 何时停下问人

- 任务无法追溯到架构产物
- harness 无法覆盖关键失败路径
- 自动化投影需要改变模块边界、状态 owner 或契约语义
- 生成物无法限制在版本架构信封允许的文件范围内
- 阶段被写成纯文档推进，但不属于 decision-only 且没有人类裁决理由
- 高风险切片没有 owner、备份 reviewer 或吸收预算
- 守护测试无法证明自己确实看到了被守护对象

## 反模式

- 生成脱离版本架构信封的任务
- 任务缺少 DoD，无法判断完成
- harness 只覆盖 happy path
- 把 OpenAPI/Swagger 或 graphify 生成物当成新的架构事实
- 继续要求“完善架构稿”，但没有代码、harness、schema、contract test 或明确裁决输出
- 代码暴露了架构缺口后，只补说明文档，不补测试或契约约束
- 用黑名单代替白名单，导致新增路径、依赖或生成物默认漏判
- 守护测试没有 liveness guard，空集合也能绿
