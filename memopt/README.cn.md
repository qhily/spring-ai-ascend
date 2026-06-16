# MemOpt —— 企业级记忆引擎(kit)

独立的记忆引擎,`kit` 范式,**与 agent-runtime 同级**(orphan,不在根 reactor),包 `com.huawei.ascend.memopt`。两个角色:

1. **per-user 长期记忆引擎**:跨会话的用户软记忆(偏好/风险态度/对话结论);**持仓等权威数据走 SoR,不进记忆**。
2. **a2a-shared-memory 的可插拔后端**:实现该 kit 的 `SharedMemoryStore` SPI,让同样的 A2A agent / `SharedMemoryKit` **不改一行**就跑在 MemOpt 引擎上。

> MemOpt 依赖 `a2a-shared-memory`(用它的 SPI + 中立 obs),**不依赖 agent-runtime**。本模块是 MemOpt 的**进程内 Java 引擎形态**;**部署形态**(form C:on-prem 容器 + gRPC `memopt.v1` + mTLS,源码不出门)见 [ADR-0162](../docs/adr/0162-a2a-shared-memory.yaml),属后续。

## per-user 长期记忆

```java
UserMemoryKit mem = UserMemoryKit.forUser(store, MemoryScope.ofUser("bank", "u-42"));
mem.remember(List.of(new MemoryRecord("prefers short-term low-risk wealth", "preference")));
mem.recall("client preference", 5);   // 引擎挂了返回空,不抛、不拖垮主响应
mem.forget();                          // 被遗忘权,一行
```

- **scope 隔离**:`MemoryScope` 默认 `tenantId+userId`(用户记忆在其各 agent 间共享),`agentId` 可选子命名空间;单一共享存储**按 scope 分区,不是每用户一张表**。
- **fail-open + 熔断**(`resilience/Circuit`):记忆是旁路,失败 recall 返空、remember 跳过、连续失败熔断;`Options(failOpen=false)` 切严格模式。
- **成本**:批内 dedupe + 存储 `maxFactsPerScope` 上限淘汰最旧(语义蒸馏由闭源引擎做)。
- `UserMemoryStore` 后端 SPI + `InMemoryUserMemoryStore`(关键词检索 + 上限)供评测。

## 作为 A2A 共享记忆的后端

```java
// 把 A2A 共享记忆 kit 的后端从内置 in-process 换成 MemOpt 引擎,agent 无感:
SharedMemoryStore backend = new MemOptSharedMemoryStore();
SharedMemoryKit board = SharedMemoryKit.forCollaboration(backend, tenantId, contextId);
```

`MemOptSharedMemoryStore implements com.huawei.ascend.a2a.memory.shared.SharedMemoryStore`,进程内 delegate + 引擎观测;部署时把 delegate 换成 gRPC 客户端打闭源引擎,SPI 让这个替换对 agent 不可见。所有权违例/引擎错均**上抛**(交给 kit 的 fail-open / 协作 reclaim)。

## 构建 & 测试

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/openjdk-21.jdk/Contents/Home \
  ./mvnw -f a2a-shared-memory/pom.xml -DskipTests install   # 先装 SPI 依赖
JAVA_HOME=… ./mvnw -f memopt/pom.xml test
```

测试:per-user(recall/remember/forget、scope 隔离、fail-open、熔断短路、dedupe、上限淘汰)+ MemOpt 后端(a2a kit 跑在 MemOpt 上:共享/所有权/观测)。
