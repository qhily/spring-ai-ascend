package com.huawei.ascend.runtime.engine.agentscope;

import java.util.Map;

/**
 * AgentScope SDK/runtime event flattened to the current agent-runtime result model.
 */
public record AgentScopeEvent(
        Type type,
        String text,
        String errorCode,
        String errorMessage,
        Map<String, Object> metadata) {

    /**
     * Sentinel error code used when a FAILED event carries no upstream error code.
     * Explicitly unclassified — distinguishable from any real AgentScope error code.
     * Maps to {@link com.huawei.ascend.runtime.engine.spi.ErrorCategory#UNKNOWN} via
     * {@link AgentScopeErrorCategories}.
     */
    public static final String UNCLASSIFIED_ERROR_CODE = "AGENTSCOPE_UNCLASSIFIED";

    public enum Type {
        OUTPUT,
        COMPLETED,
        FAILED,
        INTERRUPTED
    }

    public AgentScopeEvent {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        text = text == null ? "" : text;
        errorCode = (type == Type.FAILED && (errorCode == null || errorCode.isBlank()))
                ? UNCLASSIFIED_ERROR_CODE : errorCode;
        errorMessage = errorMessage == null ? "" : errorMessage;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static AgentScopeEvent output(String text) {
        return new AgentScopeEvent(Type.OUTPUT, text, null, null, Map.of());
    }

    public static AgentScopeEvent completed(String text) {
        return new AgentScopeEvent(Type.COMPLETED, text, null, null, Map.of());
    }

    public static AgentScopeEvent failed(String code, String message) {
        return new AgentScopeEvent(Type.FAILED, "", code, message, Map.of());
    }

    public static AgentScopeEvent interrupted(String prompt) {
        return new AgentScopeEvent(Type.INTERRUPTED, prompt, null, null, Map.of());
    }
}
