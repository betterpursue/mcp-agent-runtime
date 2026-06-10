package com.mcpruntime.core.registry;

import java.time.Duration;

public class ToolMetadata {

    public static final ToolMetadata DEFAULT = ToolMetadata.builder().build();

    private final Duration timeout;
    private final String owner;
    private final String version;

    private ToolMetadata(Builder builder) {
        this.timeout = builder.timeout;
        this.owner = builder.owner;
        this.version = builder.version;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Duration getTimeout() {
        return timeout;
    }

    public String getOwner() {
        return owner;
    }

    public String getVersion() {
        return version;
    }

    public static class Builder {
        private Duration timeout = Duration.ofSeconds(30);
        private String owner = "unknown";
        private String version = "0.0.1";

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder owner(String owner) {
            this.owner = owner;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public ToolMetadata build() {
            return new ToolMetadata(this);
        }
    }
}
