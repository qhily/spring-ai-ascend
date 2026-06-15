package com.huawei.ascend.examples.runtime.middleware.memory.mem0;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class MemoryMem0Application {
    public static void main(String[] args) {
        SpringApplication.run(MemoryMem0Application.class, args);
    }
}

@Configuration(proxyBeanMethods = false)
class MemoryMem0Configuration {
    private static final String AGENT_ID = "middleware-memory-mem0-agent";

    @Bean
    Mem0RestMemoryProvider memoryProvider(
            @Value("${sample.mem0.base-url:${SAA_SAMPLE_MEM0_BASE_URL:http://localhost:8000}}") String baseUrl,
            @Value("${sample.mem0.api-key:${SAA_SAMPLE_MEM0_API_KEY:}}") String apiKey,
            @Value("${sample.mem0.infer-on-save:${SAA_SAMPLE_MEM0_INFER_ON_SAVE:false}}") boolean inferOnSave,
            @Value("${sample.mem0.api-mode:${SAA_SAMPLE_MEM0_API_MODE:oss}}") String apiMode) {
        return new Mem0RestMemoryProvider(baseUrl, apiKey, inferOnSave, apiMode);
    }

    @Bean
    SampleMem0OpenJiuwenHandler sampleHandler(Mem0RestMemoryProvider memoryProvider) {
        SampleMem0OpenJiuwenHandler handler = new SampleMem0OpenJiuwenHandler(AGENT_ID);
        handler.setOpenJiuwenRailFactories(List.of(context -> handler.memoryRail(context, memoryProvider)));
        return handler;
    }

    @Bean
    Object sampleModelFactoryRegistration() {
        Mem0SampleModelClient.ensureRegistered();
        return new Object();
    }
}

@RestController
class MemoryMem0Controller {
    private final Mem0RestMemoryProvider memoryProvider;
    private final SampleMem0OpenJiuwenHandler handler;

    MemoryMem0Controller(Mem0RestMemoryProvider memoryProvider, SampleMem0OpenJiuwenHandler handler) {
        this.memoryProvider = memoryProvider;
        this.handler = handler;
    }

    @PostMapping("/sample/memory/remember")
    Map<String, Object> remember(@RequestBody MemoryRequest request) {
        AgentExecutionContext context = context(request.stateKey(), request.text());
        memoryProvider.save(context, List.of(new MemoryProvider.MemoryRecord(
                null, "assistant", request.text(), Map.of("source", "curl"))));
        return Map.of("stateKey", context.getAgentStateKey(), "saved", true);
    }

    @PostMapping("/sample/memory/ask")
    Map<String, Object> ask(@RequestBody MemoryRequest request) {
        AgentExecutionContext context = context(request.stateKey(), request.text());
        List<?> rawResults = handler.execute(context).toList();
        return Map.of(
                "stateKey", context.getAgentStateKey(),
                "query", request.text(),
                "rawResults", rawResults,
                "modelMessages", Mem0SampleModelClient.capturedMessages(),
                "hits", memoryProvider.search(context, request.text(), 5));
    }

    @GetMapping("/sample/memory/search")
    Map<String, Object> search(
            @RequestParam(defaultValue = "demo-user") String stateKey,
            @RequestParam(defaultValue = "green tea") String query) {
        AgentExecutionContext context = context(stateKey, query);
        return Map.of("stateKey", stateKey, "query", query, "hits", memoryProvider.search(context, query, 5));
    }

    private static AgentExecutionContext context(String stateKey, String text) {
        RuntimeIdentity identity =
                new RuntimeIdentity("sample-tenant", "sample-user", "sample-session", "sample-task",
                        "middleware-memory-mem0-agent");
        return new AgentExecutionContext(identity, "USER_MESSAGE",
                List.of(RuntimeMessage.user(text == null ? "" : text)), Map.of(), normalizeStateKey(stateKey), null);
    }

    private static String normalizeStateKey(String stateKey) {
        return stateKey == null || stateKey.isBlank() ? "demo-user" : stateKey;
    }

    record MemoryRequest(String stateKey, String text) {
    }
}

final class SampleMem0OpenJiuwenHandler extends OpenJiuwenAgentRuntimeHandler {
    private static final String MODEL_PROVIDER = "sample-memory-mem0-model";
    private final String agentId;
    private List<Function<AgentExecutionContext, AgentRail>> railFactories = List.of();

    SampleMem0OpenJiuwenHandler(String agentId) {
        super(agentId);
        this.agentId = agentId;
    }

    void setOpenJiuwenRailFactories(List<Function<AgentExecutionContext, AgentRail>> railFactories) {
        this.railFactories = List.copyOf(Objects.requireNonNull(railFactories, "railFactories"));
    }

    AgentRail memoryRail(AgentExecutionContext context, MemoryProvider provider) {
        return memoryRuntimeRail(context, provider);
    }

    @Override
    protected List<AgentRail> openJiuwenRails(AgentExecutionContext context) {
        return railFactories.stream().map(factory -> factory.apply(context)).toList();
    }

    @Override
    protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
        ReActAgent agent = new ReActAgent(AgentCard.builder()
                .id(agentId)
                .name(agentId)
                .description("Mem0 MemoryProvider curl example")
                .build());
        ReActAgentConfig config = ReActAgentConfig.builder()
                .promptTemplate(List.of(Map.of("role", "system", "content",
                        "You are a deterministic middleware memory example. Reply exactly pong.")))
                .maxIterations(1)
                .build()
                .configureModelClient(MODEL_PROVIDER, "sample-key", "http://localhost", "sample-model", false);
        agent.configure(config);
        return agent;
    }

    @Override
    protected Object runOpenJiuwenAgent(BaseAgent agent, Object input, String conversationId) {
        Iterator<Object> output = agent.stream(input, AgentSessionApi.create(conversationId, null, agent.getCard()),
                List.of(StreamMode.OUTPUT));
        output.forEachRemaining(ignored -> { });
        return Map.of("result_type", "answer", "output", "pong");
    }
}

final class Mem0SampleModelClient extends BaseModelClient {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);
    private static final CopyOnWriteArrayList<String> CAPTURED_MESSAGES = new CopyOnWriteArrayList<>();

    private Mem0SampleModelClient(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
        super(modelConfig, clientConfig);
    }

    static void ensureRegistered() {
        if (REGISTERED.compareAndSet(false, true)) {
            Model.registerFactory(new Model.ModelClientFactory() {
                @Override
                public String providerName() {
                    return "sample-memory-mem0-model";
                }

                @Override
                public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
                    return new Mem0SampleModelClient(modelConfig, clientConfig);
                }
            });
        }
    }

    static List<String> capturedMessages() {
        return List.copyOf(CAPTURED_MESSAGES);
    }

    @Override
    public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model,
            Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout, Map<String, Object> kwargs) {
        CAPTURED_MESSAGES.clear();
        if (messages instanceof List<?> list) {
            list.stream()
                    .filter(BaseMessage.class::isInstance)
                    .map(BaseMessage.class::cast)
                    .map(BaseMessage::getContentAsString)
                    .forEach(CAPTURED_MESSAGES::add);
        }
        return new AssistantMessage("pong");
    }

    @Override
    public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP,
            String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
            Map<String, Object> kwargs) {
        return List.<AssistantMessageChunk>of().iterator();
    }

    @Override
    public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size,
            String negativePrompt, int n, boolean promptExtend, boolean watermark, int seed,
            Map<String, Object> kwargs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice,
            String languageType, Map<String, Object> kwargs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl,
            String model, String size, String resolution, int duration, boolean promptExtend, boolean watermark,
            String negativePrompt, Integer seed, Map<String, Object> kwargs) {
        throw new UnsupportedOperationException();
    }
}
