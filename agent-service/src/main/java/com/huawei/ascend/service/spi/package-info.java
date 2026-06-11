/**
 * agent-service serviceization SPI: the registration/discovery/routing contract
 * surface through which the service facade fronts N agent-runtime instances.
 *
 * <p>Sub-packages carry the three seams: {@code registry} (runtime registration
 * with lease/TTL state), {@code discovery} (tenant-scoped agent card lookup and
 * route resolution), and {@code routing} (HMAC-signed route grants). This root
 * package holds the error vocabulary shared by all three.
 *
 * <p>Tenant red-line: every SPI query is tenant-scoped by signature — there are
 * no tenant-free overloads. The SPI is Spring-free by design; reference
 * implementations live in {@code com.huawei.ascend.service.core} and any HTTP or
 * DI edge composes around them outside this module.
 */
package com.huawei.ascend.service.spi;
