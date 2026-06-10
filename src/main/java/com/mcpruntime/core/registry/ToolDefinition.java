package com.mcpruntime.core.registry;

import com.mcpruntime.core.schema.JsonSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ToolDefinition {

    private final String name;
    private final String description;
    private final JsonSchema inputSchema;
    private final ToolExecutor executor;
    private final List<ToolInterceptor> interceptors;
    private final ToolMetadata metadata;

    private ToolDefinition(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.inputSchema = builder.inputSchema;
        this.executor = builder.executor;
        this.interceptors = List.copyOf(builder.interceptors);
        this.metadata = builder.metadata;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public JsonSchema getInputSchema() {
        return inputSchema;
    }

    public ToolExecutor getExecutor() {
        return executor;
    }

    public List<ToolInterceptor> getInterceptors() {
        return interceptors;
    }

    public ToolMetadata getMetadata() {
        return metadata;
    }

    public static class Builder {
        private String name;
        private String description;
        private JsonSchema inputSchema;
        private ToolExecutor executor;
        private final List<ToolInterceptor> interceptors = new ArrayList<>();
        private ToolMetadata metadata = ToolMetadata.DEFAULT;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder parameterSchema(JsonSchema schema) {
            this.inputSchema = schema;
            return this;
        }

        public Builder executor(ToolExecutor executor) {
            this.executor = executor;
            return this;
        }

        public Builder withInterceptor(ToolInterceptor interceptor) {
            this.interceptors.add(interceptor);
            return this;
        }

        public Builder metadata(ToolMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public ToolDefinition build() {
            Objects.requireNonNull(name, "tool name must not be null");
            Objects.requireNonNull(executor, "tool executor must not be null");
            if (inputSchema == null) {
                this.inputSchema = JsonSchema.empty();
            }
            return new ToolDefinition(this);
        }
    }
}
