# MemOpt — 独立企业级记忆引擎(kit 范式)

MemOpt 是一个**独立**的企业级记忆引擎,采用 **kit 范式**(继承基类/调门面 + 填意图,横切能力当 rail 插)。它**不反依赖 agent-runtime**,定义自己的记忆契约;IP 以**闭源引擎容器**交付(形态 C),这里的开放 kit 是消费面。详见 [ADR-0162](../docs/adr/0162-a2a-shared-memory.yaml) 与设计稿 [`docs/logs/reviews/2026-06-16-memopt-...`](../docs/logs/reviews/2026-06-16-memopt-enterprise-memory-kit-design.cn.md)。

> 当前为 **Phase 1:A2A 多智能体共享记忆**,内存后端,可离线评测。后端 SPI 之后由 gRPC 引擎客户端实现(同一门面)。

## A2A 共享记忆(两层)

### 第一层 — run 内共享黑板(`shared/`)
协作进行中,多 agent 读写一块黑板。

```java
SharedMemoryKit board = SharedMemoryKit.forCollaboration(store, "demo-tenant", collaborationId);
board.put("riskAssessment", "C3", "risk-agent");   // risk-agent 拥有此 key
board.get("riskAssessment");                        // 任何参与方可读
board.history("riskAssessment");                    // 带出处的 append-log
```

- **所有权写入**:key 首次写入绑定 `writerAgentId`;**仅 owner 可写/改**,非 owner 写 → `OwnershipViolationException`(引擎侧在线上映射成 gRPC `PERMISSION_DENIED`)。交接 A→B 后,B 写自己的新 key,**A 的 key 对 B 只读**。
- **append-log + 出处**:每次写追加 `(value, writerAgentId, version, ts)`,不静默覆盖;读默认取最新,可拉全历史(可审计)。
- `SharedMemoryStore` 是后端 SPI;`InMemorySharedMemoryStore` 供评测(所有权/版本分配在每个 key 的锁内原子完成,并发安全)。

### 第二层 — 跨 run 经验(`experience/`)
协作结束沉淀"哪种模式/结论有效",供未来协作召回。

```java
ExperienceMemoryKit exp = ExperienceMemoryKit.forTenant(store, "demo-tenant");
exp.record(signature, List.of("lead with risk rating before product pitch"), "risk-agent");
exp.recall(signature, 5);   // 按签名相关性排序
```

- **签名** `CollaborationSignature = 能力组合 + 任务类型`;按 `tenantId + signature` 索引(**不按 user**);召回按 Jaccard(能力) + 任务类型匹配排序。
- **强制脱敏**:`record` 前用 `PrivacyRail`/`PiiRedactor` 剥离用户 PII(邮箱、长数字串/卡号账号、身份证、手机号)——经验是租户级共享,**绝不**带客户隐私。
- **租户隔离**:`ExperienceStore` 按 tenantId 分区。

## run-end 集成钩子(`hook/`)

`CollaborationMemoryHook` 是协作引擎在一次协作结束时调用的缝。依赖方向 **collaboration → MemOpt**(MemOpt 不 import 协作模块),所以 MemOpt 独立自建。

```java
CollaborationMemoryHook hook = new DefaultCollaborationMemoryHook(experienceKit, /*releaseBlackboard=*/true);
// 协作结束:把黑板蒸馏进经验(自动脱敏),并释放黑板
hook.onCollaborationEnd(signature, board);
```

## 构建 & 测试

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/openjdk-21.jdk/Contents/Home \
  ./mvnw -f memopt/pom.xml test
```

孤儿模块(parent = spring-boot-starter-parent,不在根 reactor),独立构建;Phase 1 **18/18** 测试通过(所有权/交接/append-log/并发、经验脱敏/召回/租户隔离、PII redactor、run-end hook)。

## 路线(ADR-0162)

1. **(本期)** A2A 共享记忆契约 + kit + 内存后端 + Coordinator hook。
2. per-user 长期记忆(从 doushuai 模式抽取,Java 化)。
3. 闭源 Java 引擎容器 + gRPC(`memopt.v1`)+ mTLS;`memopt-runtime-adapter` 接 `MemoryProvider` SPI。
