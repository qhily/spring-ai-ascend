---
phase: AI-8
trigger: AI-7 自动实现完成，或 PR 准备合并
human_review_density: light
---

# AI-8：集成验证与漂移检查

## 应该做什么

1. 汇总实现结果，对比 H2 确认的架构包
2. 检查实现是否偏离版本架构信封
3. 检查 changed files 和自动化生成物是否落在允许范围内
4. 检查 graphify / OpenAPI / Swagger / schema / codegen 结果是否仍能追溯到 accepted 架构事实
5. 检查法律层是否覆盖关键 forbidden / invariant，且每个 guard 有 liveness check 或 negative case
6. 检查验证矩阵覆盖率
7. 汇总验证通过、失败、跳过的情况
8. 生成漂移报告、吸收风险和发布风险说明
9. 提交 H3 人类审核

## 输入

| 来源 | 路径 |
|---|---|
| 版本架构信封 | `10-governance/architecture-envelopes/<version>.md` |
| 4+1 评审包 | `10-governance/review-packets/<version>.md` |
| 交付视图 | `10-governance/delivery-projections/<version>.md` |
| PR / 代码变更 | 代码仓库 |
| 自动化生成物 | 代码仓库或交付视图声明的生成物目录 |
| 测试结果 | CI 输出 |
| 验证矩阵 | `09-verification/verification-matrix.md` |

## 输出

| 产出 | 归档位置 | 状态 |
|---|---|---|
| 验证汇总 | `10-governance/review-packets/<version>.md` 的验证章节 | draft |
| 漂移报告 | 同上 | draft |
| 升级项清单 | 同上 | draft |
| 发布风险说明 | 同上 | draft |
| 基线说明草案 | `10-governance/baselines/<version>.md` | draft |

## 漂移检查维度

- 实现的模块依赖是否与模块责任卡一致
- 实现的状态流转是否与状态归属矩阵一致
- 实现的契约调用是否与 ICD 一致
- 实现的错误处理是否与契约错误语义一致
- 实现的观测埋点是否与 harness 和不变量一致
- changed files 是否全部落在版本架构信封的 writable 范围内
- 自动化生成物是否全部来自交付视图声明的投影计划
- OpenAPI/Swagger/schema 是否只是契约投影，而没有引入未被 ICD 接受的新语义
- graphify 反向抽取的代码结构是否与模块责任卡和依赖边界一致
- 未列入 allowed 的路径、依赖、契约变化、生成物是否被默认拒绝
- 守护测试是否证明自己非空虚：import non-empty、fixture mutation、negative case、wire-level probe 或等价证据
- 高风险切片是否遵守 owner、备份 reviewer、理解深度和吸收预算

## 退出条件

- 设计和实现没有未解释的漂移
- P0/P1 finding 已关闭或降级并说明理由
- 契约、状态、场景、harness 的追踪关系仍然成立
- changed files 和自动化生成物没有未解释的越界
- 需要回写的文档已更新
- 法律层覆盖了关键边界，或对无法机器检查的事项给出人工 owner 和复审条件

## 何时停下问人

- 发现重大漂移无法在边界内修复
- 关键验证无法闭环
- 发布风险超出 `risk_budget` 的可接受范围
- 法律层无法覆盖关键边界且没有人工裁决接管

## 反模式

- 只检查 happy path 的验证结果
- 把漂移当成"实现优化"而不是偏离
- 跳过文档回写直接标记验证完成
- 守护测试空集合通过却被当作有效法律层
- 只看测试绿，不检查 changed files / 依赖 / 生成物是否在白名单内
