package com.huawei.ascend.runtime.engine.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import com.huawei.ascend.runtime.engine.service.RemoteAgentInvocationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class A2aAgentExecutorTest {

    /** A FAILED result must surface its code+message to the A2A wire, not a bare fail(). */
    @Test
    void failedResult_carriesErrorReasonToTheWire() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        when(handler.execute(any())).thenAnswer(inv -> Stream.of(new Object()));
        StreamAdapter adapter = raw -> raw.map(o -> AgentExecutionResult.failed("OUT_OF_DOMAIN", "no skill for request"));
        when(handler.resultAdapter()).thenReturn(adapter);

        AgentEmitter emitter = newEmitter();
        new A2aAgentExecutor(handler).execute(requestContext(), emitter);

        assertThat(failureText(emitter)).isEqualTo("OUT_OF_DOMAIN: no skill for request");
    }

    /** An exception thrown during execution must also fail with a reason, not silently. */
    @Test
    void executionException_failsWithReason() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        when(handler.execute(any())).thenThrow(new IllegalStateException("boom"));

        AgentEmitter emitter = newEmitter();
        new A2aAgentExecutor(handler).execute(requestContext(), emitter);

        assertThat(failureText(emitter)).isEqualTo("RUNTIME_ERROR: boom");
    }

    @Test
    void remoteInvocationInvokesOutboundThenReentersLocalHandlerWithToolResult() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        when(handler.execute(any())).thenAnswer(inv -> {
            var context = (com.huawei.ascend.runtime.engine.AgentExecutionContext) inv.getArgument(0);
            if ("REMOTE_RESUME".equals(context.getInputType())) {
                assertThat(context.getVariables()).containsEntry("runtime.remoteToolResult", "remote done");
                return Stream.of("resumed");
            }
            return Stream.of("remote");
        });
        when(handler.resultAdapter()).thenReturn(raw -> raw.map(value -> "remote".equals(value)
                    ? AgentExecutionResult.remoteInvocation(new AgentExecutionResult.RemoteInvocation(
                            "remote-agent", "a2a_remote_remote_agent", "tool-call-1",
                            "task-1", "ctx-1", "task-1", Map.of("message", "hello remote")))
                    : AgentExecutionResult.completed("local final after remote")));
        RecordingRemoteOutbound outbound = new RecordingRemoteOutbound(List.of(
                remoteResult(RemoteAgentInvocationService.RemoteAgentResult.Type.MESSAGE, "remote progress"),
                remoteResult(RemoteAgentInvocationService.RemoteAgentResult.Type.COMPLETED, "remote done")));

        AgentEmitter emitter = newEmitter();
        new A2aAgentExecutor(handler, A2aAgentExecutor.RemoteSupport.forOutbound(outbound)).execute(requestContext(), emitter);

        assertThat(outbound.requests).hasSize(1);
        assertThat(outbound.requests.get(0).message()).isEqualTo("hello remote");
        verify(emitter).complete(any(Message.class));
    }

    @Test
    void remoteProgressIsProjectedWhileOutboundInvocationIsStillRunning() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        when(handler.execute(any())).thenAnswer(inv -> {
            var context = (com.huawei.ascend.runtime.engine.AgentExecutionContext) inv.getArgument(0);
            return Stream.of("REMOTE_RESUME".equals(context.getInputType()) ? "resumed" : "remote");
        });
        when(handler.resultAdapter()).thenReturn(raw -> raw.map(value -> "remote".equals(value)
                ? AgentExecutionResult.remoteInvocation(new AgentExecutionResult.RemoteInvocation(
                        "remote-agent", "a2a_remote_remote_agent", "tool-call-1",
                        "task-1", "ctx-1", "conversation-1", Map.of("message", "hello remote")))
                : AgentExecutionResult.completed("local final after remote")));

        AtomicBoolean projectedBeforeOutboundReturned = new AtomicBoolean(false);
        AgentEmitter emitter = newEmitter();
        doAnswer(inv -> {
            projectedBeforeOutboundReturned.set(true);
            return null;
        }).when(emitter).addArtifact(anyList());

        RemoteAgentInvocationService.OutboundPort outbound = new RemoteAgentInvocationService.OutboundPort() {
            @Override
            public List<RemoteAgentInvocationService.RemoteAgentResult> invoke(
                    RemoteAgentInvocationService.RemoteAgentRequest request,
                    Consumer<RemoteAgentInvocationService.RemoteAgentResult> eventConsumer) {
                eventConsumer.accept(remoteResult(RemoteAgentInvocationService.RemoteAgentResult.Type.MESSAGE, "remote progress"));
                assertThat(projectedBeforeOutboundReturned).isTrue();
                return List.of(remoteResult(RemoteAgentInvocationService.RemoteAgentResult.Type.COMPLETED, "remote done"));
            }

            @Override
            public void cancel(RemoteAgentInvocationService.RemoteTaskReference reference) {
            }
        };

        new A2aAgentExecutor(handler, A2aAgentExecutor.RemoteSupport.forOutbound(outbound))
                .execute(requestContext(), emitter);

        verify(emitter).complete(any(Message.class));
    }

    @Test
    void remoteInputRequiredDoesNotReenterLocalHandlerAndUsesNonFinalInputRequired() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        when(handler.execute(any())).thenAnswer(inv -> Stream.of("remote"));
        when(handler.resultAdapter()).thenReturn(raw -> raw.map(value -> AgentExecutionResult.remoteInvocation(new AgentExecutionResult.RemoteInvocation(
                    "remote-agent", "a2a_remote_remote_agent", "tool-call-1",
                    "task-1", "ctx-1", "task-1", Map.of("message", "needs user")))));
        RecordingRemoteOutbound outbound = new RecordingRemoteOutbound(List.of(
                new RemoteAgentInvocationService.RemoteAgentResult(
                        RemoteAgentInvocationService.RemoteAgentResult.Type.INPUT_REQUIRED,
                        "remote asks for more",
                        "remote-task-1",
                        "remote-ctx-1",
                        Map.of())));

        AgentEmitter emitter = newEmitter();
        new A2aAgentExecutor(handler, A2aAgentExecutor.RemoteSupport.forOutbound(outbound)).execute(requestContext(), emitter);

        ArgumentCaptor<TaskStatusUpdateEvent> eventCaptor = ArgumentCaptor.forClass(TaskStatusUpdateEvent.class);
        verify(emitter).emitEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().status().state()).isEqualTo(TaskState.TASK_STATE_INPUT_REQUIRED);
        assertThat(eventCaptor.getValue().isFinal()).isFalse();
        assertThat(eventCaptor.getValue().metadata())
                .containsEntry("runtime.waitingTarget", "REMOTE_AGENT")
                .containsEntry("runtime.remoteTaskId", "remote-task-1")
                .containsEntry("runtime.remoteContextId", "remote-ctx-1")
                .containsEntry("runtime.toolCallId", "tool-call-1")
                .containsEntry("runtime.remoteInvocationId", "tool-call-1");
        org.mockito.Mockito.verify(handler, org.mockito.Mockito.times(1)).execute(any());
    }

    @Test
    void remoteInvocationWithoutTerminalResultReentersLocalHandlerWithToolError() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        when(handler.execute(any())).thenAnswer(inv -> {
            var context = (com.huawei.ascend.runtime.engine.AgentExecutionContext) inv.getArgument(0);
            if ("REMOTE_RESUME".equals(context.getInputType())) {
                assertThat(context.getVariables().get("runtime.remoteToolResult").toString())
                        .contains("REMOTE_TERMINAL_RESULT_MISSING");
                return Stream.of("resumed");
            }
            return Stream.of("remote");
        });
        when(handler.resultAdapter()).thenReturn(raw -> raw.map(value -> "remote".equals(value)
                ? AgentExecutionResult.remoteInvocation(new AgentExecutionResult.RemoteInvocation(
                        "remote-agent", "a2a_remote_remote_agent", "tool-call-1",
                        "task-1", "ctx-1", "conversation-1", Map.of("message", "hello remote")))
                : AgentExecutionResult.completed("local final after missing terminal")));
        RecordingRemoteOutbound outbound = new RecordingRemoteOutbound(List.of(
                remoteResult(RemoteAgentInvocationService.RemoteAgentResult.Type.MESSAGE, "remote progress only")));

        AgentEmitter emitter = newEmitter();
        new A2aAgentExecutor(handler, A2aAgentExecutor.RemoteSupport.forOutbound(outbound)).execute(requestContext(), emitter);

        verify(emitter).complete(any(Message.class));
    }

    @Test
    void remoteContinuationResumesExistingRemoteTaskWithoutCallingLocalHandlerFirst() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        when(handler.execute(any())).thenAnswer(inv -> {
            var context = (com.huawei.ascend.runtime.engine.AgentExecutionContext) inv.getArgument(0);
            assertThat(context.getInputType()).isEqualTo("REMOTE_RESUME");
            assertThat(context.getVariables()).containsEntry("runtime.remoteToolResult", "remote final");
            assertThat(context.getScope().tenantId()).isEqualTo("tenant-a");
            assertThat(context.getScope().userId()).isEqualTo("user-a");
            return Stream.of("resumed");
        });
        when(handler.resultAdapter()).thenReturn(raw -> raw.map(value -> AgentExecutionResult.completed("local final")));
        RecordingRemoteOutbound outbound = new RecordingRemoteOutbound(List.of(
                remoteResult(RemoteAgentInvocationService.RemoteAgentResult.Type.COMPLETED, "remote final")));

        AgentEmitter emitter = newEmitter();
        new A2aAgentExecutor(handler, A2aAgentExecutor.RemoteSupport.forOutbound(outbound))
                .execute(remoteContinuationContext(), emitter);

        assertThat(outbound.requests).hasSize(1);
        RemoteAgentInvocationService.RemoteAgentRequest request = outbound.requests.get(0);
        assertThat(request.remoteAgentId()).isEqualTo("remote-agent");
        assertThat(request.remoteTaskId()).isEqualTo("remote-task-1");
        assertThat(request.remoteContextId()).isEqualTo("remote-ctx-1");
        assertThat(request.toolCallId()).isEqualTo("tool-call-1");
        assertThat(request.message()).isEqualTo("user follow-up");
        verify(emitter).complete(any(Message.class));
    }

    @Test
    void remoteContinuationMissingRouteMetadataFailsWithoutCallingRemote() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        RecordingRemoteOutbound outbound = new RecordingRemoteOutbound(List.of(
                remoteResult(RemoteAgentInvocationService.RemoteAgentResult.Type.COMPLETED, "should not be called")));

        RequestContext ctx = requestContext();
        when(ctx.getTask()).thenReturn(new Task(
                "task-1",
                "ctx-1",
                new TaskStatus(TaskState.TASK_STATE_INPUT_REQUIRED),
                List.of(),
                List.of(),
                Map.of("runtime.waitingTarget", "REMOTE_AGENT")));
        AgentEmitter emitter = newEmitter();

        new A2aAgentExecutor(handler, A2aAgentExecutor.RemoteSupport.forOutbound(outbound))
                .execute(ctx, emitter);

        assertThat(outbound.requests).isEmpty();
        assertThat(failureText(emitter)).contains("REMOTE_ROUTE_METADATA_MISSING");
    }

    @Test
    void nestedRemoteInvocationAfterRemoteResumeFailsParentTask() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        when(handler.execute(any())).thenAnswer(inv -> Stream.of("resumed"));
        when(handler.resultAdapter()).thenReturn(raw -> raw.map(value -> AgentExecutionResult.remoteInvocation(
                new AgentExecutionResult.RemoteInvocation(
                        "remote-agent-2",
                        "a2a_remote_remote_agent_2",
                        "tool-call-2",
                        "task-1",
                        "ctx-1",
                        "conversation-1",
                        Map.of("message", "nested")))));
        RecordingRemoteOutbound outbound = new RecordingRemoteOutbound(List.of(
                remoteResult(RemoteAgentInvocationService.RemoteAgentResult.Type.COMPLETED, "remote final")));

        AgentEmitter emitter = newEmitter();
        new A2aAgentExecutor(handler, A2aAgentExecutor.RemoteSupport.forOutbound(outbound))
                .execute(remoteContinuationContext(), emitter);

        assertThat(outbound.requests).hasSize(1);
        assertThat(failureText(emitter))
                .isEqualTo("NESTED_REMOTE_INVOCATION_UNSUPPORTED: remote A2A invocation after REMOTE_RESUME is not supported");
    }

    @Test
    void inputRequiredMetadataMergesRemoteResultMetadata() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        when(handler.execute(any())).thenAnswer(inv -> Stream.of("remote"));
        when(handler.resultAdapter()).thenReturn(raw -> raw.map(value -> AgentExecutionResult.remoteInvocation(new AgentExecutionResult.RemoteInvocation(
                "remote-agent", "a2a_remote_remote_agent", "tool-call-1",
                "task-1", "ctx-1", "task-1", Map.of("message", "needs user")))));
        RecordingRemoteOutbound outbound = new RecordingRemoteOutbound(List.of(
                new RemoteAgentInvocationService.RemoteAgentResult(
                        RemoteAgentInvocationService.RemoteAgentResult.Type.INPUT_REQUIRED,
                        "remote asks for more",
                        "remote-task-1",
                        "remote-ctx-1",
                        Map.of("remote.promptVersion", "v2"))));

        AgentEmitter emitter = newEmitter();
        new A2aAgentExecutor(handler, A2aAgentExecutor.RemoteSupport.forOutbound(outbound)).execute(requestContext(), emitter);

        ArgumentCaptor<TaskStatusUpdateEvent> eventCaptor = ArgumentCaptor.forClass(TaskStatusUpdateEvent.class);
        verify(emitter).emitEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().metadata())
                .containsEntry("runtime.waitingTarget", "REMOTE_AGENT")
                .containsEntry("runtime.remoteTaskId", "remote-task-1")
                .containsEntry("remote.promptVersion", "v2");
    }

    @Test
    void cancelPropagatesToRemoteTaskWhenParentIsWaitingForRemoteAgent() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        RecordingRemoteOutbound outbound = new RecordingRemoteOutbound(List.of());

        AgentEmitter emitter = newEmitter();
        new A2aAgentExecutor(handler, A2aAgentExecutor.RemoteSupport.forOutbound(outbound))
                .cancel(remoteContinuationContext(), emitter);

        verify(emitter).cancel();
        assertThat(outbound.canceled).hasSize(1);
        assertThat(outbound.canceled.get(0).remoteAgentId()).isEqualTo("remote-agent");
        assertThat(outbound.canceled.get(0).remoteTaskId()).isEqualTo("remote-task-1");
        assertThat(outbound.canceled.get(0).remoteContextId()).isEqualTo("remote-ctx-1");
    }

    private static RequestContext requestContext() {
        RequestContext ctx = mock(RequestContext.class);
        when(ctx.getTaskId()).thenReturn("task-1");
        when(ctx.getContextId()).thenReturn("ctx-1");
        when(ctx.getTenant()).thenReturn("tenant-a");
        when(ctx.getMetadata()).thenReturn(Map.of("userId", "user-a", "agentId", "agent-x"));
        when(ctx.getMessage()).thenReturn(
                Message.builder().role(Message.Role.ROLE_USER).parts(List.<Part<?>>of(new TextPart("hi"))).build());
        return ctx;
    }

    private static RequestContext remoteContinuationContext() {
        RequestContext ctx = requestContext();
        when(ctx.getMessage()).thenReturn(Message.builder()
                .role(Message.Role.ROLE_USER)
                .parts(List.<Part<?>>of(new TextPart("user follow-up")))
                .build());
        when(ctx.getTask()).thenReturn(new Task(
                "task-1",
                "ctx-1",
                new TaskStatus(TaskState.TASK_STATE_INPUT_REQUIRED),
                List.of(),
                List.of(),
                Map.of(
                        "runtime.waitingTarget", "REMOTE_AGENT",
                        "runtime.remoteAgentId", "remote-agent",
                        "runtime.remoteTaskId", "remote-task-1",
                        "runtime.remoteContextId", "remote-ctx-1",
                        "runtime.toolCallId", "tool-call-1",
                        "runtime.localConversationId", "conversation-1")));
        return ctx;
    }

    private static AgentEmitter newEmitter() {
        AgentEmitter emitter = mock(AgentEmitter.class);
        when(emitter.getTaskId()).thenReturn("task-1");
        when(emitter.getContextId()).thenReturn("ctx-1");
        when(emitter.newAgentMessage(anyList(), any())).thenAnswer(inv -> {
            List<Part<?>> parts = inv.getArgument(0);
            return Message.builder().role(Message.Role.ROLE_AGENT).parts(parts).build();
        });
        return emitter;
    }

    private static RemoteAgentInvocationService.RemoteAgentResult remoteResult(
            RemoteAgentInvocationService.RemoteAgentResult.Type type, String text) {
        return new RemoteAgentInvocationService.RemoteAgentResult(type, text, null, null, Map.of());
    }

    /** Capture the Message handed to fail(Message) and concatenate its text. */
    private static String failureText(AgentEmitter emitter) {
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(emitter).fail(captor.capture());
        return captor.getValue().parts().stream()
                .filter(TextPart.class::isInstance)
                .map(p -> ((TextPart) p).text())
                .reduce("", String::concat);
    }

    private static final class RecordingRemoteOutbound implements RemoteAgentInvocationService.OutboundPort {
        private final List<RemoteAgentInvocationService.RemoteAgentResult> results;
        private final List<RemoteAgentInvocationService.RemoteAgentRequest> requests = new ArrayList<>();
        private final List<RemoteAgentInvocationService.RemoteTaskReference> canceled = new ArrayList<>();

        private RecordingRemoteOutbound(List<RemoteAgentInvocationService.RemoteAgentResult> results) {
            this.results = results;
        }

        @Override
        public List<RemoteAgentInvocationService.RemoteAgentResult> invoke(
                RemoteAgentInvocationService.RemoteAgentRequest request,
                Consumer<RemoteAgentInvocationService.RemoteAgentResult> eventConsumer) {
            requests.add(request);
            if (eventConsumer != null) {
                results.forEach(eventConsumer);
            }
            return results;
        }

        @Override
        public void cancel(RemoteAgentInvocationService.RemoteTaskReference reference) {
            canceled.add(reference);
        }
    }
}
