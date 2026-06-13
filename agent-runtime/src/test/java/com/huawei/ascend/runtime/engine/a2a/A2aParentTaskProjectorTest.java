package com.huawei.ascend.runtime.engine.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import java.util.List;
import java.util.Map;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.junit.jupiter.api.Test;

class A2aParentTaskProjectorTest {

    private final A2aParentTaskProjector projector = new A2aParentTaskProjector();

    /**
     * Remote failure text routinely carries newlines and quotes (stack traces,
     * SSL alerts); the tool result handed back to the framework must stay
     * machine-parseable JSON with the original text intact.
     */
    @Test
    void failedRemoteErrorTextSurvivesJsonRoundTrip() throws Exception {
        String text = "line one\nline two\twith \"quotes\" and a backslash \\";
        A2aParentTaskProjector.RemoteOutcome outcome = projector.projectRemoteOutcome(
                invocation(),
                List.of(new RemoteAgentInvocationService.RemoteAgentResult(
                        RemoteAgentInvocationService.RemoteAgentResult.Type.FAILED,
                        text, "remote-task-1", "remote-ctx-1", Map.of())),
                mock(AgentEmitter.class));

        JsonNode parsed = new ObjectMapper().readTree(outcome.toolResult());
        assertThat(parsed.get("error").asText()).isEqualTo(text);
        assertThat(parsed.has("code")).isFalse();
    }

    @Test
    void failedRemoteResultCodePassesThroughToToolResult() throws Exception {
        A2aParentTaskProjector.RemoteOutcome outcome = projector.projectRemoteOutcome(
                invocation(),
                List.of(new RemoteAgentInvocationService.RemoteAgentResult(
                        RemoteAgentInvocationService.RemoteAgentResult.Type.FAILED,
                        "remote A2A stream timed out", "remote-task-1", "remote-ctx-1",
                        Map.of("code", "REMOTE_TIMEOUT", "retryable", true))),
                mock(AgentEmitter.class));

        JsonNode parsed = new ObjectMapper().readTree(outcome.toolResult());
        assertThat(parsed.get("error").asText()).isEqualTo("remote A2A stream timed out");
        assertThat(parsed.get("code").asText()).isEqualTo("REMOTE_TIMEOUT");
    }

    /**
     * A resume context built from a request that carries parent metadata must propagate
     * parent linkage so the second leg's trajectory is correlated to the calling run.
     */
    @Test
    void remoteResumeContext_withParentMetadata_setsParentLinkage() {
        RequestContext ctx = mock(RequestContext.class);
        when(ctx.getMetadata()).thenReturn(Map.of(
                "runtime.parent.taskId", "caller-task-7",
                "runtime.parent.traceId", "caller-trace-abc"));

        AgentExecutionContext context = projector.remoteResumeContext(
                ctx, "agent-x", invocation(), "tool-result");

        assertThat(context.getParentTaskId()).isEqualTo("caller-task-7");
        assertThat(context.getParentTraceId()).isEqualTo("caller-trace-abc");
    }

    /**
     * A resume context built from a request with no parent metadata must leave parent
     * linkage null so a top-level continuation run does not emit spurious parent ids.
     */
    @Test
    void remoteResumeContext_withoutParentMetadata_leavesParentLinkageNull() {
        RequestContext ctx = mock(RequestContext.class);
        when(ctx.getMetadata()).thenReturn(Map.of());

        AgentExecutionContext context = projector.remoteResumeContext(
                ctx, "agent-x", invocation(), "tool-result");

        assertThat(context.getParentTaskId()).isNull();
        assertThat(context.getParentTraceId()).isNull();
    }

    private static AgentExecutionResult.RemoteInvocation invocation() {
        return new AgentExecutionResult.RemoteInvocation(
                "remote-agent", "a2a_remote_remote_agent", "tool-call-1",
                "task-1", "ctx-1", "conversation-1", Map.of("message", "hello"));
    }
}
