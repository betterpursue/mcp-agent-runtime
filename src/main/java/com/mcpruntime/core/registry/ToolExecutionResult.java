package com.mcpruntime.core.registry;

import java.util.Map;

public class ToolExecutionResult {

    private final String toolName;
    private final boolean success;
    private final Object result;
    private final String errorMessage;
    private final long elapsedNanos;
    private final ToolExecutionContext context;

    private ToolExecutionResult(Builder builder) {
        this.toolName = builder.toolName;
        this.success = builder.success;
        this.result = builder.result;
        this.errorMessage = builder.errorMessage;
        this.elapsedNanos = builder.elapsedNanos;
        this.context = builder.context;
    }

    public static ToolExecutionResult success(String toolName, Object result,
                                               long elapsedNanos,
                                               ToolExecutionContext ctx) {
        return new Builder()
            .toolName(toolName)
            .success(true)
            .result(result)
            .elapsedNanos(elapsedNanos)
            .context(ctx)
            .build();
    }

    public static ToolExecutionResult failure(String toolName, Exception e,
                                               ToolExecutionContext ctx) {
        return new Builder()
            .toolName(toolName)
            .success(false)
            .errorMessage(e.getMessage())
            .context(ctx)
            .build();
    }

    public String getToolName() {
        return toolName;
    }

    public boolean isSuccess() {
        return success;
    }

    public Object getResult() {
        return result;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public long getElapsedNanos() {
        return elapsedNanos;
    }

    public ToolExecutionContext getContext() {
        return context;
    }

    public static class Builder {
        private String toolName;
        private boolean success;
        private Object result;
        private String errorMessage;
        private long elapsedNanos;
        private ToolExecutionContext context;

        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder result(Object result) {
            this.result = result;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder elapsedNanos(long elapsedNanos) {
            this.elapsedNanos = elapsedNanos;
            return this;
        }

        public Builder context(ToolExecutionContext context) {
            this.context = context;
            return this;
        }

        public ToolExecutionResult build() {
            return new ToolExecutionResult(this);
        }
    }
}
