---
level: L1
view: governance
status: draft
---

# Delivery Projections

## 目的

保存 AI-6 生成的交付视图，把架构信封、评审结论和实现反馈转化为开发切片、实现任务草案、DoD、harness 计划、测试计划、自动化投影计划和验证矩阵增量。

交付视图不是“继续完善架构稿”的任务单。除非阶段类型明确为 `decision-only`，否则每个交付视图都必须推进代码、harness、schema、contract test、wire-level probe、漂移检查或可执行 adapter skeleton 中的至少一项；文档更新只能作为约束同步、决策记录或实现反馈回灌的伴随产物。

## 适用读者

模块负责人、开发者、AI agent、harness 生成器、测试负责人。

## 维护规则

- 每个版本使用 `<version>.md` 建档。
- 每个任务必须追溯到版本架构信封、A2D 产物、实现反馈或验证矩阵。
- 任务不得越过 `architecture-envelopes/<version>.md` 的允许范围。
- 非 `decision-only` 阶段必须声明代码、harness、schema、contract test、wire-level probe、漂移检查或 adapter skeleton 输出；没有这些推进的计划必须解释为什么此阶段只能做人类裁决。
- 进入自动实现前，DoD、验证方式、harness 计划和漂移检查必须明确。
- 使用 graphify、OpenAPI/Swagger、schema、stub/mock、contract test 或 codegen 时，必须声明 source fact、生成物路径、可写范围、验证方式和漂移检查方式。
- 实现发现架构缺口时，下一阶段交付视图必须记录“实现反馈回灌”：发现来源、影响的架构事实、回写位置、是否需要人类裁决。
- 每个切片必须声明法律层输出；只写意图文档而没有 guard / contract test / probe / drift check 的切片不能作为完成态。
- 每个守护测试必须声明 liveness check 或 negative case。
- 每个高风险切片必须声明风险等级、理解深度、人类 owner、备份 reviewer 和吸收预算。

## 最小结构

```markdown
# <version> 交付视图

## 0. 阶段类型
- 类型：decision-only / probe / code-skeleton / implementation / verification / migration / archive
- 本阶段是否允许只改文档：是/否
- 如果允许，只改文档的裁决理由：

## 1. 当前审查结论
## 2. 当前修改意见
## 3. 开发切片
每个切片必须包含代码 / harness / schema / contract test / wire probe / drift check / adapter skeleton 输出之一，并声明法律层输出、owner 和吸收预算。
## 4. 实现反馈回灌
## 5. Harness 和验证计划
## 6. 自动化投影计划
## 7. 下一阶段退出条件
```

完整模板和字段解释见 [`../a2d-artifact-templates.md`](../a2d-artifact-templates.md) 的“交付视图模板”。

## 交付视图反模式

- 把“补全 4+1 架构文档”作为独立下一阶段目标，但没有对应代码、harness、contract 或裁决需求。
- 只描述理想架构，不声明可写路径、禁止范围和验证证据。
- 发现代码暴露了架构缺口，却只补说明文档，不补 contract test / schema / harness。
- 直接安排真实 DB、broker、framework 依赖，但架构信封没有许可。
- 守护测试没有 liveness guard，空集合也能绿。
- 高风险切片没有人类 owner、备份 reviewer 或吸收预算。
