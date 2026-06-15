# Agent Runtime Memory Mem0 Example

本样例验证 `MemoryProvider` 对接 Mem0-compatible REST 服务的方式。用户启动一个常驻 Spring Boot 进程，通过 curl 触发写入、检索和 OpenJiuwen ReActAgent 执行链路。

该样例不内置 Mem0 服务；测试团队需要先准备一个兼容 Mem0 OSS REST API 的服务。

## 外部环境

默认配置：

```text
mem0.base-url=http://localhost:8000
mem0.api-key=
mem0.infer-on-save=false
```

如果你的 Mem0 服务地址不同，可以在启动时覆盖：

```bash
./mvnw -f examples/agent-runtime-middleware-memory-mem0/pom.xml spring-boot:run \
  -Dspring-boot.run.arguments="--mem0.base-url=http://localhost:8000 --mem0.api-key= --mem0.infer-on-save=false"
```

说明：

- `infer-on-save=false` 表示样例按测试输入直接写入，便于稳定验证。
- 如果接入真实 Mem0 推理写入能力，可改成 `true`，但验证结果会依赖 Mem0 的抽取策略。

## 启动服务

在仓库根目录执行：

```bash
./mvnw -f examples/agent-runtime-middleware-memory-mem0/pom.xml spring-boot:run
```

服务默认监听：

```text
http://localhost:18082
```

## curl 验证

1. 写入一条长期记忆：

```bash
curl -s -X POST http://localhost:18082/sample/memory/remember \
  -H 'Content-Type: application/json' \
  -d '{"stateKey":"demo-user","text":"the user prefers green tea"}'
```

2. 查询 Mem0 返回的记忆：

```bash
curl -s 'http://localhost:18082/sample/memory/search?stateKey=demo-user&query=green%20tea'
```

3. 模拟用户发起一次请求：

```bash
curl -s -X POST http://localhost:18082/sample/memory/ask \
  -H 'Content-Type: application/json' \
  -d '{"stateKey":"demo-user","text":"green tea"}'
```

期望响应：

- `modelMessages` 包含 Mem0 检索出的记忆内容。
- `records` 或 Mem0 后台数据中能看到本轮用户输入和 assistant 输出。

## 设计要点

- 样例通过 `SampleMem0OpenJiuwenHandler#setOpenJiuwenRailFactories(...)` 预设 OpenJiuwen rail。
- `Mem0RestMemoryProvider` 是 example 级适配器，用于演示 `MemoryProvider` 如何对接外部长期记忆服务。
- 本样例是面向用户视角的 daemon + curl 验证，不是单元测试替代品。
