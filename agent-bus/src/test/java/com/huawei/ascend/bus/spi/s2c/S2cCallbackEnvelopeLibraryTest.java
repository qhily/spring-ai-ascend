package com.huawei.ascend.bus.spi.s2c;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Library-mode SPI conformance test for S2cCallbackEnvelope + S2cCallbackResponse
 * (Rule R-M.d + ADR-0074; package move to runtime.s2c.spi in v2.0.0-rc3).
 *
 * <p>Pure JUnit Jupiter — no Spring context, no transport. Asserts the seven
 * mandatory-field invariants validated on construction (six original fields +
 * {@code tenantId} added in the Stage 2 tenant migration, Rule R-C.c) + the
 * W3C 32-char lowercase-hex traceId rule added in v2.0.0-rc3
 * (cross-constraint audit α-5).
 *
 * <p>Part of the Rule D-3.b evidence layer + Rule R-D.a.b TCK-promotion holding tank.
 */
class S2cCallbackEnvelopeLibraryTest {

    private static final String VALID_TRACE_ID = "0123456789abcdef0123456789abcdef";  // 32 lowercase hex
    private static final String VALID_TENANT_ID = "tenant-acme";
    private static final String VALID_CAPABILITY_REF = "cap.test";
    private static final Object VALID_PAYLOAD = "payload";

    @Test
    void envelope_constructor_rejects_null_callback_id() {
        assertThatThrownBy(() -> new S2cCallbackEnvelope(
                null,
                VALID_TENANT_ID,
                UUID.randomUUID(),
                VALID_CAPABILITY_REF,
                VALID_PAYLOAD,
                VALID_TRACE_ID,
                UUID.randomUUID(),
                null,
                null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("callbackId is required");
    }

    @Test
    void envelope_constructor_rejects_null_tenant_id() {
        assertThatThrownBy(() -> new S2cCallbackEnvelope(
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                VALID_CAPABILITY_REF,
                VALID_PAYLOAD,
                VALID_TRACE_ID,
                UUID.randomUUID(),
                null,
                null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tenantId is required");
    }

    @Test
    void envelope_constructor_rejects_blank_tenant_id() {
        assertThatThrownBy(() -> new S2cCallbackEnvelope(
                UUID.randomUUID(),
                "   ",
                UUID.randomUUID(),
                VALID_CAPABILITY_REF,
                VALID_PAYLOAD,
                VALID_TRACE_ID,
                UUID.randomUUID(),
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId must not be blank");
    }

    @Test
    void envelope_constructor_rejects_null_server_run_id() {
        assertThatThrownBy(() -> new S2cCallbackEnvelope(
                UUID.randomUUID(),
                VALID_TENANT_ID,
                null,
                VALID_CAPABILITY_REF,
                VALID_PAYLOAD,
                VALID_TRACE_ID,
                UUID.randomUUID(),
                null,
                null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("serverRunId is required");
    }

    @Test
    void envelope_constructor_rejects_blank_capability_ref() {
        assertThatThrownBy(() -> new S2cCallbackEnvelope(
                UUID.randomUUID(),
                VALID_TENANT_ID,
                UUID.randomUUID(),
                "  ",
                VALID_PAYLOAD,
                VALID_TRACE_ID,
                UUID.randomUUID(),
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capabilityRef must not be blank");
    }

    @Test
    void envelope_constructor_rejects_trace_id_wrong_length() {
        String shortTrace = "abc123";
        assertThatThrownBy(() -> new S2cCallbackEnvelope(
                UUID.randomUUID(),
                VALID_TENANT_ID,
                UUID.randomUUID(),
                VALID_CAPABILITY_REF,
                VALID_PAYLOAD,
                shortTrace,
                UUID.randomUUID(),
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32");
    }

    @Test
    void envelope_constructor_rejects_null_trace_id() {
        assertThatThrownBy(() -> new S2cCallbackEnvelope(
                UUID.randomUUID(),
                VALID_TENANT_ID,
                UUID.randomUUID(),
                VALID_CAPABILITY_REF,
                VALID_PAYLOAD,
                null,
                UUID.randomUUID(),
                null,
                null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("traceId is required");
    }

    @Test
    void envelope_constructor_accepts_minimal_valid_input() {
        UUID cb = UUID.randomUUID();
        UUID sr = UUID.randomUUID();
        UUID idk = UUID.randomUUID();
        S2cCallbackEnvelope env = new S2cCallbackEnvelope(
                cb, VALID_TENANT_ID, sr, VALID_CAPABILITY_REF, VALID_PAYLOAD, VALID_TRACE_ID, idk, null, null);
        assertThat(env.callbackId()).isEqualTo(cb);
        assertThat(env.tenantId()).isEqualTo(VALID_TENANT_ID);
        assertThat(env.serverRunId()).isEqualTo(sr);
        assertThat(env.capabilityRef()).isEqualTo(VALID_CAPABILITY_REF);
        assertThat(env.idempotencyKey()).isEqualTo(idk);
        assertThat(env.deadline()).isNull();
        // null requestAttributes is normalised to an empty immutable Map.
        assertThat(env.requestAttributes()).isEmpty();
    }

    @Test
    void envelope_attributes_map_is_defensive_copy() {
        java.util.HashMap<String, Object> mutable = new java.util.HashMap<>();
        mutable.put("k1", "v1");
        S2cCallbackEnvelope env = new S2cCallbackEnvelope(
                UUID.randomUUID(), VALID_TENANT_ID, UUID.randomUUID(), "cap", "p", VALID_TRACE_ID,
                UUID.randomUUID(), Instant.now(), mutable);
        mutable.put("k2", "v2");  // mutate after construction
        assertThat(env.requestAttributes()).containsOnlyKeys("k1");
    }

    @Test
    void response_ok_factory_produces_well_formed_envelope() {
        UUID cb = UUID.randomUUID();
        S2cCallbackResponse r = S2cCallbackResponse.ok(cb, VALID_TRACE_ID, "result");
        assertThat(r.callbackId()).isEqualTo(cb);
        assertThat(r.outcome()).isEqualTo(S2cCallbackResponse.Outcome.OK);
        assertThat(r.errorCode()).isNull();
        assertThat(r.errorMessage()).isNull();
    }

    @Test
    void response_error_constructor_requires_error_code() {
        UUID cb = UUID.randomUUID();
        // Direct constructor with ERROR outcome + null errorCode -> NPE.
        assertThatThrownBy(() -> new S2cCallbackResponse(
                cb, S2cCallbackResponse.Outcome.ERROR, VALID_TRACE_ID,
                null, null, "msg", Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("errorCode");
    }

    @Test
    void response_outcome_enum_is_closed_to_three_values() {
        assertThat(S2cCallbackResponse.Outcome.values())
                .containsExactly(
                        S2cCallbackResponse.Outcome.OK,
                        S2cCallbackResponse.Outcome.ERROR,
                        S2cCallbackResponse.Outcome.TIMEOUT);
    }

    @Test
    void response_timeout_factory_carries_no_payload() {
        UUID cb = UUID.randomUUID();
        S2cCallbackResponse r = S2cCallbackResponse.timeout(cb, VALID_TRACE_ID);
        assertThat(r.outcome()).isEqualTo(S2cCallbackResponse.Outcome.TIMEOUT);
        assertThat(r.responsePayload()).isNull();
        assertThat(r.errorCode()).isNull();
    }
}
