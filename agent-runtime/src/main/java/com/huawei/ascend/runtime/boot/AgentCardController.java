package com.huawei.ascend.runtime.boot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentProvider;
import org.a2aproject.sdk.spec.Legacy_0_3_AgentInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the agent card independently of the A2A request handler stack.
 * Always available as long as an AgentCard bean exists.
 */
@RestController
public class AgentCardController {

    private static final Logger log = LoggerFactory.getLogger(AgentCardController.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CACHE_CONTROL_VALUE = "public, max-age=60";

    private final AgentCard agentCard;
    private final RuntimeAccessProperties access;

    public AgentCardController(AgentCard agentCard, RuntimeAccessProperties access) {
        this.agentCard = agentCard;
        this.access = access;
    }

    @GetMapping(value = "/.well-known/agent-card.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AgentCard> agentCard(HttpServletRequest request,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        AgentCard resolved = resolveUrls(agentCard, request);
        String etag = computeEtag(resolved);
        if (etag != null && etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_VALUE)
                    .header(HttpHeaders.ETAG, etag)
                    .build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_VALUE);
        if (etag != null) {
            headers.set(HttpHeaders.ETAG, etag);
        }
        return new ResponseEntity<>(resolved, headers, HttpStatus.OK);
    }

    @GetMapping(value = "/.well-known/agent.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AgentCard> agentCardLegacy(HttpServletRequest request,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        log.info("[A2A] agent card served via legacy path");
        AgentCard resolved = resolveUrls(agentCard, request);
        String etag = computeEtag(resolved);
        if (etag != null && etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_VALUE)
                    .header(HttpHeaders.ETAG, etag)
                    .header("Link", "</.well-known/agent-card.json>; rel=\"canonical\"")
                    .build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_VALUE);
        if (etag != null) {
            headers.set(HttpHeaders.ETAG, etag);
        }
        headers.set("Link", "</.well-known/agent-card.json>; rel=\"canonical\"");
        return new ResponseEntity<>(resolved, headers, HttpStatus.OK);
    }

    private AgentCard resolveUrls(AgentCard card, HttpServletRequest request) {
        final String base = resolveBase(request);
        return AgentCard.builder(card)
                .url(resolveUrl(base, card.url()))
                .provider(card.provider() == null ? null
                        : new AgentProvider(card.provider().organization(),
                                resolveUrl(base, card.provider().url())))
                .supportedInterfaces(card.supportedInterfaces() == null ? List.of() :
                        card.supportedInterfaces().stream()
                                .map(i -> new AgentInterface(
                                        i.protocolBinding(),
                                        resolveUrl(base, i.url()),
                                        i.tenant(), i.protocolVersion()))
                                .toList())
                .additionalInterfaces(card.additionalInterfaces() == null ? List.of() :
                        card.additionalInterfaces().stream()
                                .map(i -> new Legacy_0_3_AgentInterface(
                                        i.transport(),
                                        resolveUrl(base, i.url())))
                                .toList())
                .build();
    }

    /**
     * A configured {@code agent-runtime.access.a2a.public-base-url} wins (it may carry
     * a path prefix added by a fronting proxy); otherwise the base is derived from the
     * current request. The request-derived path honors {@code X-Forwarded-*} only when
     * the host sets {@code server.forward-headers-strategy=framework} so spring-web's
     * ForwardedHeaderFilter rewrites the request facade.
     */
    private String resolveBase(HttpServletRequest request) {
        String configured = access.getPublicBaseUrl();
        if (configured != null && !configured.isBlank()) {
            String trimmed = configured.trim();
            return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        }
        return request.getScheme() + "://" + request.getServerName()
                + (request.getServerPort() != 80 && request.getServerPort() != 443
                        ? ":" + request.getServerPort() : "");
    }

    private static String resolveUrl(String base, String path) {
        if (path == null || path.isBlank()) {
            return base;
        }
        // A provider-supplied absolute URL is already resolvable — prefixing the
        // local base would yield http://host/http://other-host/a2a.
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        return base + (path.startsWith("/") ? path : "/" + path);
    }

    private static String computeEtag(AgentCard card) {
        try {
            byte[] json = MAPPER.writeValueAsBytes(card);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json);
            return "\"" + HexFormat.of().formatHex(hash) + "\"";
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            return null;
        }
    }
}
