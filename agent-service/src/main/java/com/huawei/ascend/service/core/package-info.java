/**
 * Reference implementations of the serviceization SPI: an in-memory
 * lease-tracking registry/directory, an HMAC-SHA256 route-grant service with an
 * expiring grant cache, and a pure {@code java.net.http} A2A forwarder.
 *
 * <p>These classes are module-internal mechanism, not SPI: consumers program
 * against {@code com.huawei.ascend.service.spi} and may swap any of them for a
 * persistent or policy-bearing implementation without an SPI change. Everything
 * here is plain JDK — no Spring, no container assumptions.
 */
package com.huawei.ascend.service.core;
