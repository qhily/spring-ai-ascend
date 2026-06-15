# Agent Runtime Memory InMemory Example

本样例验证“长期记忆 MemoryProvider 解耦”的最小接入方式。用户启动一个常驻 Spring Boot 进程，通过 curl 写入记忆、发起一次用户输入，并从响应中观察记忆是否进入 OpenJiuwen ReActAgent 的真实模型输入。

该样例不依赖外部服务，适合测试团队先验证 MemoryProvider 的基本链路。

## 启动服务

在仓库根目录执行：

```bash
./mvnw -f examples/agent-runtime-middleware-memory-inmemory/pom.xml spring-boot:run
```

服务默认监听：

```text
http://localhost:18081
```

## curl 验证

1. 写入一条长期记忆：

```bash
curl -s -X POST http://localhost:18081/sample/memory/remember \
  -H 'Content-Type: application/json' \
  -d '{"stateKey":"demo-user","text":"the user prefers green tea"}'
```

2. 模拟用户发起一次请求：

```bash
curl -s -X POST http://localhost:18081/sample/memory/ask \
  -H 'Content-Type: application/json' \
  -d '{"stateKey":"demo-user","text":"green tea"}'
```

期望响应：

- `modelMessages` 包含 `the user prefers green tea`，说明 memory rail 在调用模型前完成检索和注入。
- `records` 包含本轮用户输入和 assistant 输出，说明执行后触发了 `MemoryProvider.save(...)`。

说明：本样例的 InMemory provider 使用简单字符串匹配，不做向量语义检索；因此 curl 示例使用 `green tea` 作为 query，确保测试团队能稳定观察到记忆注入。

3. 查询当前 `stateKey` 下的记忆：

```bash
curl -s 'http://localhost:18081/sample/memory/records?stateKey=demo-user'
```

## 设计要点

- 样例通过 `SampleMemoryOpenJiuwenHandler#setOpenJiuwenRailFactories(...)` 预设 OpenJiuwen rail。
- 业务方可以按同样方式把 `memoryRuntimeRail(context, provider)` 设置到自己的 OpenJiuwen handler。
- `InMemoryMemoryProvider` 只放在 example 中，用于端到端验证；生产环境应替换成企业自己的长期记忆服务。
