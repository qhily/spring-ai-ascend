package com.huawei.ascend.examples.runtime.middleware.memory.inmemory;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MemoryInMemoryExampleTest {

    @Test
    void inMemoryMemoryProviderWorksThroughOpenJiuwenHandlerExecution() {
        InMemoryMemoryProvider provider = new InMemoryMemoryProvider();
        AgentExecutionContext first = MiddlewareTestFixtures.context("memory-state-a");
        AgentExecutionContext second = MiddlewareTestFixtures.context("memory-state-b");
        provider.save(first, List.of(record("the user prefers green tea")));
        provider.save(second, List.of(record("the user prefers black coffee")));
        SampleModelClient.ensureRegistered();
        SampleMemoryOpenJiuwenHandler handler = new SampleMemoryOpenJiuwenHandler("openjiuwen-simple-agent");
        handler.setOpenJiuwenRailFactories(List.of(context -> handler.memoryRail(context, provider)));

        List<?> rawResults = handler.execute(first).toList();

        assertThat(rawResults).singleElement().isEqualTo(Map.of("result_type", "answer", "output", "pong"));
        assertThat(provider.search(first, "black coffee", 3)).isEmpty();
        assertThat(provider.search(first, "green tea", 3))
                .first()
                .satisfies(hit -> assertThat(hit.content()).contains("green tea"));
        assertThat(provider.records(first))
                .extracting(MemoryProvider.MemoryRecord::content)
                .contains("the user prefers green tea", "green tea", "pong");
        assertThat(SampleModelClient.capturedMessages())
                .anySatisfy(message -> assertThat(message).contains("the user prefers green tea"));
    }

    private static MemoryProvider.MemoryRecord record(String content) {
        return new MemoryProvider.MemoryRecord(null, "assistant", content, Map.of("source", "test"));
    }
}
