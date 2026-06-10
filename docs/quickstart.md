# Quickstart — First Agent on `spring-ai-ascend`

> Goal: reach your first agent invocation over A2A without modifying any
> platform source file (constraint: ARCHITECTURE.md §4 #60, Business/Platform
> decoupling — developers build agents against the platform, not into it).

This document is referenced from the root [`README.md`](../README.md).

You will: implement a tiny echo agent against the framework-neutral
`AgentRuntimeHandler` SPI, boot it with the pure-Java entry point
`RuntimeApp.create(handler).run(LocalA2aRuntimeHost.port(8080))`, and verify it
with two `curl` calls.

---

## 1. Prerequisites

- JDK 21 (any vendor; tested with Temurin and OpenJDK).
- Maven 3.9+ (or the bundled wrapper `./mvnw`).
- Nothing else. No database, no LLM key — the runtime ships in-memory
  reference implementations and the echo agent below calls no model.

The artifacts are not yet published to Maven Central, so install the reactor
into your local repository first:

```bash
./mvnw -q -DskipTests install
```

(For the full build with unit + integration tests + the quality gate, use
`./mvnw -T 1C -Pquality verify` — the canonical command from the README.)

## 2. Add the dependency

The run-owning runtime SDK is a single module:

```xml
<dependency>
  <groupId>com.huawei.ascend</groupId>
  <artifactId>agent-runtime</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

To pin versions across a larger application, import the
`com.huawei.ascend:spring-ai-ascend-dependencies` BoM instead and omit the
version above.

## 3. Implement an echo agent

`AgentRuntimeHandler` (package `com.huawei.ascend.runtime.engine.spi`) is the
seam between the engine and a concrete agent framework: four methods, no
Spring, no A2A types in your business logic.

```java
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.a2a.Messages;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import java.util.List;
import java.util.stream.Stream;

public final class EchoAgentHandler implements AgentRuntimeHandler {
    @Override public String agentId() { return "echo-agent"; }
    @Override public boolean isHealthy() { return true; }
    @Override public Stream<?> execute(AgentExecutionContext context) {
        List<org.a2aproject.sdk.spec.Message> messages = context.getMessages();
        String text = messages.isEmpty() ? "" : Messages.text(messages.get(messages.size() - 1));
        return Stream.of("echo: " + text);
    }
    @Override public StreamAdapter resultAdapter() {
        return raw -> raw.map(line -> AgentExecutionResult.completed(String.valueOf(line)));
    }
}
```

`execute` returns a `Stream<?>` of framework-specific results;
`resultAdapter()` maps them to engine-neutral `AgentExecutionResult`s
(`output`, `completed`, `failed`, `interrupted`). The engine owns the Run
lifecycle, task state, and A2A event mapping — you never touch them.

## 4. Boot it

`RuntimeApp` / `RuntimeHost` are framework-neutral; Spring Boot is confined to
the host implementation (`LocalA2aRuntimeHost`), which assembles the runtime
layers and serves A2A over HTTP:

```java
import com.huawei.ascend.runtime.app.LocalA2aRuntimeHost;
import com.huawei.ascend.runtime.app.RunningRuntime;
import com.huawei.ascend.runtime.app.RuntimeApp;

public final class EchoAgentApp {
    public static void main(String[] args) {
        RunningRuntime runtime = RuntimeApp.create(new EchoAgentHandler())
                .run(LocalA2aRuntimeHost.port(8080));
        System.out.println("A2A serving on port " + runtime.port());
        // runtime is AutoCloseable — close() stops the server.
    }
}
```

`LocalA2aRuntimeHost.port(0)` binds an ephemeral port, readable afterwards via
`RunningRuntime.port()` — handy in integration tests.

### Booting without Postgres

The `agent-runtime` jar ships an `application.yml` whose
`spring.datasource.url` defaults to a local Postgres and which enables Flyway.
Two cases:

- **Minimal classpath (just the dependency above):** nothing to do. The
  JDBC / Flyway / pgvector dependencies are declared `<optional>` in
  `agent-runtime`, so they are not on your classpath and the corresponding
  auto-configurations never activate; the datasource keys are inert.
- **Your application adds a JDBC stack** (e.g. `spring-boot-starter-data-jdbc`
  or Flyway) for its own use: exclude the auto-configurations so boot does not
  try to reach Postgres. This is the exact list `agent-runtime`'s own
  `RuntimeAppTest` uses:

```bash
java -Dspring.autoconfigure.exclude=\
org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,\
org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,\
org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration,\
io.github.resilience4j.springboot3.verifier.autoconfigure.SpringBoot3VerifierAutoConfiguration \
  -cp ... EchoAgentApp
```

Note: setting `spring.autoconfigure.exclude` yourself **replaces** the list the
library's `application.yml` already carries, which is why the Resilience4j
verifier exclusion is repeated above — keep it in your list. Application-level
keys (like the tenant settings in §6) go in your own `application.yaml`, the
same pattern the e2e example uses.

## 5. Verify with curl

Fetch the agent card (a legacy alias is served at `/.well-known/agent.json`):

```bash
curl http://localhost:8080/.well-known/agent-card.json
```

You get the auto-generated card for `echo-agent` (override it by declaring an
`AgentCardProvider` or `AgentCard` bean).

Send a blocking JSON-RPC `SendMessage` to `/a2a`:

```bash
curl http://localhost:8080/a2a \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: bank-7' \
  -d '{"jsonrpc":"2.0","id":"request-1","method":"SendMessage","params":{"message":{"role":"ROLE_USER","parts":[{"text":"ping"}],"messageId":"message-1"}}}'
```

The response is a JSON-RPC `result` carrying the completed task:
`result.task.status.state` is `TASK_STATE_COMPLETED` and
`result.task.status.message.parts[0].text` is `echo: ping`.

`message/send` is idempotent per `(tenant, messageId)`: a retry with the same
`messageId` replays the already-created task instead of running the agent
twice.

For token-by-token streaming, change the method to `SendStreamingMessage` and
ask for SSE:

```bash
curl -N http://localhost:8080/a2a \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -H 'X-Tenant-Id: bank-7' \
  -d '{"jsonrpc":"2.0","id":"request-2","method":"SendStreamingMessage","params":{"message":{"role":"ROLE_USER","parts":[{"text":"ping"}],"messageId":"message-2"}}}'
```

Each SSE frame is a JSON-RPC response event; a mid-stream agent failure ends
the stream with a JSON-RPC error frame rather than a bare transport drop.

## 6. Multi-tenant knobs

Every request is attributed to a tenant. Precedence: JWT-authenticated tenant
(when the auth filter is enabled) > `X-Tenant-Id` header > configured default.

| Property | Default | Meaning |
|---|---|---|
| `agent-runtime.access.a2a.default-tenant-id` | `default` | Tenant attributed to requests without an `X-Tenant-Id` header — single-tenant deployments set it once instead of sending the header. |
| `agent-runtime.access.a2a.jwt.enabled` | `false` | Enables tenant authentication at the A2A ingress. |
| `agent-runtime.access.a2a.jwt.hmac-secret` | — | Shared HS256 secret used to verify bearer tokens. |
| `agent-runtime.access.a2a.jwt.clock-skew-seconds` | `30` | Tolerated clock skew for `exp`/`nbf` validation. |

With `jwt.enabled=true`, every `/a2a` request must carry
`Authorization: Bearer <jwt>` whose HS256 signature verifies and whose
`tenant_id` claim, when an `X-Tenant-Id` header is also present, must match it
(401 for missing/invalid tokens, 403 on a cross-check mismatch).

### Hosting AgentScope / LangGraph agents

Remote agents served by their native runtimes plug in behind the same SPI — no
echo-style handler to write, just construct the shipped client handler as a
bean (or pass it to `RuntimeApp.create`):

- **AgentScope Runtime:** `AgentScopeRuntimeClientHandler(agentId, new
  AgentScopeRuntimeClient(new AgentScopeRuntimeClientProperties(baseUrl)))`
  (package `com.huawei.ascend.runtime.engine.agentscope`).
- **LangGraph Platform / `langgraph-api` dev server:**
  `LangGraphRuntimeClientHandler(agentId, new LangGraphRuntimeClient(new
  LangGraphRuntimeClientProperties(baseUrl, assistantId)))`
  (package `com.huawei.ascend.runtime.engine.langgraph`).

Working Spring wiring for both lives in the e2e example (e.g.
[`LangGraphE2eConfiguration`](../examples/agent-runtime-a2a-llm-e2e/src/main/java/com/huawei/ascend/examples/a2a/LangGraphE2eConfiguration.java)).

### Declaring agents in YAML

The `agent-sdk` module builds handlers from a declarative `ascend-agent/v1`
spec: `AgentHandlerFactory.fromYaml(Path)` (package
`com.huawei.ascend.agentsdk.factory`) loads the YAML — schema, framework,
model, prompt, tools, skills, with `${ENV_VAR}` resolution — and returns a
ready `AgentRuntimeHandler`. Sample specs live under
[`examples/agent-sdk-example/openjiuwen/`](../examples/agent-sdk-example/openjiuwen/).

## 7. Where to go next

- **End-to-end example:**
  [`examples/agent-runtime-a2a-llm-e2e`](../examples/agent-runtime-a2a-llm-e2e/README.md)
  — a full Spring Boot application hosting openJiuwen / AgentScope / LangGraph
  agents behind the same A2A surface, plus a console A2A client and a
  multi-runtime gateway. Sibling examples cover return modes
  (`agent-runtime-a2a-return-modes-e2e`) and the YAML SDK
  (`agent-sdk-example`).
- Architecture of record:
  [`architecture/docs/L0/ARCHITECTURE.md`](../architecture/docs/L0/ARCHITECTURE.md).
- Runtime contract surface: [`docs/contracts/`](contracts/).
- Engineering rules you must honour: [`CLAUDE.md`](../CLAUDE.md).

## 8. When a test fails

Before reasoning about a failure, run the **Evidence-First Debug Sequence** in
[`docs/harness/debug-first-evidence.md`](harness/debug-first-evidence.md)
(authority: CLAUDE.md Rule D-3): capture the failing FQN, trace ID, MDC slice,
and raw error BEFORE opening `ARCHITECTURE.md`.

For a fast inner loop, `./mvnw -pl agent-runtime -am test -q` builds and tests
just the runtime module and its dependencies.

If anything in this quickstart requires modifying platform source to make it
work — file an issue tagged `decoupling-defect`. Developers build agents
against the platform, not into the platform.
