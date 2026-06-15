package com.huawei.ascend.examples.runtime.middleware.memory.mem0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("manual")
class MemoryMem0ExampleTest {
    @Test
    void mem0RestMemoryProviderWorksThroughOpenJiuwenHandlerExecution() {
        String baseUrl = System.getenv("SAA_SAMPLE_MEM0_BASE_URL");
        assumeTrue(hasText(baseUrl), "Set SAA_SAMPLE_MEM0_BASE_URL to run the real Mem0 example");
        Mem0RestMemoryProvider provider = new Mem0RestMemoryProvider(
                baseUrl, System.getenv("SAA_SAMPLE_MEM0_API_KEY"), false, envOrDefault("SAA_SAMPLE_MEM0_API_MODE", "oss"));
        AgentExecutionContext context = MiddlewareTestFixtures.context("mem0-state-" + System.nanoTime());
        provider.save(context, List.of(new MemoryProvider.MemoryRecord(null, "assistant",
                "the user prefers green tea", Map.of("source", "test"))));
        Mem0SampleModelClient.ensureRegistered();
        SampleMem0OpenJiuwenHandler handler = new SampleMem0OpenJiuwenHandler("openjiuwen-simple-agent");
        handler.setOpenJiuwenRailFactories(List.of(execution -> handler.memoryRail(execution, provider)));

        List<?> rawResults = handler.execute(context).toList();

        assertThat(rawResults).singleElement().isEqualTo(Map.of("result_type", "answer", "output", "pong"));
        assertThat(provider.search(context, "green tea", 5))
                .extracting(MemoryProvider.MemoryHit::content)
                .anySatisfy(content -> assertThat(content).containsIgnoringCase("green tea"));
        assertThat(Mem0SampleModelClient.capturedMessages())
                .anySatisfy(message -> assertThat(message).containsIgnoringCase("green tea"));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return hasText(value) ? value : fallback;
    }
}
