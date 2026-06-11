/**
 * OpenTelemetry binding of the gateway's GENERATION-span seam. Lives in its own
 * package so that {@code io.opentelemetry} stays an optional dependency: nothing
 * outside this package references OTel types, and the auto-configuration only
 * loads it behind a class-presence guard.
 */
package com.huawei.ascend.runtime.llm.gateway.otel;
