package com.huawei.ascend.runtime.engine.openjiuwen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.ReActAgent;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class OpenJiuwenReActHandlerTest {

    @Test
    void agentIdIsSetFromConstructor() {
        StubReActHandler handler = new StubReActHandler("my-agent");

        assertThat(handler.agentId()).isEqualTo("my-agent");
    }

    @Test
    void createOpenJiuwenAgentDelegatesToCreateReActAgent() {
        ReActAgent stub = mock(ReActAgent.class);
        StubReActHandler handler = new StubReActHandler("my-agent", stub);

        AgentExecutionContext context = context();
        BaseAgent result = handler.createOpenJiuwenAgent(context);

        assertThat(result).isSameAs(stub);
        assertThat(handler.createReActAgentCalled).isTrue();
    }

    @Test
    void executeCompletesViaReActAgentWiring() {
        ReActAgent stub = mock(ReActAgent.class);
        OverridingRunHandler handler = new OverridingRunHandler("agent", stub);

        List<?> results = handler.execute(context()).toList();

        assertThat(results).isEqualTo(List.of(Map.of("result_type", "answer", "output", "pong")));
    }

    private static AgentExecutionContext context() {
        return new AgentExecutionContext(
                new RuntimeIdentity("tenant", "user", "session", "task", "agent"),
                "USER_MESSAGE",
                List.of(RuntimeMessage.user("ping")),
                Map.of());
    }

    /** Minimal subclass: implements only createReActAgent, takes the agent as a constructor arg for testability. */
    private static class StubReActHandler extends OpenJiuwenReActHandler {
        private final ReActAgent agent;
        boolean createReActAgentCalled;

        StubReActHandler(String agentId) {
            super(agentId);
            this.agent = mock(ReActAgent.class);
        }

        StubReActHandler(String agentId, ReActAgent agent) {
            super(agentId);
            this.agent = agent;
        }

        @Override
        protected ReActAgent createReActAgent(AgentExecutionContext context) {
            createReActAgentCalled = true;
            return agent;
        }
    }

    /** Overrides runOpenJiuwenAgent to avoid real SDK invocation; confirms delegation wiring end-to-end. */
    private static final class OverridingRunHandler extends StubReActHandler {
        OverridingRunHandler(String agentId, ReActAgent agent) {
            super(agentId, agent);
        }

        @Override
        protected Object runOpenJiuwenAgent(BaseAgent agent, Object input, String conversationId) {
            return Map.of("result_type", "answer", "output", "pong");
        }
    }
}
