---
phase: AI-6
trigger: AI-5 评审包已生成，与 AI-5 同时准备 H2 输入
human_review_density: light
---

# AI-6：生成交付视图

## 应该做什么

把架构产物转化为可执行的开发计划和验证计划。

1. 根据模块设计包拆分开发切片
2. 为每个切片生成实现任务草案
3. 定义 DoD（Definition of Done）
4. 生成 harness 计划
5. 生成验证矩阵增量
6. 生成自动化投影计划
7. 汇总为交付视图

## 输入

| 来源 | 路径 |
|---|---|
| 模块设计包 | `04-modules/<module>/` |
| 契约草案 | `05-contracts/` |
| 场景断言 | `02-scenarios/` |
| 不变量 | `07-invariants/` |
| 现有验证矩阵 | `09-verification/verification-matrix.md` |
| 版本架构边界 | `10-governance/architecture-envelopes/<version>.md` |
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

## 1. 开发切片
每个切片包含：目标、涉及模块、输入、输出、依赖、harness 断言。

## 2. 实现任务草案
每个任务包含：描述、输入、输出、DoD、验证方式、owner 候选。

## 3. Harness 计划
需要新增或修改的测试：unit / contract / integration / scenario / regression。

## 4. 自动化投影计划
每个投影包含：来源架构事实、工具、生成物路径、可写范围、验证方式、漂移检查方式。

示例：

| 来源 | 工具 | 生成物 | 可自动提交吗 | 验证 |
|---|---|---|---|---|
| ICD-Tool-Gateway | OpenAPI/Swagger | client stub、mock、contract test | 是，但必须落在版本架构边界的 writable 范围内 | contract test + ICD semantic check |
| 模块责任卡 + 代码依赖 | graphify | dependency graph drift report | 否，只作为证据 | changed dependency graph 与模块责任卡比对 |

## 5. 验证矩阵增量
新增或变化的验证行，含设计项、验证方式、证据位置。
```

## 退出条件

- 每个切片能追溯到场景、能力、模块或契约
- 每个任务有 DoD 和验证方式
- harness 计划覆盖核心状态流转和跨模块契约
- 自动化投影计划只引用 accepted 架构事实，并声明生成物路径和漂移检查方式
- 验证矩阵增量覆盖所有变更

## 何时停下问人

- 任务无法追溯到架构产物
- harness 无法覆盖关键失败路径
- 自动化投影需要改变模块边界、状态 owner 或契约语义
- 生成物无法限制在版本架构边界允许的文件范围内

## 反模式

- 生成脱离版本架构边界的任务
- 任务缺少 DoD，无法判断完成
- harness 只覆盖 happy path
- 把 OpenAPI/Swagger 或 graphify 生成物当成新的架构事实
