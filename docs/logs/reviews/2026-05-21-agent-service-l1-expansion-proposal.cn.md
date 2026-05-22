---
level: L1
view: [logical, development, process, scenarios]
module: agent-service
affects_level: L0, L1
affects_view: [logical, process, scenarios, development]
status: proposed
---

# 架构评审提案：agent-service L1 领域扩展 (Wave 1.2) - 大纲

> **日期:** 2026-05-21
> **作者:** LucioIT (核心架构师) & 急急 (智能体)
> **目标 Wave:** W0/W1 (立即执行)
> **关联军规:** Rule G-1.c (L1 深度与落地), Rule R-G (响应式 I/O), Rule R-M (引擎剥离)

## 1. 逻辑视图 (Logical View)
- **多态派发器 (Polymorphic Dispatcher)**：本地函数直连 vs 远程 A2A 总线调用。
- **响应式协调器 (Reactive Orchestrator)**：核心调度，基于 A2A 封装 Tick 并处理 Yield。
- **任务中心 (Task Center)**：逻辑持久化，仅管理控制状态与 A2A 状态机。
- **会话管理器 (Session Manager)**：维护数据上下文（Context Data）与临时变量。
- **上下文投影仪 (Context Projector)**：根据当前 Task 语义动态投影出最相关的 Context。
- **执行引擎适配器 (Engine Adapter)**：为 Workflow（图）与 ReAct（循环）模式提供无状态计算接口。

## 2. 进程视图 (Process View)
- **无状态计算闭环 (Stateless Loop)**：准备数据 -> 执行计算 -> 产出增量 + 挂起信号 -> 脱水存储并释放线程。
- **响应式多态背压 (Polymorphic Backpressure)**：本地 Push (Reactor Sinks) 与云端 Pull (agent-bus) 背压流控。
- **非阻塞挂起机制 (Non-blocking Yielding)**：废除基于异常的 Suspend 原理，引擎直接抛出 Yield 原语。

## 3. 开发视图 (Development View)
```text
agent-service/src/main/java/com/huawei/ascend/agent/service/
├── api/                        # 北向入口，处理 REST/gRPC/A2A-Server
├── dispatcher/                 # [组件1] 实现多态模式切换逻辑
├── orchestrator/               # [组件2] 集成 a2a-java SDK 的核心调度器
├── task/                       # [组件3] 任务控制状态库 (映射 TaskStore)
├── session/                    # [组件4/5] 会话管理与投影逻辑
│   └── projection/             # 核心投影算法：语义切片与注入上下文生成
├── engine/                     # [组件6] 执行引擎适配层
│   ├── workflow/               # 图执行适配
│   ├── react/                  # 循环执行适配
│   └── spi/                    # [军规] 核心计算接口定义 (StatelessEngine)
└── infrastructure/             # 中间件适配 (Bus, Persistence, Identity)
```

## 4. 场景视图 (Scenarios View)
- **单次短程任务 (Task is Session)**：即用即销毁。
- **长程会话跨任务协作**：动态提取历史增量，写回增量。

## 5. 设计要点深度分解 (Deep-Dive Points)
- **A2A 标准化**：以 A2A `TaskState` 实现跨平台协同。
- **四层生命周期定界**：Run (瞬时) ≤ Task (控制) ≤ Session (数据) ≤ Memory (长程知识)。
- **无状态注入协议**：引擎严禁直接访问 I/O 或 DB，所有数据完全显式由 Service 喂入。
- **任务与数据解耦**：Task 管“做”，Session 管“记”，支持多任务动态挂载。

## 6. 附录：核心 SPI 接口 (Appendix: Core SPI Interfaces)
- `StatelessEngineExecutor`: `Mono<StateDelta> execute(TaskSpec task, InjectedContext ctx)`
- `ContextProjector`: `InjectedContext project(SessionID sid, TaskID tid)`
- `ReactiveOrchestrator`: `Flux<TaskEvent> dispatch(AgentInvokeRequest req)`
