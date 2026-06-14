---
level: L1
view: governance
status: draft
---

# Review Packets

## 目的

保存 AI-5 生成、供 H2 审核的 4+1 架构评审包，避免评审者必须逐个阅读全部 A2D 原始产物。

## 适用读者

架构负责人、业务负责人、模块负责人、评审者。

## 维护规则

- 每个版本使用 `<version>.md` 建档。
- 新评审包从 [`_TEMPLATE.md`](_TEMPLATE.md) 复制结构，不得从空白文档开始。
- 评审包不得维护另一套事实，必须引用场景、能力图、模块卡、状态矩阵、ICD、不变量、harness 或验证矩阵。
- 开放问题必须有 owner 或后续位置。
- 4+1 视图中的元素和关系必须出现在结构化表格中，供可视化、graphify 和漂移检查复用。
- OpenAPI/Swagger、schema、stub、mock、contract test 或 codegen 只能作为契约和架构事实的投影，不得反向成为语义事实源。
- Harness 和测试计划必须以 assertion 为主键，说明 source fact、must hold、test type、failure path 和 evidence。
- 自动推进范围必须同时声明 allowed、forbidden、check method、evidence 和 escalation target。
- H3 前可以追加验证汇总、漂移报告、升级项和发布风险说明。

## 最小结构

```markdown
# <version> 架构审核包

## 1. Review Summary
## 2. Source Facts
## 3. 4+1 View Model
## 4. Contract Projection Matrix
## 5. Harness Assertions And Test Plan
## 6. Automation Boundary
## 7. Automation Projection Plan
## 8. Delivery Readiness
## 9. Open Issues And H2 Decision
```
