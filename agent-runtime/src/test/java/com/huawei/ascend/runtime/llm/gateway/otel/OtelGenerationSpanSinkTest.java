package com.huawei.ascend.runtime.llm.gateway.otel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.huawei.ascend.runtime.llm.gateway.spi.GenerationSpanSink.GenerationSpan;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Collection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OtelGenerationSpanSinkTest {

    private final InMemorySpanExporter exporter = InMemorySpanExporter.create();
    private final OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
            .setTracerProvider(SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                    .build())
            .build();

    @AfterEach
    void shutDownSdk() {
        sdk.close();
    }

    @Test
    void emitsClientSpanWithGenAiAndLangfuseAttributesPlusTenantId() {
        new OtelGenerationSpanSink(sdk).emit(new GenerationSpan(
                "openai", "finance-chat", 1000, 500, 0.25, 321, "tenant-a"));

        assertThat(exporter.getFinishedSpanItems()).hasSize(1);
        SpanData span = exporter.getFinishedSpanItems().get(0);
        assertThat(span.getName()).isEqualTo("chat finance-chat");
        assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(span.getInstrumentationScopeInfo().getName())
                .isEqualTo(OtelGenerationSpanSink.TRACER_NAME);
        assertThat(span.getAttributes().get(AttributeKey.stringKey("gen_ai.system")))
                .isEqualTo("openai");
        assertThat(span.getAttributes().get(AttributeKey.stringKey("gen_ai.request.model")))
                .isEqualTo("finance-chat");
        assertThat(span.getAttributes().get(AttributeKey.longKey("gen_ai.usage.input_tokens")))
                .isEqualTo(1000);
        assertThat(span.getAttributes().get(AttributeKey.longKey("gen_ai.usage.output_tokens")))
                .isEqualTo(500);
        assertThat(span.getAttributes().get(AttributeKey.doubleKey("langfuse.cost_usd")))
                .isEqualTo(0.25);
        assertThat(span.getAttributes().get(AttributeKey.longKey("langfuse.latency_ms")))
                .isEqualTo(321);
        assertThat(span.getAttributes().get(AttributeKey.stringKey("tenant.id")))
                .isEqualTo("tenant-a");
        assertThat(span.getAttributes().size()).isEqualTo(7);
    }

    @Test
    void unpricedCallOmitsCostAttributeEntirely() {
        new OtelGenerationSpanSink(sdk).emit(new GenerationSpan(
                "openai", "finance-chat", 10, 5, null, 12, "tenant-a"));

        SpanData span = exporter.getFinishedSpanItems().get(0);
        assertThat(span.getAttributes().get(AttributeKey.doubleKey("langfuse.cost_usd"))).isNull();
        assertThat(span.getAttributes().size()).isEqualTo(6);
    }

    /** The sink runs post-call: the span is backdated so its duration is the upstream latency. */
    @Test
    void spanDurationMirrorsReportedLatency() {
        new OtelGenerationSpanSink(sdk).emit(new GenerationSpan(
                "openai", "finance-chat", 10, 5, null, 321, "tenant-a"));

        SpanData span = exporter.getFinishedSpanItems().get(0);
        assertThat(span.getEndEpochNanos() - span.getStartEpochNanos())
                .isEqualTo(321 * 1_000_000L);
    }

    /** Observer-only seam: a broken span pipeline must never fail the LLM call path. */
    @Test
    void throwingExporterDoesNotPropagateOutOfEmit() {
        SpanExporter throwingExporter = new SpanExporter() {
            @Override
            public CompletableResultCode export(Collection<SpanData> spans) {
                throw new IllegalStateException("exporter down");
            }

            @Override
            public CompletableResultCode flush() {
                return CompletableResultCode.ofSuccess();
            }

            @Override
            public CompletableResultCode shutdown() {
                return CompletableResultCode.ofSuccess();
            }
        };
        try (OpenTelemetrySdk throwingSdk = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(throwingExporter))
                        .build())
                .build()) {
            OtelGenerationSpanSink sink = new OtelGenerationSpanSink(throwingSdk);
            assertThatCode(() -> sink.emit(new GenerationSpan(
                    "openai", "finance-chat", 10, 5, null, 12, "tenant-a")))
                    .doesNotThrowAnyException();
        }
    }
}
