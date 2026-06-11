# agent-runtime 远端 A2A OpenJiuwen E2E 示例

这个示例启动两个 `agent-runtime`：

- AgentB：远端 runtime，先流式返回两条消息，然后返回 `INPUT_REQUIRED`；下一轮用户输入后再流式返回两条消息并 `COMPLETED`。
- AgentA：本地 OpenJiuwen 0.1.12 agent，由 runtime 发现 AgentB 的 A2A card，并把 AgentB 注入成一个远端 tool。示例主路径用确定性 handler 产生与 OpenJiuwen remote rail 等价的 remote interrupt marker，然后由 runtime 使用 A2A client 流式调用 AgentB。

主验证路径是确定性的，不依赖真实大模型，也不把真实 LLM 是否选择 tool 作为稳定验收前提。`agent-runtime` 的 OpenJiuwen 单元测试覆盖 ToolCard / placeholder Tool / remote rail 注入与 rail interrupt；本示例聚焦两个 runtime 之间的真实 A2A 出站、远端 `INPUT_REQUIRED` 续写和远端完成后 resume 本地 OpenJiuwen。

## 覆盖范围

这个 example 覆盖的是当前远端 A2A tool invocation 的端到端主链路：

- AgentA 启动后按 `agent-runtime.remote-agents[0].url` 读取 AgentB 的 A2A card。
- Runtime 根据 AgentB card 生成远端 tool spec，并在 AgentA 的 OpenJiuwen handler 生命周期里安装 runtime tool。
- AgentA 产生远端 tool 调用意图后，runtime 不让 placeholder tool 真执行，而是通过 A2A client 调用 AgentB。
- AgentB 通过 streaming A2A 返回两条进度消息，然后返回 `TASK_STATE_INPUT_REQUIRED`。
- AgentA 的 parent task 写入远端路由 metadata，并把 AgentB 的追问返回给用户。
- 第二轮用户请求携带同一个 parent `taskId` / `contextId` 后，runtime 先续写同一个远端 AgentB task。
- AgentB 完成后，runtime 把远端结果作为 OpenJiuwen `InteractiveInput` resume 给 AgentA，AgentA 最终 `COMPLETED`。

这个 example 不覆盖真实 LLM 是否会自主选择远端 tool；该部分由 OpenJiuwen remote tool installer / rail 的单元测试覆盖。它也不覆盖 push notification，当前手工路径使用 `SendStreamingMessage` 验证 streaming。

## 启动服务

推荐用两个独立 PowerShell 窗口前台启动，方便观察日志，也避免后台 shell 退出后进程被回收。

先打包：

```powershell
cd D:\Code\spring-ai-ascend
mvn -f examples\agent-runtime-a2a-remote-openjiuwen-e2e\pom.xml package -DskipTests
```

窗口 1 启动 AgentB：

```powershell
cd D:\Code\spring-ai-ascend
java -jar examples\agent-runtime-a2a-remote-openjiuwen-e2e\target\agent-runtime-a2a-remote-openjiuwen-e2e-example-0.1.0-SNAPSHOT.jar `
  --server.port=18082 `
  --sample.remote-openjiuwen.role=b
```

窗口 2 启动 AgentA：

```powershell
cd D:\Code\spring-ai-ascend
java -jar examples\agent-runtime-a2a-remote-openjiuwen-e2e\target\agent-runtime-a2a-remote-openjiuwen-e2e-example-0.1.0-SNAPSHOT.jar `
  --server.port=18081 `
  --sample.remote-openjiuwen.role=a `
  --agent-runtime.remote-agents[0].url=http://localhost:18082
```

也可以用 Maven 直接启动。先启动 AgentB：

```powershell
cd D:\Code\spring-ai-ascend
mvn -f examples\agent-runtime-a2a-remote-openjiuwen-e2e\pom.xml spring-boot:run `
  "-Dspring-boot.run.arguments=--server.port=18082 --sample.remote-openjiuwen.role=b"
```

再启动 AgentA：

```powershell
cd D:\Code\spring-ai-ascend
mvn -f examples\agent-runtime-a2a-remote-openjiuwen-e2e\pom.xml spring-boot:run `
  "-Dspring-boot.run.arguments=--server.port=18081 --sample.remote-openjiuwen.role=a --agent-runtime.remote-agents[0].url=http://localhost:18082"
```

启动后可以先确认两个 card 都可访问：

```powershell
curl.exe http://localhost:18082/.well-known/agent-card.json
curl.exe http://localhost:18081/.well-known/agent-card.json
```

AgentA 对远端 card 是后台重试发现，成功后不会继续刷新。启动 AgentA 后建议等几秒，直到 AgentA 窗口看到类似 `installed 1 remote A2A tool(s)` 的日志，再发送第一次请求。

## 第一次请求：触发 AgentA 调用 AgentB

```powershell
$body = @{
  jsonrpc = '2.0'
  id = 'remote-openjiuwen-1'
  method = 'SendStreamingMessage'
  params = @{
    message = @{
      role = 'ROLE_USER'
      messageId = 'msg-a-1'
      contextId = 'ctx-a-1'
      metadata = @{
        userId = 'manual-user'
        agentId = 'local-a'
      }
      parts = @(@{ text = '请调用远端 AgentB 做流式 input required 演示' })
    }
  }
} | ConvertTo-Json -Depth 12

$first = curl.exe http://localhost:18081/a2a `
  -H "Content-Type: application/json" `
  -H "Accept: text/event-stream" `
  -d $body

$first
```

成功现象：

- SSE 中先看到 AgentB 的两条远端流式消息，通常体现为 parent task 上的 artifact/message 事件。
- 最后看到 parent task 状态为 `TASK_STATE_INPUT_REQUIRED`。
- `status.message.parts[0].text` 包含 AgentB 的追问。
- 记下返回里的 parent `taskId` 和 `contextId`。

可以用下面的 PowerShell 从 SSE 输出里提取 parent taskId：

```powershell
$events = $first |
  Where-Object { $_ -like 'data:*' } |
  ForEach-Object { ($_ -replace '^data:', '') | ConvertFrom-Json }

$taskId = $events |
  ForEach-Object {
    if ($_.result.statusUpdate.status.state -eq 'TASK_STATE_INPUT_REQUIRED') {
      $_.result.statusUpdate.taskId
    } elseif ($_.result.task.status.state -eq 'TASK_STATE_INPUT_REQUIRED') {
      $_.result.task.id
    }
  } |
  Where-Object { $_ } |
  Select-Object -First 1

$taskId
```

也可以查询 parent task，确认它已经停在 `INPUT_REQUIRED`：

```powershell
$getTaskBody = @{
  jsonrpc = '2.0'
  id = 'get-parent-1'
  method = 'GetTask'
  params = @{
    id = $taskId
  }
} | ConvertTo-Json -Depth 8

Invoke-RestMethod http://localhost:18081/a2a `
  -Method Post `
  -ContentType 'application/json' `
  -Body $getTaskBody |
  ConvertTo-Json -Depth 20
```

## 第二次请求：把用户输入续写回同一个远端 AgentB task

如果上一节已经提取了 `$taskId`，这里可以直接复用；否则把 `$taskId` 改成第一次返回的 parent taskId。

```powershell
# $taskId = '<第一次返回的 parent taskId>'

$body = @{
  jsonrpc = '2.0'
  id = 'remote-openjiuwen-2'
  method = 'SendStreamingMessage'
  params = @{
    message = @{
      role = 'ROLE_USER'
      messageId = 'msg-a-2'
      taskId = $taskId
      contextId = 'ctx-a-1'
      metadata = @{
        userId = 'manual-user'
        agentId = 'local-a'
      }
      parts = @(@{ text = 'follow up from user' })
    }
  }
} | ConvertTo-Json -Depth 12

$second = curl.exe http://localhost:18081/a2a `
  -H "Content-Type: application/json" `
  -H "Accept: text/event-stream" `
  -d $body

$second
```

成功现象：

- 第二次请求先不会重新进入 AgentA 本地推理，而是根据 parent task metadata 续写同一个远端 AgentB task。
- SSE 中看到 AgentB 第二轮的两条流式消息。
- AgentB 完成后，runtime 再用远端结果 resume AgentA，最后看到 AgentA 的最终回答，内容包含 `AgentA resumed from remote tool result`。
- parent task 最终进入 `TASK_STATE_COMPLETED`。

最后可以再次查询 parent task：

```powershell
$getTaskBody = @{
  jsonrpc = '2.0'
  id = 'get-parent-2'
  method = 'GetTask'
  params = @{
    id = $taskId
  }
} | ConvertTo-Json -Depth 8

Invoke-RestMethod http://localhost:18081/a2a `
  -Method Post `
  -ContentType 'application/json' `
  -Body $getTaskBody |
  ConvertTo-Json -Depth 20
```

成功时返回里能看到 parent task 的 `status.state` 为 `TASK_STATE_COMPLETED`，并且文本里包含 `AgentA resumed from remote tool result` 和 `AgentB completed after the second user input`。
