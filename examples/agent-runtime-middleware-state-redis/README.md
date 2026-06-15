# Agent Runtime Agent State Redis Example

本样例验证 OpenJiuwen Checkpointer 的 Redis 形态。它代表短期状态或会话 checkpoint 接入可恢复后端的方式：用户先启动 Redis，再启动样例服务，通过 curl 保存、查询、释放 state。

## 外部环境

启动前需要有可访问的 Redis：

```text
redis.host=localhost
redis.port=6379
```

可以通过启动参数覆盖：

```bash
./mvnw -f examples/agent-runtime-middleware-state-redis/pom.xml spring-boot:run \
  -Dspring-boot.run.arguments="--redis.host=localhost --redis.port=6379"
```

## 启动服务

在仓库根目录执行：

```bash
./mvnw -f examples/agent-runtime-middleware-state-redis/pom.xml spring-boot:run
```

服务默认监听：

```text
http://localhost:18084
```

## curl 验证

1. 保存一次 state：

```bash
curl -s -X POST http://localhost:18084/sample/state/save \
  -H 'Content-Type: application/json' \
  -d '{"stateKey":"demo-state","input":"first turn","turn":1,"answer":"pong"}'
```

2. 查询 state 是否存在：

```bash
curl -s 'http://localhost:18084/sample/state/exists?stateKey=demo-state'
```

期望返回：

```json
{"stateKey":"demo-state","exists":true}
```

3. 释放 state：

```bash
curl -s -X DELETE http://localhost:18084/sample/state/demo-state
```

释放后再次查询应返回 `exists=false`。

## 设计要点

- `OpenJiuwenCheckpointerConfigurer.setDefault(...)` 把 Redis checkpointer 设置为 OpenJiuwen 默认实现。
- Redis 适合验证“短期状态可恢复后端”的 wiring 方式。
- 本样例不负责启动 Redis；测试团队或客户开发团队按自己的环境准备即可。
