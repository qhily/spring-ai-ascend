package com.huawei.ascend.service.testsupport;

import java.util.List;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentProvider;
import org.a2aproject.sdk.spec.TransportProtocol;

/** Builds minimal valid A2A agent cards for registry and grant tests. */
public final class AgentCards {

    private AgentCards() {
    }

    public static AgentCard agentCard(String agentId) {
        return AgentCard.builder()
                .name(agentId)
                .description(agentId + " A2A runtime")
                .url("/a2a")
                .version("1.0.0")
                .provider(new AgentProvider("spring-ai-ascend", "http://localhost:8080"))
                .capabilities(AgentCapabilities.builder()
                        .streaming(true)
                        .pushNotifications(true)
                        .extendedAgentCard(false)
                        .build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of())
                .supportedInterfaces(List.of(new AgentInterface(
                        TransportProtocol.JSONRPC.asString(), "/a2a")))
                .preferredTransport(TransportProtocol.JSONRPC.asString())
                .build();
    }
}
