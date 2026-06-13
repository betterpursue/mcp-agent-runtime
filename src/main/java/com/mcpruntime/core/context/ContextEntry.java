package com.mcpruntime.core.context;

/**
 * A single unit of context: a user message, assistant response, tool invocation,
 * tool result, or a compressed summary.
 */
public class ContextEntry {

    public enum Role {
        USER,
        ASSISTANT,
        TOOL_CALL,
        TOOL_RESULT,
        SYSTEM,
        SUMMARY
    }

    private final Role role;
    private final String content;
    private final long timestamp;
    private final double relevanceScore;
    private final int estimatedTokens;

    private ContextEntry(Builder builder) {
        this.role = builder.role;
        this.content = builder.content;
        this.timestamp = builder.timestamp;
        this.relevanceScore = builder.relevanceScore;
        this.estimatedTokens = builder.estimatedTokens;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ContextEntry system(String content) {
        return builder().role(Role.SYSTEM).content(content).build();
    }

    public static ContextEntry userMessage(String content) {
        return builder().role(Role.USER).content(content).build();
    }

    public static ContextEntry toolResult(String toolName, String result) {
        return builder()
                .role(Role.TOOL_RESULT)
                .content("[Tool: " + toolName + "]\n" + result)
                .build();
    }

    public Role getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public double getRelevanceScore() {
        return relevanceScore;
    }

    public int getEstimatedTokens() {
        return estimatedTokens;
    }

    public static class Builder {
        private Role role;
        private String content;
        private long timestamp = System.currentTimeMillis();
        private double relevanceScore = 1.0;
        private int estimatedTokens = -1;

        public Builder role(Role role) {
            this.role = role;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder relevanceScore(double relevanceScore) {
            this.relevanceScore = relevanceScore;
            return this;
        }

        public Builder estimatedTokens(int estimatedTokens) {
            this.estimatedTokens = estimatedTokens;
            return this;
        }

        public ContextEntry build() {
            if (content == null) {
                this.content = "";
            }
            if (estimatedTokens < 0) {
                this.estimatedTokens = TokenBudgetManager.estimateTokens(this.content);
            }
            return new ContextEntry(this);
        }
    }
}
