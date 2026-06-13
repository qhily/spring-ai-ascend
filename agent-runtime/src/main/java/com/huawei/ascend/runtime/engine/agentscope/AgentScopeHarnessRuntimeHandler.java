package com.huawei.ascend.runtime.engine.agentscope;

import java.util.Objects;
import java.util.stream.Stream;

public final class AgentScopeHarnessRuntimeHandler extends AbstractAgentScopeRuntimeHandler {

    private final AgentScopeAgent harnessAgent;

    public AgentScopeHarnessRuntimeHandler(String agentId, AgentScopeAgent harnessAgent) {
        super(agentId, agentId, "AgentScope Harness agent hosted by agent-runtime.");
        this.harnessAgent = Objects.requireNonNull(harnessAgent, "harnessAgent");
    }

    public AgentScopeHarnessRuntimeHandler(
            String agentId,
            String name,
            String description,
            AgentScopeAgent harnessAgent) {
        super(agentId, name, description);
        this.harnessAgent = Objects.requireNonNull(harnessAgent, "harnessAgent");
    }

    @Override
    protected Stream<?> streamAgentScopeEvents(AgentScopeInvocation invocation) {
        return harnessAgent.streamEvents(invocation);
    }
}
