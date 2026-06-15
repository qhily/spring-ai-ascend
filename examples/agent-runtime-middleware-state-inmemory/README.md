# Agent Runtime Agent State InMemory Example

本样例验证 OpenJiuwen Checkpointer 的 InMemory 形态。它代表短期状态或会话 checkpoint 的最小使用方式：用户启动常驻服务，通过 curl 保存、查询、释放 state。

## 启动服务

在仓库根目录执行：

```bash
./mvnw -f examples/agent-runtime-middleware-state-inmemory/pom.xml spring-boot:run
```

服务默认监听：

```text
http://localhost:18083
```

## curl 验证

1. 保存一次 state：

```bash
curl -s -X POST http://localhost:18083/sample/state/save \
  -H 'Content-Type: application/json' \
  -d '{"stateKey":"demo-state","input":"first turn","turn":1,"answer":"pong"}'
```

2. 查询 state 是否存在：

```bash
curl -s 'http://localhost:18083/sample/state/exists?stateKey=demo-state'
```

期望返回：

```json
{"stateKey":"demo-state","exists":true}
```

3. 释放 state：

```bash
curl -s -X DELETE http://localhost:18083/sample/state/demo-state
```

释放后再次查询应返回 `exists=false`。

## 设计要点

- `openJiuwenCheckpointer` Bean 在 Spring 容器启动时初始化。
- 样例通过 `OpenJiuwenCheckpointerConfigurer.setInMemoryDefault()` 把 OpenJiuwen 默认 checkpointer 设置为 InMemory。
- InMemory 适合本地开发和功能验证；生产环境通常应使用 Redis 或其他可恢复后端。
