package com.huawei.ascend.runtime.engine.a2a;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.service.RemoteAgentInvocationService;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TextPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class A2aAgentExecutor implements AgentExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(A2aAgentExecutor.class);

    private final AgentRuntimeHandler handler;
    private final RemoteSupport remoteSupport;
    private final A2aParentTaskProjector parentProjector = new A2aParentTaskProjector();

    public A2aAgentExecutor(AgentRuntimeHandler handler) {
        this(handler, null);
    }

    public A2aAgentExecutor(AgentRuntimeHandler handler, RemoteSupport remoteSupport) {
        this.handler = handler;
        this.remoteSupport = remoteSupport;
    }

    @Override
    public void execute(RequestContext ctx, AgentEmitter emitter) {
        String taskId = ctx.getTaskId();
        if (handler == null) {
            LOG.warn("[A2A] no handler registered taskId={}", taskId);
            emitter.fail();
            return;
        }
        long startedNanos = System.nanoTime();
        String sessionId = ctx.getContextId();
        String agentId = handler.agentId();
        LOG.info("[A2A] execute start taskId={} sessionId={} agentId={}", taskId, sessionId, agentId);

        // SUBMITTED -> WORKING
        emitter.startWork();
        LOG.info("[A2A] task state=WORKING taskId={}", taskId);

        String inputText = extractText(ctx);
        LOG.info("[A2A] input parsed taskId={} textChars={}", taskId, inputText.length());

        if (parentProjector.isRemoteContinuation(ctx)) {
            handleRemoteContinuation(ctx, emitter, taskId);
            LOG.info("[A2A] execute finish taskId={} durationMs={}",
                    taskId, (System.nanoTime() - startedNanos) / 1_000_000L);
            return;
        }

        AgentExecutionContext context = toExecutionContext(ctx);

        try {
            RouteDecision decision = consumeHandler(context, emitter, taskId, true);
            if (decision.remoteInvocation() != null) {
                handleRemoteInvocation(ctx, decision.remoteInvocation(), emitter, taskId);
            }
            LOG.info("[A2A] execute finish taskId={} durationMs={}",
                    taskId, (System.nanoTime() - startedNanos) / 1_000_000L);

        } catch (Exception e) {
            LOG.error("[A2A] execute failed taskId={} errorClass={} message={}",
                    taskId, e.getClass().getSimpleName(), e.getMessage(), e);
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            emitter.fail(failureMessage(emitter, "RUNTIME_ERROR", detail));
            LOG.info("[A2A] task state=FAILED taskId={}", taskId);
        }
    }

    @Override
    public void cancel(RequestContext ctx, AgentEmitter emitter) {
        LOG.info("[A2A] cancel requested taskId={}", ctx.getTaskId());
        emitter.cancel();
        if (remoteSupport != null && parentProjector.isRemoteContinuation(ctx)) {
            try {
                RemoteAgentInvocationService.RemoteRoute route = parentProjector.remoteRoute(ctx.getTask());
                remoteSupport.invocationService.cancel(new RemoteAgentInvocationService.RemoteTaskReference(
                        route.remoteAgentId(), route.remoteTaskId(), route.remoteContextId()));
            } catch (RuntimeException error) {
                LOG.warn("[A2A] remote cancel propagation failed taskId={} errorClass={} message={}",
                        ctx.getTaskId(), error.getClass().getSimpleName(), error.getMessage());
            }
        }
        LOG.info("[A2A] task state=CANCELED taskId={}", ctx.getTaskId());
    }

    private RouteDecision consumeHandler(AgentExecutionContext context, AgentEmitter emitter, String taskId,
            boolean remoteInvocationAllowed) {
        try (Stream<?> raw = handler.execute(context);
             Stream<AgentExecutionResult> results = handler.resultAdapter().adapt(raw)) {
            Iterator<AgentExecutionResult> iterator = results.iterator();
            while (iterator.hasNext()) {
                AgentExecutionResult result = iterator.next();
                LOG.info("[A2A] result taskId={} type={} outputChars={}",
                        taskId, result.type(),
                        result.outputContent() != null ? result.outputContent().length() : 0);
                RouteDecision decision = route(result, emitter, taskId, remoteInvocationAllowed);
                if (decision.stop()) {
                    return decision;
                }
            }
            return RouteDecision.terminal();
        }
    }

    private RouteDecision route(AgentExecutionResult result, AgentEmitter emitter, String taskId,
            boolean remoteInvocationAllowed) {
        switch (result.type()) {
            case OUTPUT -> {
                String text = outputText(result);
                LOG.info("[A2A] output stream taskId={} textChars={}", taskId, text.length());
                emitter.addArtifact(List.<Part<?>>of(new TextPart(text)));
                // State stays WORKING; more output may follow.
                return RouteDecision.continueRoute();
            }
            case COMPLETED -> {
                String text = outputText(result);
                if (!text.isBlank()) {
                    LOG.info("[A2A] complete with final output taskId={} textChars={}", taskId, text.length());
                    emitter.complete(emitter.newAgentMessage(List.<Part<?>>of(new TextPart(text)), null));
                } else {
                    emitter.complete();
                }
                LOG.info("[A2A] task state=COMPLETED taskId={}", taskId);
                return RouteDecision.terminal();
            }
            case FAILED -> {
                String code = result.errorCode() == null ? "RUNTIME_ERROR" : result.errorCode();
                String msg = result.errorMessage() == null ? code : result.errorMessage();
                LOG.warn("[A2A] task state=FAILED taskId={} code={} message={}", taskId, code, msg);
                emitter.fail(failureMessage(emitter, code, result.errorMessage()));
                return RouteDecision.terminal();
            }
            case INTERRUPTED -> {
                String prompt = result.prompt() == null ? "" : result.prompt();
                LOG.info("[A2A] task state=INPUT_REQUIRED taskId={} prompt={}", taskId, prompt);
                Message message = prompt.isBlank()
                        ? null
                        : emitter.newAgentMessage(List.<Part<?>>of(new TextPart(prompt)), null);
                emitter.requiresInput(message, false);
                return RouteDecision.terminal();
            }
            case REMOTE_INVOCATION -> {
                if (!remoteInvocationAllowed) {
                    emitter.fail(failureMessage(
                            emitter,
                            "NESTED_REMOTE_INVOCATION_UNSUPPORTED",
                            "remote A2A invocation after REMOTE_RESUME is not supported"));
                    return RouteDecision.terminal();
                }
                return RouteDecision.remote(result.remoteInvocation());
            }
        }
        throw new IllegalStateException("Unsupported result type: " + result.type());
    }

    private void handleRemoteInvocation(RequestContext requestContext,
            AgentExecutionResult.RemoteInvocation invocation, AgentEmitter emitter, String taskId) {
        if (remoteSupport == null) {
            emitter.fail(failureMessage(emitter, "REMOTE_NOT_CONFIGURED", "remote A2A invocation is not configured"));
            return;
        }
        List<RemoteAgentInvocationService.RemoteAgentResult> results =
                remoteSupport.invocationService.invoke(invocation,
                        result -> parentProjector.projectRemoteProgress(result, emitter));
        handleRemoteResults(requestContext, invocation, results, emitter, taskId);
    }

    private void handleRemoteContinuation(RequestContext ctx, AgentEmitter emitter, String taskId) {
        if (remoteSupport == null) {
            emitter.fail(failureMessage(emitter, "REMOTE_NOT_CONFIGURED", "remote A2A invocation is not configured"));
            return;
        }
        RemoteAgentInvocationService.RemoteRoute route;
        try {
            route = parentProjector.remoteRoute(ctx.getTask());
        } catch (IllegalArgumentException error) {
            emitter.fail(failureMessage(emitter, "REMOTE_ROUTE_METADATA_MISSING", error.getMessage()));
            return;
        }
        List<RemoteAgentInvocationService.RemoteAgentResult> results =
                remoteSupport.invocationService.resumeRemoteInput(route, extractText(ctx),
                        result -> parentProjector.projectRemoteProgress(result, emitter));
        AgentExecutionResult.RemoteInvocation invocation = parentProjector.remoteInvocation(route);
        handleRemoteResults(ctx, invocation, results, emitter, taskId);
    }

    private void handleRemoteResults(RequestContext requestContext, AgentExecutionResult.RemoteInvocation invocation,
            List<RemoteAgentInvocationService.RemoteAgentResult> results, AgentEmitter emitter, String taskId) {
        A2aParentTaskProjector.RemoteOutcome outcome =
                parentProjector.projectRemoteOutcome(invocation, results, emitter);
        if (outcome.waitingForRemoteInput()) {
            return;
        }
        AgentExecutionContext resumeContext =
                parentProjector.remoteResumeContext(requestContext, handler.agentId(), invocation, outcome.toolResult());
        consumeHandler(resumeContext, emitter, taskId, false);
    }

    private static String outputText(AgentExecutionResult result) {
        return result.outputContent() != null ? result.outputContent() : "";
    }

    /**
     * Builds an agent message carrying the failure reason so the A2A client sees
     * why the task failed instead of a bare FAILED status with no detail.
     */
    private static Message failureMessage(AgentEmitter emitter, String code, String detail) {
        String text = (detail == null || detail.isBlank()) ? code : code + ": " + detail;
        return emitter.newAgentMessage(List.<Part<?>>of(new TextPart(text)), null);
    }

    private AgentExecutionContext toExecutionContext(RequestContext ctx) {
        String text = extractText(ctx);
        List<Message> messages = List.of(Message.builder()
                .role(Message.Role.ROLE_USER)
                .parts(List.<Part<?>>of(new TextPart(text)))
                .build());
        return new AgentExecutionContext(
                new RuntimeIdentity(
                        metadata(ctx, "tenantId", "default"),
                        metadata(ctx, "userId", "system"),
                        ctx.getContextId() != null ? ctx.getContextId() : ctx.getTaskId(),
                        ctx.getTaskId(),
                        metadata(ctx, "agentId", handler.agentId())),
                "USER_MESSAGE", messages, Map.of());
    }

    private static String extractText(RequestContext ctx) {
        if (ctx.getMessage() == null || ctx.getMessage().parts() == null) return "";
        return ctx.getMessage().parts().stream()
                .filter(TextPart.class::isInstance)
                .map(TextPart.class::cast)
                .map(TextPart::text)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    private static String metadata(RequestContext ctx, String key, String fallback) {
        if ("tenantId".equals(key) && hasText(ctx.getTenant())) {
            return ctx.getTenant();
        }
        Map<String, Object> md = ctx.getMetadata();
        Object value = md == null ? null : md.get(key);
        return hasText(value) ? String.valueOf(value) : fallback;
    }

    private static boolean hasText(Object value) {
        return value != null && !String.valueOf(value).isBlank();
    }

    public static final class RemoteSupport {
        private final RemoteAgentInvocationService invocationService;

        public RemoteSupport(RemoteAgentInvocationService invocationService) {
            this.invocationService = Objects.requireNonNull(invocationService, "invocationService");
        }

        public static RemoteSupport forOutbound(RemoteAgentInvocationService.OutboundPort outboundPort) {
            return new RemoteSupport(new RemoteAgentInvocationService(outboundPort));
        }
    }

    private record RouteDecision(boolean stop, AgentExecutionResult.RemoteInvocation remoteInvocation) {
        static RouteDecision continueRoute() {
            return new RouteDecision(false, null);
        }

        static RouteDecision terminal() {
            return new RouteDecision(true, null);
        }

        static RouteDecision remote(AgentExecutionResult.RemoteInvocation invocation) {
            return new RouteDecision(true, invocation);
        }
    }
}
