package com.mcpruntime.core.schema;

import java.util.*;

public class JsonSchema {

    private final String type;
    private final Map<String, SchemaProperty> properties;
    private final List<String> required;

    private JsonSchema(Builder builder) {
        this.type = "object";
        this.properties = Map.copyOf(builder.properties);
        this.required = List.copyOf(builder.required);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static JsonSchema empty() {
        return new Builder().build();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", type);
        Map<String, Object> props = new LinkedHashMap<>();
        properties.forEach((name, prop) -> props.put(name, prop.toMap()));
        schema.put("properties", props);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    public static class Builder {
        private final Map<String, SchemaProperty> properties = new LinkedHashMap<>();
        private final List<String> required = new ArrayList<>();

        public Builder addProperty(String name, SchemaProperty property) {
            this.properties.put(name, property);
            return this;
        }

        public Builder addRequired(String name) {
            if (!this.required.contains(name)) {
                this.required.add(name);
            }
            return this;
        }

        public JsonSchema build() {
            return new JsonSchema(this);
        }
    }
}
