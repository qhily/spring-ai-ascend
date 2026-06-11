# spring-ai-ascend 开发者手册

英文原版: [developer-handbook.md](developer-handbook.md)（两版不一致时以英文版为准）

这是面向行业（银行侧）开发者的统一入口手册，帮助你在 `spring-ai-ascend`
上构建智能体和多智能体系统。它既是**地图，也是契约**：本手册中的每个类名、
属性、端点和代码片段都已对照源码树核实；当其他文档已经把某条流程讲清楚时，
本手册直接链接过去，而不是复制一份。

版本背景：所有 reactor 模块均为 `0.1.0-SNAPSHOT`（semver 姿态为
`experimental` / `0.x` — 见 §10）。制品尚未发布到 Maven Central；请先执行
`./mvnw -q -DskipTests install`。

**新手阅读顺序：** §1（地图）→
[quickstart](quickstart.md)（第一个可运行的智能体）→ 下文中与你任务匹配的章节。

---

## 1. 平台是什么

`spring-ai-ascend` 是一个可自托管的多租户智能体平台：一个拥有 Run 所有权的
运行时，把构建在异构框架上的智能体托管在统一的 A2A（Agent-to-Agent 协议）
表面之后；一个为运行时集群提供前置的服务化 facade；用于调用托管智能体的
客户端 SDK；以及一个 LLM 出口网关，让智能体永远不持有模型提供商凭证。

### 你站在哪里：四个概念平面

Maven 制品并**不是**架构本身 — 它们只是架构在构建层面的投影。从概念上看，
平台有四个平面，你工作在哪个平面决定了你依赖什么：

1. **托管平面 — 运行你的智能体。** 一个模块：`agent-runtime`
   （运行时宿主：引擎 SPI、Run 生命周期、A2A 入口、LLM 出口网关、可启动
   应用）。你的智能体在执行时消费的总线能力表面 — 会话记忆、知识接缝、
   进程内消息 — 实现在 `agent-bus` 中，但是**通过**运行时的执行上下文到达
   你手里；你永远不直接依赖 `agent-bus`。
2. **编写平面 — 你用什么写智能体。** `agent-sdk`：声明式
   `ascend-agent/v1` YAML 路径。可选 — 处理器也可以是直接面向引擎 SPI 的
   纯 Java。
3. **集群平面 — 为多个运行时提供前置。** `agent-service`（无 Spring 的
   facade：注册表、目录、路由授权）加上 `agent-service-starter`（它唯一的
   Spring 感知层：HTTP 控制器 + JWT 入口过滤器）。一个概念，刻意拆成两半，
   让 facade 核心保持框架无关。
4. **调用方平面 — 其他应用嵌入什么。** `springai-ascend-client`
   （Java A2A 客户端，不依赖任何平台服务端模块）加上
   `springai-ascend-client-kotlin`（习惯用法层，每个调用都委托给 Java
   facade）。一个概念，两种语言。

`spring-ai-ascend-dependencies` 不属于任何平面 — 它是 BOM，一个把上述所有
模块版本钉死的版本化制品。

### 构建层面的映射：8 个 reactor 模块

| 平面 | 模块 | 类型 | 一句话职责 |
|---|---|---|---|
| 托管 | `agent-runtime` | domain | 拥有 Run 所有权的运行时 SDK：框架中立的引擎 SPI、Run 生命周期、A2A 入口、LLM 出口网关、可启动应用 |
| 托管（经由运行时） | `agent-bus` | domain | Bus & State Hub 平面：实际可用的能力表面 `bus.memory` / `bus.knowledge` / `bus.messaging`（+ 设计冻结的 `bus.spi.*` 契约） |
| 编写 | `agent-sdk` | domain | 声明式智能体定义 SDK：`ascend-agent/v1` YAML → 可运行的处理器 |
| 集群 | `agent-service` | domain | 无 Spring 的企业服务化 facade：注册 / 发现 / 路由授权 SPI + 内存参考实现 + 字节级 A2A 转发器 |
| 集群 | `agent-service-starter` | starter | `agent-service` 的 Spring Boot 边缘：自动配置的 HTTP 控制器 + JWT 租户交叉校验过滤器 |
| 调用方 | `springai-ascend-client` | sdk | 面向外部应用的 Java A2A 客户端 SDK（无 Spring 依赖） |
| 调用方 | `springai-ascend-client-kotlin` | sdk | 架在 Java 客户端之上的 Kotlin 协程 / DSL 习惯用法层 |
| — | `spring-ai-ascend-dependencies` | bom | 物料清单（BOM），钉死所有可消费模块及 OSS 传递依赖 |

逐模块说明（对照各 `module-metadata.yaml` 与 pom 核实）：

- **`agent-runtime`** 通过单一框架中立 SPI
  （`com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler` + 可选的
  `AgentCardProvider` + `StreamAdapter` + 窄接口 `MemoryProvider`）驱动构建在
  异构智能体框架上的 Agent 实例，随附 openJiuwen、AgentScope 和 LangGraph
  适配器。它拥有引擎分发、Run 生命周期、带 `(tenant, messageId)` 幂等
  `message/send` 的 A2A 接入、OpenAI-compatible LLM 出口网关
  （`com.huawei.ascend.runtime.llm.gateway`），以及可启动的运行时应用
  （`runtime.app.RuntimeApp` / `LocalA2aRuntimeHost`）。托管智能体的应用
  必须依赖的模块只有它一个。
- **`agent-bus`** 是 Bus & State Hub 平面。它的 `bus.spi.engine` /
  `bus.spi.s2c` 包是设计冻结的契约（不被生产代码消费）；其实际可用的能力
  表面是 `bus.memory`（会话工作记忆 + 业务事实发射）、`bus.knowledge`
  （租户范围的检索接缝）和 `bus.messaging`（进程内异步智能体间消息）—
  每个都是带内存参考实现的 SPI（ADR-0163）。见 §5。
- **`agent-sdk`** 加载声明式 `ascend-agent/v1` YAML 智能体规格
  （framework、model、prompt、skills、带 file/http/mcp 引用的 tools），
  解析工具，并把规格适配为以 `AgentRuntimeHandler` 暴露的可运行 openJiuwen
  智能体。它的扩展接缝是
  `com.huawei.ascend.agentsdk.spec.tool.ToolResolver`。
- **`agent-service`** 是无 Spring 的企业服务化 facade：
  `RuntimeRegistry` 租约/TTL 状态、租户范围的 `AgentDirectory`、
  HMAC 签名的 `RouteGrantService`、内存参考实现、字节级 A2A 透传转发器
  （`service.core.RuntimeA2aGateway`），以及运行时自注册客户端
  （`service.client`）。
- **`agent-service-starter`** 是 facade 唯一的 Spring 感知层：架在 SPI 之上
  自动配置的注册 / 发现 / 路由授权 / A2A 转发 HTTP 控制器，外加服务入口处
  的 JWT 租户交叉校验过滤器（`agent-service.access.jwt.*`）。
- **`springai-ascend-client`** 是面向外部 Spring 开发者的 A2A 客户端 SDK：
  架在 OSS `a2a-java-sdk` JSON-RPC 客户端之上的无 Spring facade，内置平台的
  终态事件 / 终态后取消语义、每次调用的 W3C `traceparent` 传播 +
  `traceresponse` 关联，以及 JWT bearer + `X-Tenant-Id` 认证头。它刻意
  **不**依赖任何 reactor 兄弟模块，因此可以嵌入客户应用而不拉入平台服务端
  模块。
- **`springai-ascend-client-kotlin`** 增加 suspend 形式的
  `sendText`/`streamText` 扩展（协程取消 → 线程中断）、`ascendA2aClient {}`
  构建器 DSL，以及具名参数的 `sendSpec` 工厂。每个调用都委托给 Java
  facade；自身不含任何协议逻辑。
- **`spring-ai-ascend-dependencies`** 钉死全部七个可消费模块及 OSS 传递依赖
  版本。其声明的策略是：*所有版本都是精确补丁版本；不用范围；不用 LATEST*。

### 三类受众

| 受众 | 你是… | 你依赖 |
|---|---|---|
| **A — 智能体构建者** | 构建并托管智能体的银行侧工程团队 | `agent-runtime`（+ YAML 路径用 `agent-sdk`，+ 为集群提供前置用 `agent-service-starter`） |
| **B — 智能体调用者** | 调用托管智能体的应用团队 | `springai-ascend-client`（和/或 `-kotlin`）— 其他什么都不需要 |
| **C — 平台扩展者** | 替换参考实现的平台团队 | `agent-runtime`、`agent-service`、`agent-bus` 的 SPI 包，通过 `@Bean` 覆盖接线 |

行业开发者通常同时是 A 和 B：用 `agent-runtime` 托管自己的智能体，用客户端
SDK 调用它们（以及其他团队的智能体）。你永远不需要把 `agent-bus` 或
`agent-service` 作为直接依赖 — 它们会在相关之处以传递依赖到达。

---

## 2. 构建你的第一个智能体

**从 [quickstart §3–5](quickstart.md#3-implement-an-echo-agent) 开始：**
一个 10 行的 echo `AgentRuntimeHandler`，用
`RuntimeApp.create(handler).run(LocalA2aRuntimeHost.port(8080))` 启动，用两条
curl 验证。那覆盖了代码路径。本节覆盖 quickstart 只点到为止的**声明式 YAML
路径**。

### `ascend-agent/v1` YAML 规格

`AgentHandlerFactory.fromYaml(Path)`（包
`com.huawei.ascend.agentsdk.factory`）加载 YAML 规格并返回一个可直接使用的
`AgentRuntimeHandler`。构建器形式可额外提供网关设置和自定义工具解析器：

```java
AgentRuntimeHandler handler = AgentHandlerFactory.builder()
        .gateway("http://localhost:8080", "team-default-minted-token") // model.alias only
        .toolResolver(myCustomResolver)                                // optional extra scheme
        .fromYaml(Path.of("agent.yaml"));
```

顶层字段（对照 `AgentYamlParser` 核实）：

| 字段 | 必填 | 含义 |
|---|---|---|
| `schema` | 是 | 必须严格为 `ascend-agent/v1` |
| `name` | 是 | 智能体 id；成为处理器的 `agentId()` |
| `displayName` | 否 | 默认为 `name` |
| `description` | 是 | 面向人的描述 |
| `metadata` | 否 | 自由格式的 map |
| `cacheRoot` | 否 | 路径；为 `localCache` 物化预留 |
| `framework.type` | 是 | 目前仅 `openjiuwen` |
| `framework.agent` | 是 | `react` 或 `deepagent` |
| `framework.options` | 否 | 框架特定选项 map（如 `executeMode`、`maxIterations`） |
| `model` | 是（二选一） | 显式形式或别名形式，绝不混用（见下文） |
| `prompt.system` | 否 | System prompt 文本（默认为空） |
| `skills.sources` | 否 | 技能源目录列表（字符串简写或 `{type, path, localCache}`） |
| `tools` | 否 | 工具声明列表（见下文） |
| `mcpServers` | 否 | 具名 MCP 服务器 map（见下文） |

文件中任意位置的 `${ENV_VAR}` 占位符在解析前会从进程环境变量解析；未设置的
变量会**导致加载失败**，报错
`Environment variable is not set: <name>` — 不存在静默的空默认值。

**Model — 两种互斥形式。** 显式形式直接指名上游；别名形式把全部路由委托给
平台 LLM 网关：

```yaml
# Explicit form — you own the credential:
model:
  provider: openai-compatible    # default if omitted
  name: deepseek-chat            # required
  baseUrl: https://api.deepseek.com   # required
  apiKey: ${DEEPSEEK_API_KEY}    # required
  sslVerify: true                # default true
  headers: {}                    # optional extra headers

# Alias form — the gateway owns the credential:
model:
  alias: team-default
```

`alias` 旁残留的 `provider`/`name`/`baseUrl`/`apiKey` 键会被按名称拒绝
（否则网关会静默忽略它们）。别名形式按构建器的
`.gateway(baseUrl, mintedToken)` 值解析，缺省时回退到
`SAA_GATEWAY_BASE_URL` / `SAA_GATEWAY_TOKEN` 环境变量；生效的规格会把框架的
OpenAI-compatible 客户端指向网关的 `/v1` 表面，铸造令牌借用既有的 `apiKey`
字段传递 — 框架不需要任何代码改动。见 §6。

**Tools。** 每个条目包含：`name`（唯一 — 重复会被拒绝，名称即全局工具
注册表的键）、`description`（必填）、可选的
`inputSchema`/`outputSchema`（JSON-Schema map）、可选的 `localCache`
（布尔），以及字符串简写或 map 形式的 `ref`：

```yaml
tools:
  - name: queryOrder
    description: Look up an order.
    ref: file:com.example.QueryOrderTool#query    # Java class#method
  - name: weather
    description: Weather lookup.
    ref: http:https://api.example.com/weather     # HTTP endpoint
  - name: search
    description: Search the docs.
    ref: mcp:docs/search                          # MCP server/tool
  - name: queryOrderLong
    description: Map form of the same Java ref.
    ref:
      type: file
      class: com.example.QueryOrderTool
      method: query
```

内置解析器：`file:`（`JavaFileToolResolver` — 类路径上的类 + 方法，或相对
YAML 解析的源码文件 `path`）、`http:`（`HttpToolResolver` — 真实 HTTP
执行）、`mcp:`（`McpToolResolver` — 必须指向 `mcpServers` 中声明的服务器）。
新的 scheme 通过在构建器上注册 `ToolResolver` 实现。

**MCP 服务器。** 每个服务器恰好一种传输 — `command`（stdio）或 `url`
（HTTP/SSE）— 跨传输的键会被大声拒绝：

```yaml
mcpServers:
  local-files:                  # stdio: command (+args, +env); headers rejected
    command: npx
    args: ["-y", "@modelcontextprotocol/server-filesystem", "/data"]
    env:
      LOG_LEVEL: warn
  docs:                         # HTTP/SSE: url (+headers); args/env rejected
    url: https://mcp.example.com/sse
    headers:
      Authorization: Bearer ${MCP_TOKEN}
```

**Skills。** `skills.sources` 的条目要么是路径字符串（文件系统源，相对 YAML
解析），要么是 `{type: filesystem, path: …, localCache: …}`。技能目录由
`SkillSourceLoader` 加载并映射到 openJiuwen 智能体中。

可运行的规格和独立示例工程位于
[`examples/agent-sdk-example/`](../examples/agent-sdk-example/README.md)
（包含一个不调用模型即可验证接线的 proof 模式）。

---

## 3. 托管异构引擎

### SPI 契约

`AgentRuntimeHandler`（包 `com.huawei.ascend.runtime.engine.spi`）是引擎与
具体智能体框架之间的接缝 — 四个方法，业务逻辑中没有 Spring、没有 A2A 类型：

```java
public interface AgentRuntimeHandler {
    String agentId();
    boolean isHealthy();
    Stream<?> execute(AgentExecutionContext context);  // framework-specific results
    StreamAdapter resultAdapter();                     // maps them to neutral results
}

@FunctionalInterface
public interface StreamAdapter {
    Stream<AgentExecutionResult> adapt(Stream<?> rawResults);
}
```

`AgentExecutionResult` 恰好有四种工厂形态：`output(content)`
（中间 token/chunk）、`completed(content)`（终态回答）、
`failed(errorCode, errorMessage)`，以及 `interrupted(prompt)`（运行挂起等待
调用方输入 — 这就是在客户端表现为 `awaitingInput()` 的情形，见 §4）。引擎
拥有 Run 生命周期、任务状态和 A2A 事件映射；你的处理器从不触碰它们。

两个可选的伴生 SPI：`AgentCardProvider`（自己描述智能体的 A2A agent card，
而不是用自动生成的默认值）和 `MemoryProvider`（一个窄的 init/search/save
接缝，供希望由运行时提供记忆的框架使用）。

`AgentExecutionContext` 向处理器提供租户/会话/任务范围
（`getScope()` → `RuntimeIdentity`）、A2A 消息（`getMessages()`，用
`runtime.engine.a2a.Messages.text(message)` 提取文本）、请求变量、按任务的
智能体状态，以及 — 当托管应用接线了它们时 — 三个 agent-bus 能力表面
（`getSessionMemory()`、`getKnowledge()`、`getMessageBus()`，各为一个
`Optional`；见 §5）。

### 随附的三族适配器

| 适配器族 | 包（`com.huawei.ascend.runtime.engine.…`） | 形态 | 它期望什么 |
|---|---|---|---|
| **openJiuwen** | `openjiuwen` | 进程内：继承 `OpenJiuwenAgentRuntimeHandler`（或让 `agent-sdk` 从 YAML 构建） | 一个 openJiuwen `ReActAgent`/DeepAgent 实例；基类拥有执行流程、护栏安装、消息映射和稳定的 `conversation_id` |
| **AgentScope** | `agentscope` | 进程内（SDK 智能体用 `AgentScopeAgentRuntimeHandler`，harness 智能体用 `AgentScopeHarnessRuntimeHandler`）**或**远程（`AgentScopeRuntimeClientHandler`） | 远程形式：`new AgentScopeRuntimeClientHandler(agentId, new AgentScopeRuntimeClient(new AgentScopeRuntimeClientProperties(baseUrl)))` 对接一个 AgentScope Runtime REST/SSE 端点 |
| **LangGraph** | `langgraph` | 仅远程：`LangGraphRuntimeClientHandler` | `new LangGraphRuntimeClientHandler(agentId, new LangGraphRuntimeClient(new LangGraphRuntimeClientProperties(baseUrl, assistantId)))` 对接 LangGraph Platform 或一个 `langgraph-api` dev server |

以上全部的可工作 Spring 接线都在 e2e 示例中 — 见
[其 README](../examples/agent-runtime-a2a-llm-e2e/README.md) 和该模块中的
`LangGraphE2eConfiguration`。

### 接缝保证

当某个引擎框架升级版本时，只要你没有亲自编写框架代码，**对你来说什么都不会
变**：A2A 线上表面、Run 生命周期、租户体系、幂等性、追踪和客户端 SDK 全部
位于 `AgentRuntimeHandler`/`StreamAdapter` 的引擎中立一侧。适配器吸收框架的
方言；你的调用方无法分辨是哪个框架 — 或哪个框架*版本* — 在服务某个智能体。
远程适配器族把这一点推得更远：AgentScope Runtime 和 LangGraph 智能体跑在
它们的原生运行时中，平台只与它们的公开 HTTP/SSE API 对话。

---

## 4. 调用智能体

[Quickstart §5b](quickstart.md#5b-call-it-from-java-or-kotlin) 展示了最小的
Java 和 Kotlin 调用。契约细节如下：

### Java — `springai-ascend-client`

```java
try (AscendA2aClient client = AscendA2aClient.builder()
        .baseUrl("https://agents.example.com")
        .timeout(Duration.ofSeconds(60))                       // default 30s, whole call
        .auth(ClientAuth.jwtBearer(tokenSupplier, "bank-7"))   // optional
        .telemetry(otelTelemetry)                              // optional, closed with the client
        .build()) {

    A2aResponse reply = client.sendText(
            SendSpec.of("wealth-advisor", "session-42", "user-9", "ping"));

    if (reply.awaitingInput()) {
        // HITL: the run is suspended on input-required / auth-required.
        // reply.text() is the agent's prompt; answer it with a follow-up
        // send on the SAME sessionId.
    }
}
```

- `SendSpec(agentId, sessionId, userId, text, messageId, metadata)` — 前四个
  必填；`messageId` 为 null 时自动生成（自己设置它即可获得幂等重试，因为
  `message/send` 在服务端按 `(tenant, messageId)` 去重）；保留的路由键
  （`userId`/`agentId`/`sessionId`）不能通过 `metadata` 覆盖。
- `sendText(spec)` — 阻塞式 JSON-RPC 发送，在终态事件时返回。
- `streamText(spec)` / `streamText(spec, listener)` — SSE；阻塞直到本轮的
  结束事件，并把每个 `StreamingEventKind` 透出给监听器。
- `A2aResponse` 携带 `text()`（提取出的用户可见回答）、`events()`
  （每个原始 A2A 事件，按到达顺序）、`trace()`（`TraceCorrelation` —
  出站 `traceparent` + 服务端 `traceresponse`），以及 `awaitingInput()`
  （当运行因 `input-required`/`auth-required` 挂起而非结束时为 true）。
- `agentCard()` 获取所服务的 agent card。
- `ClientAuth.jwtBearer(Supplier<String> token)` 或
  `jwtBearer(token, tenantId)` — 令牌是一个 `Supplier`，每次调用重新求值，
  因此可直接接入轮换凭证。使用带租户的重载时会发送 `X-Tenant-Id` 头，且它
  **必须**与 JWT 的 `tenant_id` claim 一致（不一致时入口返回 403 — 见 §7）。
- 内置的平台语义：终态事件检测、终态后取消处理，以及每次调用的 W3C 追踪
  传播（默认 `TracePropagation.sampled()`；也提供 `notSampled()`）。

### Kotlin — `springai-ascend-client-kotlin`

```kotlin
val client = ascendA2aClient {
    baseUrl = "https://agents.example.com"
    auth = ClientAuth.jwtBearer({ token }, "bank-7")
}
val reply = client.sendTextSuspending(
    sendSpec(agentId = "wealth-advisor", sessionId = "session-42",
             userId = "user-9", text = "ping"))
```

`sendTextSuspending` / `streamTextSuspending` 通过 `runInterruptible` 在
`Dispatchers.IO` 上运行阻塞调用：取消协程会中断工作线程，并表现为
`CancellationException`。未设置的 DSL 属性保持 Java 构建器的默认值。

### 原始 A2A 作为兜底

任何支持 JSON-RPC 的客户端都可以不用 SDK 直接工作 — `SendMessage`
（阻塞）和 `SendStreamingMessage`（SSE）的精确 curl 请求体、agent-card 端点
`/.well-known/agent-card.json`，以及响应形态都在
[quickstart §5](quickstart.md#5-verify-with-curl)。

---

## 5. 多智能体系统

两个互补的平面：

- **跨进程**（位于不同运行时上的智能体）：经过**服务 facade** 的 A2A —
  注册、发现、签名的路由授权、字节级转发。这是唯一的跨进程智能体到智能体
  路径（A2A-NO-REWRITE：转发器从不改写载荷）。
- **进程内**（共同托管在一个运行时 JVM 上的智能体）：**agent-bus 能力
  表面** — 会话记忆、知识检索、异步消息（ADR-0163）。

### 5.1 服务 facade（跨进程）

把 `agent-service-starter` 加进任意 Spring Boot web 应用，边缘即刻生效
（`agent-service.enabled` 默认 `true`）。最小 yaml、三个控制器表面和验证用
curl 在
[quickstart §7](quickstart.md#7-front-the-runtime-with-the-service-facade)；
端点汇总：

| 控制器 | 端点 |
|---|---|
| `RuntimeRegistryController` | `POST /v1/runtime-registrations`、`PUT /v1/runtime-registrations/{runtimeInstanceId}/lease`、`DELETE /v1/runtime-registrations/{runtimeInstanceId}`、`GET /v1/agents?tenantId=…`、`GET /v1/agents/{agentId}/card?tenantId=…`、`POST /v1/agents/{agentId}/routes/resolve?tenantId=…` |
| `RouteGrantController` | `POST /v1/route-grants/resolve`、`POST /v1/route-grants/validate` |
| `A2aGatewayController` | `POST /v1/agents/{agentId}/a2a?tenantId=…`（依被转发方法返回 JSON 或 SSE） |

关键 starter 属性（`AgentServiceProperties`）：

| 属性 | 默认值 | 含义 |
|---|---|---|
| `agent-service.enabled` | `true` | 整套自动配置的总开关 |
| `agent-service.route-grant-secret` | 入库的开发默认值 | 签名 HMAC 路由授权 — **必须**按部署单独配置（§7） |
| `agent-service.public-base-url` | 空 | 设置后，所服务的 agent card 会被掩码为 `<publicBaseUrl>/v1/agents/<agentId>/a2a`，后端运行时拓扑永不泄露（`MaskedAgentDirectory`）；为空时原样返回 card |
| `agent-service.access.jwt.{enabled,hmac-secret,clock-skew-seconds}` | 禁用 / — / `30` | 服务入口的 JWT 租户交叉校验，与运行时边缘形态一致 |

每个实现都位于 `@ConditionalOnMissingBean` 之后：贡献你自己的
`RuntimeRegistry`、`AgentDirectory` 或 `RouteGrantService` bean 即可替换
内存参考实现。

**自注册 + 心跳。** 运行时用无 Spring 的 `RuntimeRegistrationClient`
（模块 `agent-service`，包 `com.huawei.ascend.service.client`）宣告自己：

```java
RuntimeRegistrationClient client = RuntimeRegistrationClient.builder(serviceBaseUrl)
        .tenantId("bank-7")
        .bearerTokenSupplier(tokenSupplier)   // when the service ingress enforces JWT
        .build();
client.register(registration);   // RuntimeAgentRegistration: instance id, tenant, agent,
                                 // card, a2aEndpoint, healthEndpoint, version, ttl,
                                 // capacity snapshot, metadata
client.startHeartbeat(...);      // periodic lease renewal keeps the instance routable
// close() deregisters.
```

租约语义（内存参考实现 `InMemoryRuntimeRegistry`）：注册后实例进入
`READY`；过期租约被观测为 `UNREACHABLE`；`AT_CAPACITY` 在查询时由容量快照
推导。完整的可运行流程是 e2e 示例中的
`RuntimeSelfRegistrationE2eTest`，记录于
[其 README](../examples/agent-runtime-a2a-llm-e2e/README.md)。

**会话亲和。** 路由解析按会话粘滞：当 `RoutingContext` 携带非空
`sessionId` 时，第一次解析把 `(tenant, agent, session)` 钉到选中的实例；
后续解析在该实例仍处于注册、租约存活且 `READY` 状态时返回它。注销和租约
过期会移除钉选；钉选 map 有上限（10 000 条，最旧的被驱逐），废弃会话不会
撑大内存。

**东西向调用。** 源运行时解析一个短期有效、HMAC 签名的
`RouteGrant`（grantId、租户、源/目标智能体、允许的方法、签名），直接调用
目标，目标校验该授权。facade 的转发器会在被转发的调用上附加
`X-Ascend-Route-Grant-Id`、`X-Ascend-Route-Grant-Signature`、
`X-Ascend-Source-Agent` 和 `X-Ascend-Tenant` 头。

### 5.2 agent-bus 能力表面（进程内）

`agent-bus` 中的三个包（每个都是带内存参考实现的 SPI），由你的处理器通过
`AgentExecutionContext` 触达 — 运行时把参考实现自动配置为
`@ConditionalOnMissingBean` bean，因此它们在每个 Spring 启动的运行时中都
存在，并可被你自己的 bean 替换：

```java
Optional<SessionMemoryStore>    memory   = context.getSessionMemory();
Optional<KnowledgeRegistry>     registry = context.getKnowledge();
Optional<AgentMessageBus>       bus      = context.getMessageBus();
Optional<BusinessFactPublisher> facts    = context.getBusinessFacts();
```

**会话记忆 — `com.huawei.ascend.bus.memory`。**

```java
public interface SessionMemoryStore {
    void append(String tenantId, String sessionId, MemoryEntry entry);
    List<MemoryEntry> window(String tenantId, String sessionId, int maxEntries); // newest first
    void clear(String tenantId, String sessionId);
}
// MemoryEntry(role, text, timestamp, attributes)
```

参考实现 `InMemorySessionMemoryStore`：按 `(tenant, session)` 的有界窗口
（默认 200 条，最旧的被驱逐）— 这是一个近期性窗口，不是档案。租户隔离是
结构性的：存储键包含租户 id，因此不存在读到其他租户窗口的代码路径。

**你必须理解的所有权规则（ADR-0051）。** 平台只拥有
*S 侧工作记忆* — 对话窗口和轨迹相关的会话状态。你的智能体在执行中发现的
业务事实（偏好、实体状态变化、任何有业务含义的东西）**发射到你的系统，
平台永不存储**：通过 `BusinessFactPublisher` SPI（处理器经
`context.getBusinessFacts()` 触达）发布一条
`BusinessFactEvent(tenantId, sessionId, runId, factType, payload,
placeholdersPreserved, occurredAt)`，由你这一侧决定接受、转换、存储还是
丢弃。仓库内的 `RecordingBusinessFactPublisher` 是一个有界的测试/示例
日志，其 `drain()` 恰好一次地交出事实 — 真实部署在该 SPI 后面插接一座
通往你系统的桥。平台不主张事实权威，载荷中的不透明身份占位符
（如 `[USER_ID_102]`）作为符号原样携带，永不在平台侧解析。

**知识接缝 — `com.huawei.ascend.bus.knowledge`。** 平台永不拥有业务知识
内容；它拥有检索接缝：

```java
public interface KnowledgeSource {
    List<KnowledgeFragment> retrieve(KnowledgeQuery query);
}
// KnowledgeQuery(tenantId, query, topK, filters)
// KnowledgeFragment(sourceId, content, score, provenance)
```

`KnowledgeRegistry` 按租户注册具名来源（重名会被大声拒绝）；
`new CompositeKnowledgeSource(registry).retrieve(query)` 把查询扇出到该
租户的全部来源，按分数降序合并，排序确定（`topK` 在合并后应用）。参考实现
`InMemoryKnowledgeSource` 在预置文档上用朴素的 token 重叠打分 — 它诚实地
承认自己只是接缝演练器，不是向量库；语义检索通过同一 SPI 插入。

**异步消息 — `com.huawei.ascend.bus.messaging`。** 共同托管在一个运行时
JVM 上的智能体的进程内平面（跨进程仍走经过 facade 的 A2A）：

```java
public interface AgentMessageBus {
    void publish(AgentMessage message);
    Subscription subscribe(String tenantId, String topic, AgentMessageHandler handler);
}
// AgentMessage(messageId, tenantId, topic, fromAgentId,
//              correlationId?, traceparent?, payload, occurredAt)
// AgentMessage.of(tenantId, topic, fromAgentId, payload) auto-generates id + timestamp
// Subscription extends AutoCloseable; droppedCount() reports queue drops
```

参考实现 `InMemoryAgentMessageBus`：主题按租户隔离（订阅者永远看不到
另一个租户的消息，即使主题名完全相同）；每个主题内投递有序（单个 daemon
分发线程）；每个订阅者拥有一个有界 FIFO 队列（默认 256），队满时丢弃
**最旧**的消息 — 丢弃在订阅上计数，首次丢弃会记录一条警告，绝不静默；
处理器异常被记录并隔离，一个坏订阅者无法破坏整个主题。总线是
`AutoCloseable`，Spring 上下文在关闭时关闭它。

**典型用法**是 `MultiAgentBusE2eTest`
（[examples/agent-runtime-a2a-llm-e2e](../examples/agent-runtime-a2a-llm-e2e/src/test/java/com/huawei/ascend/examples/a2a/MultiAgentBusE2eTest.java)）：
一个启动的 `RuntimeApp`、无 LLM — 对 "planner" 处理器的一次 A2A 调用把本轮
记入会话记忆，从一条预置的知识片段作答，并通过总线上以 `correlationId`
关联的异步请求/应答，把最终措辞委托给共同托管的 "worker"。注意它所编码的
模式：A2A 入口按设计是单处理器的，所以共同托管的辅助智能体被建模为总线
订阅者，而不是第二个对外暴露 A2A 的处理器。

内存参考实现**不**具备的东西：持久性。Broker 支撑的消息、向量知识库和
持久记忆后端是这些 SPI 后面的扩展点，不是随附代码（§9）。

---

## 6. LLM 出口网关

智能体永远不持有提供商密钥：它们用一个**铸造的、智能体范围的令牌**和一个
**模型别名**调用运行时的 OpenAI-compatible 网关
（`POST /v1/chat/completions`）；只有网关的服务端路由表知道真实的上游
URL、凭证和模型。端到端的搭建演练见
[quickstart §8](quickstart.md#8-route-agent-llm-traffic-through-the-egress-gateway)；
契约如下：

```yaml
agent-runtime:
  llm:
    gateway:
      enabled: true                  # default false — whole surface off otherwise
      connect-timeout: 5s            # default; TCP connect to upstream
      request-timeout: 120s          # default; whole exchange (buffered) /
                                     # time-to-response-headers (streaming —
                                     # the SSE relay itself is unbounded by design)
      aliases:
        team-default:                              # the "model" callers send
          base-url: https://api.example.com/v1     # OpenAI-compatible root incl. /v1
          api-key: ${UPSTREAM_API_KEY}             # real credential; explicitly empty
                                                   #   declares a no-auth upstream
          provider: openai-compatible              # telemetry label (gen_ai.system)
          upstream-model: gpt-5.4-mini             # omit to forward the alias unchanged
          pricing:                                 # omit = unpriced (cost omitted, not zeroed)
            input-per-million-tokens-usd: 0.15
            output-per-million-tokens-usd: 0.60
      tokens:
        team-default-minted-token:                 # opaque token the agent sends
          tenant-id: bank-7
          agent-id: echo-agent
```

请求处理顺序（对照 `ChatCompletionsController` 核实）：

1. **认证最先** — bearer 令牌仅根据服务端配置在 `tokens` 中解析为其预配的
   `(tenant, agent)` 身份；未知/缺失令牌在*接触任何上游之前*返回 `401`
   （未认证的调用方永不消耗提供商配额）。
2. **校验** — 非 JSON 对象的请求体 → `400`；缺少 `model` → `400`；
   `model` 不是已配置的别名 → `404`。
3. **转发** — 用真实密钥（以及设置了的话，真实的 `upstream-model`）调用
   别名对应的上游；`"stream": true` 切换为 SSE 中继。上游 `5xx` 表现为
   `502 upstream_error`；传输失败被记录并透出，绝不吞掉。
4. **计量 + 发射** — 从响应中提取用量 token，记录聚合度量（§8），向
   `SpendLog` SPI 追加一条 `SpendRecord(tenantId, agentId, modelAlias, day,
   inputTokens, outputTokens, costUsd)`（默认 `InMemorySpendLog` —
   进程内，重启不保留），并通过 `GenerationSpanSink` SPI 发射一个
   GENERATION span（存在 `OpenTelemetry` bean 时自动接线一个
   `OtelGenerationSpanSink`）。

观察者接缝（`runtime.llm.gateway.spi`：`LlmCallListener`、
`GenerationSpanSink`、`SpendLog`）是 GENERATION span 和支出记录的**唯一**
发射路径 — 实现它们即可把成本数据落到你自己的系统。

在智能体一侧，YAML 的 `model.alias` 形式（§2）就是与之匹配的客户端半边。
e2e 示例用一个开关
（`SAA_SAMPLE_LLM_VIA_GATEWAY=true` / `sample.llm.via-gateway=true`）把它的
两个框架样例同时切换到网关路径 — 线上到底变了什么，见
["Routing Sample LLM Traffic Through the Egress
Gateway"](../examples/agent-runtime-a2a-llm-e2e/README.md)。

---

## 7. 安全与多租户

### JWT 租户交叉校验 — 两个边缘同一套方案

两个入口边缘（运行时 `/a2a` 经 `A2aTenantAuthFilter`；服务 facade 的
注册/发现/授权端点经 `ServiceTenantAuthFilter`）执行同一个模型：
`Authorization: Bearer <jwt>`，其 HS256 签名必须用配置的共享密钥验证通过，
且其 `tenant_id` claim 会与请求中任何显式的租户归属交叉核对（两个边缘都看
`X-Tenant-Id` 头；服务边缘额外看 `tenantId` 查询参数）。令牌缺失/无效 →
`401`；交叉校验不一致 → `403`。两个过滤器在密钥配置之前都处于禁用状态：

| 边缘 | 属性前缀 | 过滤器作用范围 |
|---|---|---|
| 运行时 A2A | `agent-runtime.access.a2a.jwt` | `/a2a`、`/a2a/*` |
| 服务 facade | `agent-service.access.jwt` | `/v1/runtime-registrations*`、`/v1/agents*`、`/v1/route-grants*` |

两者共享 `JwtTenantValidator`（`runtime.boot`）— 一个校验器，一套语义，
两个边缘。客户端 SDK 的 `ClientAuth.jwtBearer` 生成的正是这些过滤器期望的
请求头。

> **过渡状态（ADR-0164）：** HS256 共享密钥方案是为开发/本地和单运营方
> 部署记录在案的过渡路径。生产方向是经 Spring Security 的 `JwtDecoder`
> 走 OIDC/JWKS，平台只保留租户交叉校验；待密钥基础设施落地后，它在同一
> 过滤器表面后无缝接入。

运行时入口的租户归属优先级：JWT 认证的租户（启用时）> `X-Tenant-Id` 头 >
`agent-runtime.access.a2a.default-tenant-id`（默认 `default`）。

### 租户结构性隔离 — 实际机制

隔离是结构性的（带键存储），不是"在共享查询上加过滤器"式的策略：

| 表面 | 机制 |
|---|---|
| Run 归属 | 每个 run 在其 `RuntimeIdentity` 范围内携带已解析的租户 |
| `message/send` 幂等 | 去重键是 `(tenant, messageId)` — 租户之间不可能碰撞或互相重放 |
| 会话记忆 | `InMemorySessionMemoryStore` 以 `(tenantId, sessionId)` 记录键划分窗口 |
| 知识 | `KnowledgeQuery` 携带 `tenantId`；`KnowledgeRegistry` 按租户注册来源；参考来源按租户存储文档 |
| 消息总线 | 主题键为 `(tenantId, topic)` — 不同租户的同名主题永不交叉 |
| 注册表/发现 | 候选过滤先按租户、再按智能体；`GET /v1/agents` 和路由解析都以租户为参数 |
| LLM 网关 | 铸造令牌仅根据服务端配置解析为其预配的 `(tenant, agent)` — 不信任任何调用方提供的身份 |

### 路由授权密钥纪律

`agent-service.route-grant-secret` 默认是一个**入库的、公开的**开发值。
starter 的行为（在 `AgentServiceAutoConfiguration` 中核实）：

- 默认密钥 + JWT 入口禁用 → 启动时 **WARN**：用公开默认值签名的授权不提供
  任何鉴权；把边缘暴露到本地开发之外前，请设置私有值；
- 默认密钥 + `agent-service.access.jwt.enabled=true` → 启动时 **fail-fast**
  抛出 `IllegalStateException`：配置了 JWT 的部署属于生产姿态，可伪造的
  路由授权会悄无声息地令边缘的鉴权模型失效。

---

## 8. 可观测性

### 追踪传播：`traceparent` 进，`traceresponse` 出

`TraceParentFilter`（注册在认证之前，因此即使被拒绝的请求也可关联）接受
每个入站请求上的 W3C version-00 `traceparent` — 缺失或不可解析时自行生成
trace id — 把 `trace_id` / `span_id` 放进 Logback MDC，并在每个响应上回答
`traceresponse: 00-<trace_id>-<server_span_id>-01`。不可解析的入站头计入
`springai_ascend_traceparent_invalid_total`。

### 客户端侧遥测

客户端 SDK 每次调用发送一个新的 `traceparent`，并在
`A2aResponse.trace()` 上暴露服务端的 `traceresponse`。通过
`Builder#telemetry` 插入：

- `OtelClientTelemetry` — 通过**你的** `OpenTelemetry` 实例产生 span：每个
  调用成为一个 CLIENT span `a2a send <agentId>` / `a2a stream <agentId>`，
  携带 `a2a.*`、`tenant.id`、`server.address` 属性；出站 `traceparent`
  派生自 span 上下文，因此线上追踪与本地 span 共享同一个 trace id。
- `OtlpClientTelemetry` — 当你的应用没有自己的 OTel SDK 时，一个自包含的
  OTLP/HTTP 导出器。

两者都遵循客户端 `Posture` 枚举 — 平台姿态契约在客户端侧的镜像：

| Posture | 头部采样 | 遥测中的消息文本 |
|---|---|---|
| `DEV` | 100% | 允许 |
| `RESEARCH` | 10% | 允许 |
| `PROD` | 1% | **从不设置** — PII 脱敏是结构性的（属性根本不被写入，而不是写入后再过滤） |

### 网关度量

标签只用有界词表（`model_alias`、`provider`、`outcome`、`direction`）—
绝不用基数无界的 `tenant_id`；按租户的成本问题由支出日志和 GENERATION
记录回答，不由 Prometheus 回答：

| 度量 | 类型 | 标签 |
|---|---|---|
| `springai_ascend_llm_requests_total` | counter | `model_alias`、`provider`、`outcome` |
| `springai_ascend_llm_tokens_total` | counter | `model_alias`、`provider`、`direction`（`input`/`output`） |
| `springai_ascend_llm_upstream_latency_seconds` | timer | `model_alias`、`provider` |
| `springai_ascend_llm_cost_unpriced_total` | counter | `model_alias`、`provider` |
| `springai_ascend_traceparent_invalid_total` | counter | — |

当类路径上没有 Micrometer `MeterRegistry` 时，一切以无操作（no-op）方式
运行，不做计量。

### OTel GENERATION 桥（服务端）

当启用了网关的运行时中存在 `OpenTelemetry` bean 时，
`OtelGenerationSpanSink` 把每次 LLM 调用转换为一个 CLIENT span
`chat <model>`，携带 `gen_ai.system`、`gen_ai.request.model`、
`gen_ai.usage.input_tokens`/`output_tokens`、`langfuse.cost_usd`
（未定价时整个省略 — 伪造的 0 会被读成"免费"）、`langfuse.latency_ms`，
以及强制的 `tenant.id`。该 span 会回填时间，使其持续时长与实测的上游延迟
一致，且该 sink 永不抛出异常（纯观察者接缝）。

---

## 9. 运维

### 配置参考

**运行时（`agent-runtime.*`）：**

| 属性 | 默认值 | 含义 |
|---|---|---|
| `agent-runtime.enabled` | `true` | facade 开关：在仅 facade 的部署中（例如一个仅为共享 JWT 校验器才依赖 `agent-runtime` 的 `agent-service-starter` 应用）设为 `false`，把整个 A2A 运行时内核排除出上下文 |
| `agent-runtime.access.a2a.default-tenant-id` | `default` | 没有 `X-Tenant-Id` 的请求被归属到的租户 |
| `agent-runtime.access.a2a.jwt.enabled` | `false` | A2A 入口的租户认证 |
| `agent-runtime.access.a2a.jwt.hmac-secret` | — | 共享 HS256 密钥 |
| `agent-runtime.access.a2a.jwt.clock-skew-seconds` | `30` | `exp`/`nbf` 容许的时钟偏移 |
| `agent-runtime.llm.gateway.enabled` | `false` | 整个网关表面的开关 |
| `agent-runtime.llm.gateway.aliases.<alias>.{base-url,api-key,provider,upstream-model,pricing.*}` | — | 路由表（§6） |
| `agent-runtime.llm.gateway.tokens.<token>.{tenant-id,agent-id}` | — | 铸造令牌目录 |
| `agent-runtime.llm.gateway.connect-timeout` | `5s` | 上游 TCP 连接 |
| `agent-runtime.llm.gateway.request-timeout` | `120s` | 上游响应上限（流式路径只约束到响应头） |

**服务 facade（`agent-service.*`）：** 见 §5.1 中的表格。

**智能体侧网关环境变量（由 `agent-sdk` 的 `model.alias` 消费）：**
`SAA_GATEWAY_BASE_URL`、`SAA_GATEWAY_TOKEN` — 作为
`AgentHandlerFactory` 构建器上未设置值的回退。

**示例/样例环境变量**（`SAA_SAMPLE_LLM_API_KEY`、`SAA_SAMPLE_LLM_MODEL`、
`SAA_SAMPLE_OPENJIUWEN_API_BASE`、`SAA_SAMPLE_LLM_VIA_GATEWAY`、…）只属于
e2e 示例，优先级规则记录在
["Which Environment Values Are
Effective?"](../examples/agent-runtime-a2a-llm-e2e/README.md)。

**不带 Postgres 启动：** `agent-runtime` jar 自带一个含 datasource/Flyway
键的 `application.yml`；在最小类路径上 JDBC 栈是 `<optional>` 的，这些键
处于惰性状态，但如果你的应用引入了自己的 JDBC 栈，就必须携带文档中给出的
`spring.autoconfigure.exclude` 列表 — 精确列表以及"设置它会*替换*库自身
排除项"的注意事项见
[quickstart §4](quickstart.md#booting-without-postgres)。

姿态由 `APP_POSTURE`（`dev`/`research`/`prod`）选择：`dev` 宽松
（内存后端、缺少配置时 WARN）；`research`/`prod` 在缺少必需配置时启动即
失败关闭。

### 今天哪些是内存态 — 以及替换它的 SPI

诚实的现状：每个有状态的参考实现都是进程内的，重启不保留。每个都位于
SPI + `@ConditionalOnMissingBean` 接缝之后，所以替换它只是一次 bean 覆盖，
不是平台补丁：

| 关注点 | 随附参考实现 | 替换方式 |
|---|---|---|
| Run 状态 | `InMemoryRunRepository` | `RunRepository` bean（Postgres 层是扩展点，不随附） |
| `message/send` 去重 | `InMemoryIdempotencyStore` | `IdempotencyStore` bean |
| A2A 任务存储 | `InMemoryTaskStore`（a2a-sdk） | `TaskStore` bean |
| 会话记忆 | `InMemorySessionMemoryStore` | `SessionMemoryStore` bean |
| 知识检索 | `InMemoryKnowledgeSource`（token 重叠） | 在 `KnowledgeRegistry` 上注册真实的 `KnowledgeSource`（向量库、Graphiti） |
| 智能体间消息 | `InMemoryAgentMessageBus` | `AgentMessageBus` bean（broker 支撑的传输） |
| 业务事实发射 | `RecordingBusinessFactPublisher` | 桥接到你系统的 `BusinessFactPublisher` |
| 注册表 / 发现 | `InMemoryRuntimeRegistry` | `RuntimeRegistry` / `AgentDirectory` bean |
| 路由授权 | `HmacRouteGrantService`（共享密钥 HMAC） | `RouteGrantService` bean |
| LLM 支出账本 | `InMemorySpendLog` | `SpendLog` bean |
| LLM 上游传输 | `RestClientUpstreamModelClient` | `UpstreamModelClient` bean |

密钥遵循同样的诚实原则：属性像任何 Spring 属性一样*可以*用 Vault 解析，
但平台不随附也不强制任何 Vault 集成。按能力划分的已交付/暂缓清单见
[`docs/governance/architecture-status.yaml`](governance/architecture-status.yaml)。

---

## 10. 升级与兼容性承诺

版本变动时，保护你代码的是什么：

- **引擎 SPI 接缝。** 你的业务逻辑只触碰
  `AgentRuntimeHandler`/`StreamAdapter`/`AgentExecutionContext`。当某个
  智能体框架（openJiuwen、AgentScope、LangGraph）升级时，平台的适配器吸收
  变化；你的处理器和你的调用方什么都看不到（§3）。
- **`model.alias`。** 采用别名形式的智能体不携带任何提供商 URL、凭证或
  真实模型名 — 把一个别名重新指向新的提供商或模型版本只是一次网关侧配置
  变更，智能体零重新部署（§6）。
- **A2A 线上协议。** 协议表面钉在 OSS A2A Java SDK
  （`org.a2aproject.sdk`，整个 reactor 钉同一个版本）上，且 facade 的
  转发器是字节级透传（A2A-NO-REWRITE）— 平台从不发明自己的 A2A 载荷
  序列化，所以客户端和服务端只能随 SDK 版本钉一起漂移，绝不会各自漂移。
- **BOM 单钉策略。** `spring-ai-ascend-dependencies` 把全部七个可消费模块
  钉在同一个版本，OSS 传递依赖钉精确补丁版本 — 不用范围，不用 LATEST。
  导入 BOM 后，平台升级就是一次属性版本号提升；混合版本的模块组合在结构上
  被避免。
- **可替换的 bean，而不是补丁。** §9 中的每个参考实现都是一个
  `@ConditionalOnMissingBean` 接缝：平台升级不会与你的覆盖冲突，你的覆盖
  也永远不需要 fork 平台源码。如果有什么事情需要修改平台源码才能做到，
  那是一个解耦缺陷 — 请按缺陷提报。

诚实地说，目前**尚未**承诺的：各模块为 `0.1.0-SNAPSHOT`，
`semver_compatibility: experimental`（`agent-runtime`、`agent-bus`、
`agent-sdk`）或 `0.x`（其余模块）— 公开签名在 0.x 版本之间仍可能变化，
每次发布的变更在
[`docs/logs/releases/`](logs/releases/) 中声明。被刻意跟踪的运行时契约
表面位于
[`docs/contracts/contract-catalog.md`](contracts/contract-catalog.md)；
架构记录是
[`architecture/docs/L0/ARCHITECTURE.md`](../architecture/docs/L0/ARCHITECTURE.md)。
