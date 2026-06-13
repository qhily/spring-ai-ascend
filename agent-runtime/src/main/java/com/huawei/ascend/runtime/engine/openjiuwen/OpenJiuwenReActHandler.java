package com.huawei.ascend.runtime.engine.openjiuwen;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.ReActAgent;

/**
 * Convenience base class for openJiuwen {@link ReActAgent} handlers. Subclasses implement
 * only {@link #createReActAgent(AgentExecutionContext)} and the {@code agentId} constructor
 * call; all runtime-facing execute flow, rail installation, and result mapping are inherited
 * from {@link OpenJiuwenAgentRuntimeHandler}.
 *
 * <p>Use this class instead of subclassing {@link OpenJiuwenAgentRuntimeHandler} directly when
 * the concrete agent is a {@link ReActAgent}.
 */
public abstract class OpenJiuwenReActHandler extends OpenJiuwenAgentRuntimeHandler {

    protected OpenJiuwenReActHandler(String agentId) {
        super(agentId);
    }

    protected OpenJiuwenReActHandler(String agentId, OpenJiuwenMessageAdapter messageConverter) {
        super(agentId, messageConverter);
    }

    /**
     * Build the concrete {@link ReActAgent} instance for this execution.
     *
     * <p>Implementations typically construct the agent once per execution. See
     * {@link OpenJiuwenAgentRuntimeHandler#openJiuwenRails(AgentExecutionContext)} for caching
     * caveats when returning the same instance across calls.
     */
    protected abstract ReActAgent createReActAgent(AgentExecutionContext context);

    @Override
    protected final BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
        return createReActAgent(context);
    }
}
