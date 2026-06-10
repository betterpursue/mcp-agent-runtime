package com.mcpruntime.core.registry;

import java.util.HashMap;
import java.util.Map;

public class ToolExecutionContext {

    private final String sessionId;
    private final String traceId;
    private final String callId;
    private final Map<String, Object> args;
    private final Map<String, Object> context;

    private ToolExecutionContext(Builder builder) {
        this.sessionId = builder.sessionId;
        this.traceId = builder.traceId;
        this.callId = builder.callId;
        this.args = builder.args != null ? Map.copyOf(builder.args) : Map.of();
        this.context = builder.context != null ? Map.copyOf(builder.context) : Map.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getCallId() {
        return callId;
    }

    public Map<String, Object> getArgs() {
        return args;
    }

    @SuppressWarnings("unchecked")
    public <T> T getArg(String name) {
        return (T) args.get(name);
    }

    @SuppressWarnings("unchecked")
    public <T> T getArgOrDefault(String name, T defaultValue) {
        return (T) args.getOrDefault(name, defaultValue);
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public static class Builder {
        private String sessionId;
        private String traceId;
        private String callId;
        private Map<String, Object> args;
        private Map<String, Object> context;

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder callId(String callId) {
            this.callId = callId;
            return this;
        }

        public Builder args(Map<String, Object> args) {
            this.args = args;
            return this;
        }

        public Builder context(Map<String, Object> context) {
            this.context = context;
            return this;
        }

        public ToolExecutionContext build() {
            return new ToolExecutionContext(this);
        }
    }
}
