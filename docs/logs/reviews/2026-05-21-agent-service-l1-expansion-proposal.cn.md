---
level: L1
view: [scenarios, logical, process, development, physical]
module: agent-service
affects_level: L0, L1
affects_view: [scenarios, logical, process, development, physical]
status: proposed
---

# 架构评审提案：agent-service L1 领域扩展 (Wave 1.2)

> **日期:** 2026-05-21
> **作者:** LucioIT (核心架构师) & 急急 (智能体)
> **目标 Wave:** W0/W1 (立即执行)
> **关联军规:** Rule G-1.c (L1 深度与落地), Rule R-G (响应式 I/O), Rule R-M (引擎剥离)

## 1. 背景与原则 (Background & Principles)

### 1.1 顶层设计背景 (L0 架构)
本模块（agent-service）作为整体智能体生态中的核心一环，深度嵌入在 **L0 顶层设计架构**之中。L0 架构整体由 **6 大核心模块** 与 **2 种核心部署/集成模式** 构成：

#### 1.1.1 六大核心模块
1. **智能体客户端 (agent-client)**：在 SaaS 应用与桌面应用中被集成，负责感知业务知识与状态，操作业务环境与工具，下发管理智能体配置，调用执行智能体服务。
2. **智能体服务端 (agent-service)**：**（本模块核心定界）** 负责把图模式执行的 workflow 智能体与循环模式执行 of ReAct 智能体封装成微服务。
3. **智能体执行引擎 (agent-execution-engine)**：负责提供两大类智能体的执行器，提供可供开发者使用的各种组件，如 workflow 会用到的 node、ReAct 会用到的 tool 和 hook。
4. **智能体总线 (agent-bus)**：负责连接南北向的 C/S 通信流量，连接东西向 of A2A 通信流量。
5. **智能体中间件 (agent-middleware)**：负责提供智能体需要的基础服务，如记忆服务、技能服务、知识服务、沙箱服务等。
6. **智能体演进平台 (agent-evolve)**：负责在线与离线的智能体自主演进。

#### 1.1.2 两种核心部署/集成模式
- **平台中心模式 (Platform-Centric Mode)**：业务侧仅集成 `agent-client`，其他所有模块均部署在平台端（集中托管与运行，降低业务集成心智负担）。
- **业务中心模式 (Business-Centric Mode)**：业务侧不仅集成 `agent-client`，还会在本地化（业务物理边界内）部署 `agent-service` 和 `agent-execution-engine`，实现就近计算；平台侧仅提供统一治理、互联互通及基础公共服务。

### 1.2 项目阶段背景与演进规划
为了在项目孵化期平衡“交付速度”与“长远大方向”，本项目确立了明确的阶段性演进和建设重点：

- **聚焦生态与体验，暂缓高并发压力**：当前处于项目早期阶段。在确保大方向和关键架构定界正确的前提下，研发重点应聚焦于**构建良性的智能体生态与极致的开发者/用户体验**，而非在现阶段过度投入解决高并发、海量吞吐等纯系统工程问题（架构设计留出扩展余地，实现上力求轻量高效）。
- **六大模块分步建设**：
  - **核心自研优先（Client/Service/Engine）**：本阶段优先并集中实现 `agent-client`、`agent-service` 和 `agent-execution-engine` 三大核心支柱模块。
  - **成熟开源引入（Bus/Middleware）**：对于 `agent-bus`（总线）与 `agent-middleware`（中间件），本阶段主要引入业界成熟的开源技术栈（如 NATS/RabbitMQ/Redis 存储/向量库等），做轻量适配集成，拒绝闭门造车，全力保障核心链路的交付速度。
- **部署模式演进 roadmap**：
  - **当前阶段**：优先打通并完整实现**平台中心模式 (Platform-Centric Mode)**，快速跑通端到端核心用例，实现业务闭环。
  - **下一阶段**：全面落地支持**业务中心模式 (Business-Centric Mode)**。虽然该模式在本阶段暂不交付，但**当前阶段的 L1 架构与 SPI 接口设计必须前置深度考虑该模式的隔离和多态调用语义**，确保未来切换时业务零改动。

### 1.3 设计原则与核心形态
`agent-service` 在 L1 层的设计中必须严格遵循以下原则，以支撑核心的智能体形态和业务演进诉求：

#### 1.3.1 两种智能体形态的封装
1. **工作流智能体 (Workflow Agent)**：封装图模式（Graph）执行的智能体，对应确定性强、有向无环或带有复杂拓扑的分支流程。
2. **ReAct 智能体 (ReAct Agent)**：封装循环模式（Loop）执行的智能体，通过“推理-动作”闭环循环，自主选择并调用工具与钩子，处理非确定性任务。

#### 1.3.2 两种部署形态与集成调用方式（双模态）
1. **共进程函数调用 (Embedded Co-process)**：`agent-service` 与 `agent-execution-engine` 共进程部署（如同一 JVM），采用直接的方法/函数级调用。追求极低的延迟和极致的计算性能。
2. **无状态服务级调用 (Stateless Service-level)**：将智能体作为完全无状态的服务化节点运行在独立的执行引擎中。`agent-service` 作为管控层，通过 RPC、gRPC 或 A2A 总线向执行引擎下发控制指令。

#### 1.3.3 异构智能体兼容设计原则
- **向后兼容与生态解耦 (Heterogeneous Compatibility)**：支持对客户系统内现存、已在运行态的异构/存量智能体进行无缝收口。通过 `agent-service` 的服务级封装和适配器，将老系统中的智能体转化为标准服务形态，实现平滑演进与统一治理。

## 2. 场景视图 (Scenarios View)
本设计方案覆盖的核心业务运作场景如下：

### 2.1 高性能内聚运行场景 (共进程模式)
- **典型链路**：业务侧触发指令 -> 本地 `agent-service` 快速加载 -> 通过内存/函数级调用直接驱动共进程的 `agent-execution-engine` 执行计算 -> 内存传递 Delta 结果并落盘。
- **适用场景**：对响应时间极其敏感（如高频交互、本地 SaaS 辅助）且资源开销高度紧凑的边缘计算或业务中心模式。

### 2.2 异构存量智能体兼容集成场景 (服务化模式)
- **典型链路**：业务侧下发复杂决策任务 -> `agent-service` 判断当前智能体为存量 or 异构运行态实例 -> 派发器（Dispatcher）切换到服务化模式 -> 通过 A2A 总线或 RPC 调用客户自建的、异构运行的外部引擎实例 -> 接收执行状态、返回控制流。
- **适用场景**：企业级混部场景。客户已存在运行中的私有智能体，需要平滑接入统一的平台总线治理框架。

## 3. 逻辑视图 (Logical View)
实现双模态调用的核心逻辑组件设计：

### 3.1 多态派发器 (Polymorphic Dispatcher)
- 智能体调用的统一物理入口。它根据注册表配置，判断当前被调用的智能体类型 and 运行环境。
- 提供本地分支（`LocalDirectExecutor`）和服务化远程分支（`RemoteServiceExecutor`）的两路多态派发，向北向调用方屏蔽底层的部署差异。

### 3.2 引擎适配器 (Engine Adapter)
- 屏蔽 Workflow（图）与 ReAct（循环）引擎的具体执行语义，抽象出统一的无状态计算接口。
- 本地共进程运行时，直接代理 `agent-execution-engine` SDK；在服务化部署时，则封装 A2A 协议客户端与 RPC 调用代理。

## 4. 进程视图 (Process View)

## 5. 开发视图 (Development View)

## 6. 物理视图 (Physical View)
双模态集成在部署上的拓扑映射：

### 6.1 共进程内聚部署拓扑 (Embedded Deployment)
- `agent-service.jar` 与 `agent-execution-engine.jar` 作为一个进程（如一个 Pod 或边缘容器）整体打包，共享同一物理运行空间。

### 6.2 存量解耦/异构微服务部署拓扑 (Decoupled Service Deployment)
- `agent-service` 作为主管控实例集中部署，通过网络（总线/网关）连接独立的、在边缘或客户内网运行的 `agent-execution-engine` 集群或存量第三方智能体执行实例。

## 7. 附录：核心 SPI 接口 (Appendix: Core SPI Interfaces)
