package com.huawei.ascend.bus.spi.s2c;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Server-to-Client (S2C) capability invocation request envelope.
 *
 * <p>Schema authority: {@code docs/contracts/s2c-callback.v1.yaml#request}.
 * The Phase 3a cross-rule audit matrix (see
 * {@code docs/logs/reviews/2026-05-16-engine-contract-structural-response.en.md} §5.2)
 * defines six mandatory fields that MUST appear on every S2C envelope at every
 * layer (envelope class, transport SPI, response validator, integration test,
 * audit log). The record below validates those six on construction, plus the
 * seventh mandatory field — {@code tenantId} — added in the Stage 2 tenant
 * migration (see {@code docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage1-review-and-stage2-plan.md}).
 *
 * <p>Stage 2 tenant migration: {@code tenantId} is now an in-band required
 * field on the envelope itself (Rule R-C.c — Contract Spine Completeness),
 * symmetric with {@link com.huawei.ascend.bus.spi.ingress.IngressEnvelope#tenantId()}.
 * The prior ADR-0074 §Consequences design resolved tenant out-of-band via the
 * {@code S2cCallbackTransport} registry binding at the wrapping Run boundary;
 * that path remains as a COMPATIBILITY ONLY adjunct and MUST NOT substitute for
 * the in-band tenant scope carried by this envelope.
 *
 * <p>Lives in {@code com.huawei.ascend.bus.spi.s2c} (moved from the old
 * runtime S2C package per the cross-constraint audit) so the SPI literally imports
 * only {@code java.*} + same-spi-package siblings, restoring exact agreement
 * with the ARCHITECTURE.md SPI-purity prose.
 *
 * <p>Authority: ADR-0074; CLAUDE.md Rule 46 (S2C Callback Envelope + Lifecycle Bound).
 */
// scope: process-internal — transport envelope; tenantId is the in-band required
// tenant scope (Rule R-C.c); the S2cCallbackTransport registry binding remains as
// a COMPATIBILITY ONLY path and MUST NOT substitute for this in-band scope
// (Stage 2 migration of ADR-0074 §Consequences).
public record S2cCallbackEnvelope(
        UUID callbackId,            // primary correlation key
        String tenantId,            // Rule R-C.c contract spine — required tenant scope (Stage 2 migration)
        UUID serverRunId,           // suspending Run id
        String capabilityRef,       // declared client capability id
        Object requestPayload,      // opaque, validated by capability-specific schema (W3)
        String traceId,             // W3C 32-char lowercase hex; MUST equal suspending Run.traceId
        UUID idempotencyKey,        // client may retry; runtime dedupes within window
        Instant deadline,           // absolute deadline; null means "use skill-capacity timeout_ms"
        Map<String, Object> requestAttributes  // optional capability-specific extras
) {
    public S2cCallbackEnvelope {
        Objects.requireNonNull(callbackId, "callbackId is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        Objects.requireNonNull(serverRunId, "serverRunId is required");
        Objects.requireNonNull(capabilityRef, "capabilityRef is required");
        if (capabilityRef.isBlank()) {
            throw new IllegalArgumentException("capabilityRef must not be blank");
        }
        Objects.requireNonNull(requestPayload, "requestPayload is required");
        requireLowerHex32(traceId, "traceId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
        // deadline + requestAttributes are optional
        requestAttributes = requestAttributes == null ? Map.of() : Map.copyOf(requestAttributes);
    }

    /**
     * Enforce the W3C trace-id schema literally: exactly 32 lowercase hex
     * chars (0-9, a-f). Added in v2.0.0-rc3 per cross-constraint audit α-5 /
     * P1-5 — prior code validated only {@code length() != 32} so the contract
     * text "lowercase hex" was unenforced.
     */
    static void requireLowerHex32(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " is required");
        if (value.length() != 32) {
            throw new IllegalArgumentException(fieldName + " must be exactly 32 lowercase hex chars (W3C)");
        }
        for (int i = 0; i < 32; i++) {
            char c = value.charAt(i);
            boolean isLowerHex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!isLowerHex) {
                throw new IllegalArgumentException(fieldName + " must be exactly 32 lowercase hex chars (W3C); offending char at index " + i + ": '" + c + "'");
            }
        }
    }
}
