package com.mcpruntime.core.schema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SchemaProperty {

    private final String type;
    private final String description;
    private final List<Object> enumValues;
    private final Object defaultValue;

    private SchemaProperty(String type, String description,
                           List<Object> enumValues, Object defaultValue) {
        this.type = type;
        this.description = description;
        this.enumValues = enumValues;
        this.defaultValue = defaultValue;
    }

    public static SchemaProperty ofType(String type) {
        return new SchemaProperty(type, null, null, null);
    }

    public static final SchemaProperty STRING = ofType("string");
    public static final SchemaProperty INTEGER = ofType("integer");
    public static final SchemaProperty NUMBER = ofType("number");
    public static final SchemaProperty BOOLEAN = ofType("boolean");
    public static final SchemaProperty ARRAY = ofType("array");
    public static final SchemaProperty OBJECT = ofType("object");

    public SchemaProperty withDescription(String description) {
        return new SchemaProperty(this.type, description, this.enumValues, this.defaultValue);
    }

    public SchemaProperty withEnum(Object... values) {
        return new SchemaProperty(this.type, this.description, List.of(values), this.defaultValue);
    }

    public SchemaProperty withDefault(Object defaultValue) {
        return new SchemaProperty(this.type, this.description, this.enumValues, defaultValue);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", type);
        if (description != null) map.put("description", description);
        if (enumValues != null && !enumValues.isEmpty()) map.put("enum", enumValues);
        if (defaultValue != null) map.put("default", defaultValue);
        return map;
    }
}
