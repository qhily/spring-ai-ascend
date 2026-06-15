---
phase: AI-7
trigger: H2 已确认架构包，允许进入自动实现
human_review_density: light
---

# AI-7：自动实现

## 应该做什么

在已确认的版本架构信封内，按照交付视图自动修改代码、测试、配置和文档。

1. 按开发切片顺序实现
2. 按自动化投影计划使用 graphify、OpenAPI/Swagger、schema、stub/mock、contract test 或 codegen
3. 每个切片完成后运行测试和法律层检查（guard / contract test / probe / drift check）
4. 持续检查实现和生成物是否偏离版本架构信封
5. 记录实现反馈：如果代码或测试暴露架构缺口，优先用 contract test / schema / harness 锁住事实，并回写对应 A2D 文档

## 输入

| 来源 | 路径 |
|---|---|
| 交付视图 | `10-governance/delivery-projections/<version>.md` |
| 版本架构信封 | `10-governance/architecture-envelopes/<version>.md` |
| 模块设计包 | `04-modules/<module>/` |
| Harness 设计 | `08-harness/` 或模块 `harness-design.md` |
| 验证矩阵 | `09-verification/verification-matrix.md` |
| 自动化投影计划 | `10-governance/delivery-projections/<version>.md` |
| 代码结构 | 仓库源码 |

## 输出

| 产出 | 归档位置 | 状态 |
|---|---|---|
| 代码变更 | 代码仓库 | reviewed |
| 测试变更 | 代码仓库 | reviewed |
| 自动化生成物 | 版本架构信封允许的生成物路径 | reviewed |
| 文档回写 | 对应 A2D 文档 | draft |
| 实现反馈 | 交付视图、评审包或相关 L1/L2/ICD | draft |
| 实现证据 | PR 描述、CI 结果、验证矩阵 | reviewed |

## 约束

- 遵循 `CLAUDE.md` 所有工程规则
- 遵循 `change-governance.md` 的变更分级
- 不得生成脱离版本架构信封的代码
- 不得生成脱离自动化投影计划的接口文档、stub/mock、contract tests 或代码骨架
- 不得让工具生成结果反向改变模块边界、状态 owner 或契约语义
- 每个 commit 遵循 Rule D-3 预提交清单
- 不得绕过 gate、linter 或测试
- 实现发现的缺口如果仍在架构信封允许范围内，应转成代码修正、harness、schema 或 contract test，并回写实现反馈；不得把它退化为单纯“继续完善架构稿”
- 文档回写必须绑定到代码事实、测试事实、契约事实或人类裁决，不得独立扩大设计范围
- 默认拒绝：未被架构信封显式允许的路径、依赖、生成物、契约变化不得施工
- 新增或修改守护测试时，必须提供 liveness check 或 negative case，证明守护不是空检查
- 高风险切片必须遵守信封中的 owner、备份 reviewer、理解深度和吸收预算

## 何时停下问人

- 触发版本架构信封的升级条件
- 实现发现设计包有缺口或矛盾，且无法在当前架构信封内通过代码 / harness / contract test 收敛
- 自动化生成物需要写入边界外目录
- 自动化生成物和 ICD / 模块责任卡 / 状态归属矩阵语义不一致
- 测试无法通过且根因不在本切片范围内
- 发现需要改变版本架构信封的实际情况
- 法律层检查无法表达关键边界，且没有人工 owner 接管

## 反模式

- 触发升级条件后继续自动实现
- 只改代码不回写文档
- 跳过测试直接标记切片完成
- 把 Level 2/3 的实现藏在多个 Level 0 commit 里
- 手工修改生成物来掩盖契约或架构漂移
- 遇到实现细节缺口就回到纯文档阶段，而不是补最小代码骨架和 harness
- 新增守护测试但没有证明它会在违规样例下失败
- 在吸收债已经到顶时继续安排 AI 产码
