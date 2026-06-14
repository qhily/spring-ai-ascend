---
phase: AI-5
trigger: AI-4 产物已批量生成，准备提交 H2 审核
human_review_density: light
---

# AI-5：生成 4+1 评审视图

## 应该做什么

把 AI-4 生成的架构产物汇总为人类可审阅的架构包。这个包是 H2 的直接输入。

1. 从已接受的 A2D 产物中提取逻辑、开发、进程、物理和场景视图
2. 生成结构化视图元素和关系表，支撑 4+1 可视化、graphify 和漂移检查
3. 生成决策摘要：本版本做了哪些架构决策
4. 汇总变更级别和影响面
5. 列出开放问题和需要人类裁决的例外
6. 生成契约投影矩阵，说明 ICD 如何派生 OpenAPI/Swagger、schema、stub、mock 或 contract test
7. 生成 harness assertions 和测试计划
8. 生成 H2 自动化边界摘要和交付准备度检查

## 输入

| 来源 | 路径 |
|---|---|
| AI-4 全部产出 | `01-capabilities/` ~ `09-verification/` |
| 版本架构边界 | `10-governance/architecture-envelopes/<version>.md` |
| 上一次基线 | `10-governance/baselines/` 最新版本 |
| 评审流程 | `10-governance/architecture-review-process.md` |
| 评审包模板 | `10-governance/review-packets/_TEMPLATE.md` |

## 输出

| 产出 | 归档位置 | 状态 |
|---|---|---|
| 4+1 评审视图包 | `10-governance/review-packets/<version>.md` | draft |

## 评审包模板

新评审包必须使用 `10-governance/review-packets/_TEMPLATE.md`。

模板中的硬结构不可删除：

| 结构 | 用途 |
|---|---|
| Source Facts | 防止评审包维护另一套事实 |
| View Elements / View Relationships | 支撑 4+1 可视化、graphify 和漂移检查 |
| Contract Projection Matrix | 约束 OpenAPI/Swagger/schema 等投影不得反向成为事实源 |
| Harness Assertions And Test Plan | 支撑 harness 和测试计划生成 |
| Automation Boundary | 定义 H2 后 AI 能自动推进的范围和检查方法 |
| Automation Projection Plan | 定义工具生成物、写入路径、验证方式和漂移检查 |
| Delivery Readiness | 判断是否可以进入 AI-7 自动实现 |
| H2 Decision | 记录人类最终裁决、残余风险和下一步产物 |

## 退出条件

- 评审包覆盖了所有受影响模块
- 每个变更都有对应的验证行
- 每个关键 4+1 元素和关系都有 source fact
- 每个跨模块契约都有投影规则或明确说明不投影
- 每个关键设计项都有 harness assertion 或未覆盖说明
- 自动化边界包含 allowed、forbidden、check method、evidence 和 escalation target
- 开放问题都有 owner 或后续位置
- 需要裁决的例外已明确列出

## 何时停下问人

- 评审包生成完毕，提交 H2

## 反模式

- 评审包维护另一套事实（应直接引用 A2D 产物）
- 只写变更，不写整体视图（人类需要看到完整画面）
- 把 Level 3 变更藏在 Level 1 列表里
- 只写自然语言 4+1，不提供元素和关系表
- 把 OpenAPI/Swagger 或 graphify 结果当成契约语义源
- 只给 happy path 测试计划，不列 failure path
