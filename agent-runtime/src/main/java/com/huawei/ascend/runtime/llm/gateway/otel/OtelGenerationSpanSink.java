package com.huawei.ascend.runtime.llm.gateway.otel;

import com.huawei.ascend.runtime.llm.gateway.spi.GenerationSpanSink;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenTelemetry bridge for GENERATION records: each LLM invocation becomes a
 * CLIENT span named {@code chat <model>} carrying the {@code gen_ai.*} semconv
 * attributes, the {@code langfuse.*} platform attributes, and the mandatory
 * {@code tenant.id} attribution. An unpriced call omits {@code langfuse.cost_usd}
 * entirely — a fabricated zero would read as "free".
 *
 * <p>The sink runs after the upstream call completed, so the span is backdated:
 * start = now − latency, end = now, making the span duration mirror the measured
 * upstream latency. It parents onto the current OTel context and invents no
 * propagation of its own. Observer-only seam: emit never throws.
 */
public final class OtelGenerationSpanSink implements GenerationSpanSink {

    public static final String TRACER_NAME = "spring-ai-ascend-llm-gateway";

    private static final AttributeKey<String> GEN_AI_SYSTEM =
            AttributeKey.stringKey("gen_ai.system");
    private static final AttributeKey<String> GEN_AI_REQUEST_MODEL =
            AttributeKey.stringKey("gen_ai.request.model");
    private static final AttributeKey<Long> GEN_AI_USAGE_INPUT_TOKENS =
            AttributeKey.longKey("gen_ai.usage.input_tokens");
    private static final AttributeKey<Long> GEN_AI_USAGE_OUTPUT_TOKENS =
            AttributeKey.longKey("gen_ai.usage.output_tokens");
    private static final AttributeKey<Double> LANGFUSE_COST_USD =
            AttributeKey.doubleKey("langfuse.cost_usd");
    private static final AttributeKey<Long> LANGFUSE_LATENCY_MS =
            AttributeKey.longKey("langfuse.latency_ms");
    private static final AttributeKey<String> TENANT_ID =
            AttributeKey.stringKey("tenant.id");

    private static final Logger log = LoggerFactory.getLogger(OtelGenerationSpanSink.class);

    private final Tracer tracer;

    public OtelGenerationSpanSink(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer(TRACER_NAME);
    }

    @Override
    public void emit(GenerationSpan span) {
        try {
            Instant end = Instant.now();
            SpanBuilder builder = tracer.spanBuilder("chat " + span.genAiRequestModel())
                    .setSpanKind(SpanKind.CLIENT)
                    .setParent(Context.current())
                    .setStartTimestamp(end.minusMillis(span.latencyMs()))
                    .setAttribute(GEN_AI_SYSTEM, span.genAiSystem())
                    .setAttribute(GEN_AI_REQUEST_MODEL, span.genAiRequestModel())
                    .setAttribute(GEN_AI_USAGE_INPUT_TOKENS, span.inputTokens())
                    .setAttribute(GEN_AI_USAGE_OUTPUT_TOKENS, span.outputTokens())
                    .setAttribute(LANGFUSE_LATENCY_MS, span.latencyMs())
                    .setAttribute(TENANT_ID, span.tenantId());
            if (span.costUsd() != null) {
                builder.setAttribute(LANGFUSE_COST_USD, span.costUsd());
            }
            builder.startSpan().end(end);
        } catch (RuntimeException e) {
            log.warn("GENERATION span emission failed; the LLM call itself is unaffected", e);
        }
    }
}
