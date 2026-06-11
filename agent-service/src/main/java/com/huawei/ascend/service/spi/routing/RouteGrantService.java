package com.huawei.ascend.service.spi.routing;

/**
 * Signed routing capabilities: a {@link RouteGrant} proves the service facade
 * authorized one source agent to call one target agent over one A2A method
 * within a bounded validity window.
 *
 * <p>Tenant red-line: the grant embeds the tenant it was minted for and
 * {@link #validate} re-checks it against the inbound context — a grant minted
 * for one tenant must never validate for another.
 *
 * <p>Grants authorize the hop only; the A2A payload itself is forwarded as
 * opaque bytes and is never parsed, validated, or rewritten on the way through
 * (the A2A-NO-REWRITE invariant).
 */
public interface RouteGrantService {

    RouteGrant resolveGrant(RouteGrantRequest request);

    GrantValidationResult validate(RouteGrant grant, InboundA2aContext context);
}
