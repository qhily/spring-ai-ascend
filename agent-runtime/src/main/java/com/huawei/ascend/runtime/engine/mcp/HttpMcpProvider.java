package com.huawei.ascend.runtime.engine.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.McpProvider;
import com.huawei.ascend.runtime.engine.spi.McpToolResult;
import com.huawei.ascend.runtime.engine.spi.McpToolSpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Minimal JSON-RPC over HTTP MCP provider. */
public final class HttpMcpProvider implements McpProvider {
    private static final Logger LOG = LoggerFactory.getLogger(HttpMcpProvider.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<String, ServerEndpoint> serversById;
    private final Set<String> initializedServers = ConcurrentHashMap.newKeySet();
    private final AtomicLong nextRequestId = new AtomicLong(1L);

    public HttpMcpProvider(McpProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, defaultClient(properties));
    }

    HttpMcpProvider(McpProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.serversById = Map.copyOf(toEndpoints(Objects.requireNonNull(properties, "properties")));
    }

    @Override
    public List<McpToolSpec> listTools(AgentExecutionContext context) {
        List<McpToolSpec> tools = new ArrayList<>();
        for (ServerEndpoint server : serversById.values()) {
            long started = System.nanoTime();
            ensureInitialized(server);
            Map<String, Object> result = request(server, "tools/list", Map.of());
            List<Map<String, Object>> serverTools = toolMaps(result.get("tools"));
            for (Map<String, Object> tool : serverTools) {
                tools.add(toToolSpec(server, tool));
            }
            LOG.info("mcp tools discovered serverId={} count={} latencyMs={}",
                    server.serverId(), serverTools.size(), elapsedMs(started));
        }
        return tools;
    }

    @Override
    public McpToolResult callTool(AgentExecutionContext context, String serverId, String name,
            Map<String, Object> arguments) {
        ServerEndpoint server = serversById.get(serverId);
        if (server == null) {
            return McpToolResult.error("MCP_SERVER_NOT_CONFIGURED",
                    "MCP server is not configured: " + serverId,
                    Map.of("serverId", safe(serverId), "toolName", safe(name)));
        }
        long started = System.nanoTime();
        try {
            ensureInitialized(server);
            Map<String, Object> result = request(server, "tools/call", Map.of(
                    "name", safe(name),
                    "arguments", arguments == null ? Map.of() : arguments));
            McpToolResult toolResult = toToolResult(server, name, result, elapsedMs(started));
            LOG.info("mcp tool call finished serverId={} toolName={} latencyMs={} isError={}",
                    server.serverId(), name, elapsedMs(started), toolResult.isError());
            return toolResult;
        } catch (McpRequestException error) {
            LOG.warn("mcp tool call failed serverId={} toolName={} errorCode={} message={}",
                    server.serverId(), name, error.errorCode(), error.getMessage());
            return McpToolResult.error(error.errorCode(), error.getMessage(),
                    Map.of("serverId", server.serverId(), "toolName", safe(name), "latencyMs", elapsedMs(started)));
        }
    }

    private void ensureInitialized(ServerEndpoint server) {
        if (initializedServers.add(server.serverId())) {
            try {
                request(server, "initialize", Map.of(
                        "protocolVersion", "2025-06-18",
                        "capabilities", Map.of(),
                        "clientInfo", Map.of("name", "spring-ai-ascend-agent-runtime", "version", "0.1")));
                LOG.info("mcp server initialized serverId={} transport={}", server.serverId(), server.transport());
            } catch (RuntimeException error) {
                initializedServers.remove(server.serverId());
                throw error;
            }
        }
    }

    private Map<String, Object> request(ServerEndpoint server, String method, Map<String, Object> params) {
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(Map.of(
                    "jsonrpc", "2.0",
                    "id", nextRequestId.getAndIncrement(),
                    "method", method,
                    "params", params));
        } catch (IOException error) {
            throw new McpRequestException("MCP_BAD_RESPONSE", error.getMessage(), error);
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(server.url()))
                .timeout(server.requestTimeout())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody));
        server.headers().forEach(builder::header);

        HttpResponse<String> response;
        try {
            response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new McpRequestException("MCP_TOOL_TIMEOUT", error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new McpRequestException("MCP_BAD_RESPONSE", error.getMessage(), error);
        } catch (IOException error) {
            throw new McpRequestException("MCP_SERVER_UNAVAILABLE", error.getMessage(), error);
        }

        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new McpRequestException("MCP_AUTH_FAILED",
                    "MCP server rejected authentication for serverId=" + server.serverId());
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new McpRequestException("MCP_SERVER_UNAVAILABLE",
                    "MCP server returned HTTP " + response.statusCode() + " for serverId=" + server.serverId());
        }
        try {
            return parseJsonRpcResult(server, response.body());
        } catch (IOException error) {
            throw new McpRequestException("MCP_BAD_RESPONSE", error.getMessage(), error);
        }
    }

    private Map<String, Object> parseJsonRpcResult(ServerEndpoint server, String responseBody) throws IOException {
        String json = extractJson(responseBody);
        Map<String, Object> response = objectMapper.readValue(json, MAP_TYPE);
        Object error = response.get("error");
        if (error instanceof Map<?, ?> errorMap) {
            Object message = errorMap.get("message");
            String errorMessage = message == null ? "MCP server returned JSON-RPC error" : String.valueOf(message);
            String errorCode = errorMessage.toLowerCase().contains("not found")
                    ? "MCP_TOOL_NOT_FOUND"
                    : "MCP_BAD_RESPONSE";
            throw new McpRequestException(errorCode, errorMessage);
        }
        Object result = response.get("result");
        if (!(result instanceof Map<?, ?> resultMap)) {
            throw new McpRequestException("MCP_BAD_RESPONSE",
                    "MCP server response has no object result for serverId=" + server.serverId());
        }
        return stringObjectMap(resultMap);
    }

    private static String extractJson(String responseBody) {
        String body = responseBody == null ? "" : responseBody.trim();
        if (!body.startsWith("event:") && !body.startsWith("data:")) {
            return body;
        }
        StringBuilder data = new StringBuilder();
        for (String line : body.split("\\R")) {
            if (line.startsWith("data:")) {
                if (!data.isEmpty()) {
                    data.append('\n');
                }
                data.append(line.substring("data:".length()).trim());
            }
        }
        return data.toString();
    }

    private static McpToolSpec toToolSpec(ServerEndpoint server, Map<String, Object> tool) {
        String name = stringValue(tool.get("name"));
        return new McpToolSpec(
                server.serverId(),
                name,
                stringValue(tool.get("title")),
                stringValue(tool.get("description")),
                mapValue(tool.get("inputSchema")),
                mapValue(tool.get("outputSchema")),
                mapValue(tool.get("annotations")),
                mapValue(tool.get("_meta")),
                Map.of("transport", server.transport(), "serverUrl", server.url()));
    }

    private static McpToolResult toToolResult(ServerEndpoint server, String toolName, Map<String, Object> result,
            long latencyMs) {
        return new McpToolResult(
                contentValue(result.get("content")),
                result.get("structuredContent"),
                Boolean.TRUE.equals(result.get("isError")),
                "",
                "",
                mapValue(result.get("_meta")),
                Map.of("serverId", server.serverId(), "toolName", safe(toolName), "latencyMs", latencyMs));
    }

    private static List<Map<String, Object>> contentValue(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(HttpMcpProvider::stringObjectMap)
                .toList();
    }

    private static List<Map<String, Object>> toolMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(HttpMcpProvider::stringObjectMap)
                .toList();
    }

    private static Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return stringObjectMap(map);
        }
        return Map.of();
    }

    private static Map<String, Object> stringObjectMap(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(String.valueOf(key), value));
        return copy;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }

    private static HttpClient defaultClient(McpProperties properties) {
        Duration connectTimeout = properties.getConnectTimeout() == null
                ? Duration.ofSeconds(2)
                : properties.getConnectTimeout();
        return HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
    }

    private static Map<String, ServerEndpoint> toEndpoints(McpProperties properties) {
        Map<String, ServerEndpoint> endpoints = new LinkedHashMap<>();
        List<McpProperties.Server> servers = properties.getServers();
        for (int i = 0; i < servers.size(); i++) {
            McpProperties.Server server = servers.get(i);
            if (server.getUrl() == null || server.getUrl().isBlank()) {
                continue;
            }
            String serverId = server.getServerId() == null || server.getServerId().isBlank()
                    ? "mcp-" + i
                    : server.getServerId().trim();
            if (endpoints.containsKey(serverId)) {
                throw new IllegalArgumentException("Duplicate MCP serverId: " + serverId);
            }
            Duration timeout = server.getRequestTimeout() == null
                    ? properties.getRequestTimeout()
                    : server.getRequestTimeout();
            endpoints.put(serverId, new ServerEndpoint(
                    serverId,
                    server.getUrl(),
                    server.getTransport() == null || server.getTransport().isBlank()
                            ? "streamable-http"
                            : server.getTransport(),
                    timeout == null ? Duration.ofSeconds(10) : timeout,
                    Map.copyOf(server.getHeaders())));
        }
        return endpoints;
    }

    private record ServerEndpoint(
            String serverId,
            String url,
            String transport,
            Duration requestTimeout,
            Map<String, String> headers) {
    }

    private static final class McpRequestException extends RuntimeException {
        private final String errorCode;

        McpRequestException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        McpRequestException(String errorCode, String message, Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
        }

        String errorCode() {
            return errorCode;
        }
    }
}
