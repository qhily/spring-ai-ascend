# L1 Agent Service IEQ / Task-Control Design

## 1. Purpose

This wave keeps the IEQ and task-control surface intentionally small. It defines:

- a local in-memory queue component backed by the JDK;
- a static queue factory method for the current in-memory backend;
- a weak queue manager for registration and lookup;
- a JavaBean-style `Task` model;
- one compact `TaskControlClient` API with a single access-facing `runTask` entry and runtime `mark*` signals.

It does not define any new task-control SPI in this wave. SPI remains reserved for "this module defines the interface, another provider implements it" extension points. The current queue/task-control surface is internal API and local component code.

Current implementation notes:

- Access-facing task control remains a single `runTask(RunTaskCommand)` method.
- `RUN`, `RESUME_INPUT`, and `CANCEL` are carried by `TaskAction`, not by separate handler methods.
- No `RuntimeQueueGateway` is defined. Runtime reports state through an adapter back to `TaskControlClient.mark*`.
- `engine.spi` is provider-only: `AgentHandler` is the only SPI. Engine outbound clients live under `engine.port`, and engine internal command queue types live under `engine.queue`.

## 2. Package Layout

```text
agent-service/src/main/java/com/huawei/ascend/service/
  queue/
    InternalEventQueue.java
    InMemoryInternalEventQueue.java
    QueueFactory.java
    QueueManager.java
    QueueRegistration.java
  taskcontrol/
    Task.java
    TaskState.java
    WaitingReason.java
    TaskFailureCode.java
    TaskControlService.java
    EngineTaskControlAdapter.java
    api/
      TaskControlClient.java
```

## 3. Queue Component

`InternalEventQueue<T>` is a thin local abstraction over queue operations:

```java
public interface InternalEventQueue<T> {
    String queueId();
    boolean offer(T value);
    Optional<T> poll();
    Optional<T> peek();
    Optional<T> find(Predicate<? super T> matcher);
    List<T> snapshot();
    int size();
}
```

The default implementation is `InMemoryInternalEventQueue<T>`, backed by `java.util.concurrent.LinkedBlockingQueue`.

`QueueFactory` is a final utility class, not an SPI:

```java
public final class QueueFactory {
    public static <T> InternalEventQueue<T> inMemoryQueue(String queueId) { ... }
}
```

The queue does not own task state and does not inspect item payload type. The queue also does not expose a runtime gateway. Runtime code must not publish to or consume from IEQ directly.

## 4. Task Model

`Task` is a mutable JavaBean-style model with a no-arg constructor and standard getters/setters. The first implementation wave keeps it simple so later persistence, serialization, and controller code can reuse the same shape.

Core fields:

- `tenantId`
- `sessionId`
- `taskId`
- `agentId`
- `state`
- `revision`
- `waitingReason`
- `failureCode`
- `detail`
- `createdAt`
- `updatedAt`

`TaskState` contains:

```text
CREATED, RUNNING, WAITING, PAUSED, CANCELLING, COMPLETED, FAILED, CANCELLED
```

No `QUEUED`, `WAITING_FOR_TOOL`, or `EXPIRED` state is defined in this wave.

## 5. Control API

`TaskControlClient` is the internal task-control API in this wave. It is not registered as SPI because the current implementation direction is not "external provider implements the contract"; task-control owns the control service and callers invoke it.

```java
public interface TaskControlClient {
    CompletionStage<TaskResult> runTask(RunTaskCommand command);
    CompletionStage<TaskResult> markRunning(MarkTaskCommand command);
    CompletionStage<TaskResult> markWaiting(MarkTaskCommand command);
    CompletionStage<TaskResult> markSucceeded(MarkTaskCommand command);
    CompletionStage<TaskResult> markFailed(MarkTaskCommand command);
    CompletionStage<TaskResult> markCancelled(MarkTaskCommand command);
}
```

`RunTaskCommand` carries a `TaskAction` enum:

```text
RUN, RESUME_INPUT, CANCEL
```

This keeps the access `TaskHandler` shape to one method. Future control intent can extend the enum and command fields without adding another access-facing method.

Command/result records live inside `TaskControlClient`:

- `RunTaskCommand`
- `TaskAction`
- `MarkTaskCommand`
- `TaskResult`

This keeps the public interface count small while preserving typed command/result semantics.

## 6. Runtime Alignment

`EngineDispatchApi` is the runtime/engine dispatch reference. Task-control calls it to enqueue execute/resume/cancel requests.

This wave does not add a second runtime dispatch SPI. Runtime and engine report state changes back through an engine outbound port implemented by `EngineTaskControlAdapter`, which then calls `TaskControlClient.mark*`.

`agentId` validation is primarily an access/entry responsibility. Access must reject blank `agentId`, and any registry/card/authorization check should happen before task-control. Task-control trusts that entry contract and packages the `agentId` into the engine scope without depending on an agent registry. Engine dispatch may still reject unavailable targets as a defensive fallback, but that is not the normal validation path.

The only engine provider SPI is `AgentHandler`; examples should implement `AgentHandler` instead of introducing another example interface.

## 7. White-Box Test Scope

The current test wave pins:

- queue manager registration and session lookup;
- task-control task creation, current-task selection, idempotency, fencing, and state transitions;
- engine bridge conversion from engine events into task-control marks.

White-box tests live under `agent-service/src/test/java/com/huawei/ascend/service/taskcontrol/test`.

## 8. Deferred

- durable queue backend;
- optional TaskStore or index for durable backends;
- OOD / `NOT_CURRENT_TASK` policy;
- broader integration tests across access, session, task-control, and runtime.
