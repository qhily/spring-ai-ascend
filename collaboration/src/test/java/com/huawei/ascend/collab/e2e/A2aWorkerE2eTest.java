package com.huawei.ascend.collab.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.ascend.collab.a2a.A2aWorker;
import com.huawei.ascend.collab.core.CollaborationResult;
import com.huawei.ascend.collab.core.Coordinator;
import com.huawei.ascend.collab.core.SubTask;
import com.huawei.ascend.collab.core.TaskToken;
import com.huawei.ascend.collab.core.WorkResult;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Real A2A round-trip: boots the no-LLM {@link DeterministicEchoAgent} on a random
 * port and drives {@link A2aWorker} against it over the actual A2A JSON-RPC wire —
 * proving the engine→A2A bridge works end to end, deterministically, no API key.
 *
 * <p>DISABLED pending a repo-wide a2a-sdk version fix: the root pom declares
 * {@code a2a-sdk 1.0.0.Final} and agent-runtime's source uses .Final-only client
 * APIs, but only {@code 1.0.0.CR1} is in the local {@code .m2}. With a CR1 client
 * talking to the .Final-built runtime, the streaming endpoint yields zero events
 * (wire-format drift). The agent boots and the path resolves (no 404); the gap is
 * purely the CR1↔Final SSE mismatch. Re-enable once the platform pins one version
 * and it is present in the repo's resolver. The agent/test scaffolding is kept so
 * this verifies immediately when that lands.
 */
@Disabled("blocked by repo-wide a2a-sdk CR1/.Final version drift — see class javadoc")
class A2aWorkerE2eTest {

    private static ConfigurableApplicationContext boot() {
        return new SpringApplicationBuilder(DeterministicEchoAgent.class)
                .run("--server.port=0", "--spring.main.web-application-type=servlet",
                        "--logging.level.root=WARN");
    }

    private static String baseUrl(ConfigurableApplicationContext ctx) {
        Integer port = ctx.getEnvironment().getProperty("local.server.port", Integer.class);
        assertNotNull(port, "local.server.port available");
        return "http://localhost:" + port; // agent base; card resolved from /.well-known/agent-card.json
    }

    @Test
    void a2aWorkerCompletesOverTheWire() {
        try (ConfigurableApplicationContext ctx = boot()) {
            A2aWorker worker = new A2aWorker("echo-worker", Set.of("echo"), baseUrl(ctx));

            TaskToken token = TaskToken.issue("t1", "echo", "echo-worker", "demo-tenant",
                    UUID.randomUUID(), 30_000, System.currentTimeMillis());
            WorkResult r = worker.execute(SubTask.of("t1", "echo", "hello world"), token);

            assertEquals(WorkResult.Status.COMPLETED, r.status(),
                    "remote echo agent completes; detail=" + r.detail() + " output=" + r.output());
            assertNotNull(r.output(), "output present");
        }
    }

    @Test
    void coordinatorOrchestratesARealA2aAgent() {
        try (ConfigurableApplicationContext ctx = boot()) {
            A2aWorker worker = new A2aWorker("echo-worker", Set.of("echo"), baseUrl(ctx));
            Coordinator coordinator = new Coordinator(List.of(worker));

            CollaborationResult result = coordinator.run(List.of(
                    SubTask.of("t1", "echo", "task one"),
                    SubTask.of("t2", "echo", "task two")));

            assertTrue(result.allCompleted(), "both tasks complete via real A2A: " + result.outcomes());
        }
    }
}
