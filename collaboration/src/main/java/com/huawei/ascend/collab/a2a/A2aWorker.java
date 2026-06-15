package com.huawei.ascend.collab.a2a;

import com.huawei.ascend.collab.core.SubTask;
import com.huawei.ascend.collab.core.TaskToken;
import com.huawei.ascend.collab.core.WorkResult;
import com.huawei.ascend.collab.core.Worker;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.a2aproject.sdk.client.http.A2ACardResolver;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.CancelTaskParams;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;

/**
 * Bridges the collaboration engine to a real A2A agent: the
 * {@link com.huawei.ascend.collab.core.Coordinator} dispatches a {@link SubTask}
 * to this worker, which streams it to a remote A2A endpoint over the SDK
 * {@link ClientTransport}, carrying the {@link TaskToken} on the message metadata,
 * and maps the remote task's terminal state to a {@link WorkResult}. The same
 * Coordinator therefore orchestrates real A2A agents (this worker) or, in eval,
 * deterministic in-memory workers.
 *
 * <p>Uses streaming ({@code sendMessageStreaming}) — the transport the runtime
 * serves — collecting events until a terminal/interrupted status. Token echo: A2A
 * correlates by taskId/contextId and the endpoint is operator-configured, so on a
 * correlated response this worker re-presents the issued token (the metadata-carried
 * token is the idempotency/deadline credential).
 */
public final class A2aWorker implements Worker {

    public static final String MK_TOKEN = "task.token.id";
    public static final String MK_TASK = "task.token.task";
    public static final String MK_IDEM = "task.token.idempotencyKey";
    public static final String MK_DEADLINE = "task.token.deadlineEpochMs";

    private final String id;
    private final Set<String> capabilities;
    private final ClientTransport transport;
    private final long timeoutMs;

    /**
     * @param baseUrl the remote agent's BASE url (e.g. {@code http://host:8080}); the
     *                agent card is resolved from {@code /.well-known/agent-card.json}.
     */
    public A2aWorker(String id, Set<String> capabilities, String baseUrl) {
        this(id, capabilities, baseUrl, 30_000);
    }

    public A2aWorker(String id, Set<String> capabilities, String baseUrl, long timeoutMs) {
        this.id = id;
        this.capabilities = Set.copyOf(capabilities);
        this.timeoutMs = timeoutMs;
        try {
            AgentCard card = new A2ACardResolver(baseUrl).getAgentCard();
            this.transport = new JSONRPCTransport(card);
        } catch (Throwable e) {
            throw new IllegalStateException(
                    "failed to resolve A2A agent card at " + baseUrl + ": " + e.getMessage(), e);
        }
    }

    /** For tests/custom transports. */
    public A2aWorker(String id, Set<String> capabilities, ClientTransport transport, long timeoutMs) {
        this.id = id;
        this.capabilities = Set.copyOf(capabilities);
        this.transport = transport;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Set<String> capabilities() {
        return capabilities;
    }

    @Override
    public WorkResult execute(SubTask task, TaskToken token) {
        List<StreamingEventKind> events = new ArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<TaskState> terminal = new AtomicReference<>();
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put(MK_TOKEN, token.tokenId().toString());
            meta.put(MK_TASK, token.taskId());
            meta.put(MK_IDEM, token.idempotencyKey().toString());
            meta.put(MK_DEADLINE, token.deadlineEpochMs());

            Message message = Message.builder()
                    .role(Message.Role.ROLE_USER)
                    .messageId(UUID.randomUUID().toString())
                    .contextId(token.taskId())
                    .parts(List.<Part<?>>of(new TextPart(task.payload() == null ? "" : task.payload())))
                    .metadata(meta)
                    .build();
            MessageSendParams params = MessageSendParams.builder()
                    .message(message).metadata(meta).tenant(token.tenantId()).build();

            transport.sendMessageStreaming(params,
                    event -> {
                        events.add(event);
                        TaskState st = terminalStateOf(event);
                        if (st != null) {
                            terminal.set(st);
                            done.countDown();
                        }
                    },
                    error -> {
                        failure.set(error);
                        done.countDown();
                    },
                    new ClientCallContext(Map.of(), Map.of("X-Tenant-Id", token.tenantId())));

            if (!done.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                return WorkResult.timeout(task.id(), token, id);
            }
        } catch (Throwable t) {
            return WorkResult.failed(task.id(), token, id, "a2a error: " + t.getClass().getSimpleName());
        }

        if (failure.get() != null && terminal.get() == null) {
            return WorkResult.failed(task.id(), token, id, "a2a stream error: " + failure.get().getMessage());
        }

        TaskState state = terminal.get();
        String text = textFrom(events);
        if (state == TaskState.TASK_STATE_COMPLETED) {
            return WorkResult.completed(task.id(), text == null || text.isBlank() ? "completed" : text, token, id);
        }
        if (state == TaskState.TASK_STATE_INPUT_REQUIRED || state == TaskState.TASK_STATE_AUTH_REQUIRED) {
            return new WorkResult(task.id(), WorkResult.Status.INPUT_REQUIRED, null, token, id, null, "remote input");
        }
        if (state == TaskState.TASK_STATE_CANCELED) {
            return WorkResult.timeout(task.id(), token, id);
        }
        return WorkResult.failed(task.id(), token, id, "remote state " + state);
    }

    /** Reclaim a remote task (orchestrator on timeout/reassignment). */
    public void cancelRemote(String remoteTaskId, String tenantId) {
        try {
            transport.cancelTask(new CancelTaskParams(remoteTaskId),
                    new ClientCallContext(Map.of(), Map.of("X-Tenant-Id", tenantId)));
        } catch (Throwable ignored) {
            // best-effort
        }
    }

    private static TaskState terminalStateOf(StreamingEventKind event) {
        if (event instanceof TaskStatusUpdateEvent s && s.status() != null && s.status().state() != null) {
            TaskState st = s.status().state();
            return st.isFinal() || st.isInterrupted() ? st : null;
        }
        if (event instanceof Task t && t.status() != null && t.status().state() != null) {
            TaskState st = t.status().state();
            return st.isFinal() || st.isInterrupted() ? st : null;
        }
        if (event instanceof Message) {
            return TaskState.TASK_STATE_COMPLETED; // a direct message reply means done
        }
        return null;
    }

    private static String textFrom(List<StreamingEventKind> events) {
        StringBuilder sb = new StringBuilder();
        for (StreamingEventKind event : events) {
            if (event instanceof Message m) {
                appendParts(sb, m.parts());
            } else if (event instanceof TaskStatusUpdateEvent s
                    && s.status() != null && s.status().message() != null) {
                appendParts(sb, s.status().message().parts());
            } else if (event instanceof TaskArtifactUpdateEvent a && a.artifact() != null) {
                appendParts(sb, a.artifact().parts());
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static void appendParts(StringBuilder sb, List<Part<?>> parts) {
        if (parts == null) {
            return;
        }
        for (Part<?> p : parts) {
            if (p instanceof TextPart tp) {
                sb.append(tp.text());
            }
        }
    }
}
