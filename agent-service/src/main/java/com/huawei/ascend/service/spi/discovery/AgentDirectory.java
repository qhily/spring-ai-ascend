package com.huawei.ascend.service.spi.discovery;

import java.util.List;
import org.a2aproject.sdk.spec.AgentCard;

/**
 * Tenant-scoped agent discovery and route resolution over the registered
 * runtime population.
 *
 * <p>Tenant red-line: every query takes a {@code tenantId} and there is no
 * tenant-free overload; implementations must filter candidates tenant-first so
 * one tenant can never observe or route to another tenant's runtimes.
 *
 * <p>Route selection policy (capacity scoring, tie-breaking, session affinity)
 * is an implementation concern behind {@link #resolveRoute}; the
 * {@link RoutingContext} carries the keys a sticky implementation needs without
 * an SPI change.
 */
public interface AgentDirectory {

    AgentCard getAgentCard(String agentId, String tenantId);

    List<AgentCardSummary> listAgents(String tenantId);

    RuntimeRoute resolveRoute(String agentId, String tenantId, RoutingContext routingContext);
}
