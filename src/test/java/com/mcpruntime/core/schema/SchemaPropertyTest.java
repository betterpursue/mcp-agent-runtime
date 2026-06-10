package com.mcpruntime.core.schema;

import org.junit.jupiter.api.Test;

import java.util.Map;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SchemaPropertyTest {

    @Test
    void shouldCreateStringProperty() {
        SchemaProperty prop = SchemaProperty.STRING;
        Map<String, Object> map = prop.toMap();

        assertEquals("string", map.get("type"));
        assertFalse(map.containsKey("description"));
        assertFalse(map.containsKey("enum"));
        assertFalse(map.containsKey("default"));
    }

    @Test
    void shouldAddDescription() {
        SchemaProperty prop = SchemaProperty.STRING
            .withDescription("用户名称");

        Map<String, Object> map = prop.toMap();
        assertEquals("string", map.get("type"));
        assertEquals("用户名称", map.get("description"));
    }

    @Test
    void shouldAddEnumValues() {
        SchemaProperty prop = SchemaProperty.STRING
            .withEnum("a", "b", "c");

        Map<String, Object> map = prop.toMap();
        assertEquals(List.of("a", "b", "c"), map.get("enum"));
    }

    @Test
    void shouldAddDefaultValue() {
        SchemaProperty prop = SchemaProperty.STRING
            .withDefault("default_val");

        Map<String, Object> map = prop.toMap();
        assertEquals("default_val", map.get("default"));
    }

    @Test
    void shouldChainAllModifiers() {
        SchemaProperty prop = SchemaProperty.STRING
            .withDescription("颜色")
            .withEnum("红", "绿", "蓝")
            .withDefault("红");

        Map<String, Object> map = prop.toMap();
        assertEquals("string", map.get("type"));
        assertEquals("颜色", map.get("description"));
        assertEquals(List.of("红", "绿", "蓝"), map.get("enum"));
        assertEquals("红", map.get("default"));
    }

    @Test
    void shouldCreateIntegerAndNumberTypes() {
        assertEquals("integer", SchemaProperty.INTEGER.toMap().get("type"));
        assertEquals("number", SchemaProperty.NUMBER.toMap().get("type"));
        assertEquals("boolean", SchemaProperty.BOOLEAN.toMap().get("type"));
        assertEquals("array", SchemaProperty.ARRAY.toMap().get("type"));
        assertEquals("object", SchemaProperty.OBJECT.toMap().get("type"));
    }
}
