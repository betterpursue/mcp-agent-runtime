package com.mcpruntime.core.schema;

import org.junit.jupiter.api.Test;

import java.util.Map;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonSchemaTest {

    @Test
    void shouldBuildEmptySchema() {
        JsonSchema schema = JsonSchema.empty();

        Map<String, Object> map = schema.toMap();
        assertEquals("object", map.get("type"));
        assertTrue(map.containsKey("properties"));
        assertFalse(map.containsKey("required"));
    }

    @Test
    void shouldBuildWithProperties() {
        JsonSchema schema = JsonSchema.builder()
            .addProperty("city", SchemaProperty.STRING
                .withDescription("城市名")
                .withEnum("北京", "上海"))
            .addProperty("count", SchemaProperty.INTEGER)
            .addRequired("city")
            .build();

        Map<String, Object> map = schema.toMap();
        assertEquals("object", map.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) map.get("properties");
        assertEquals(2, props.size());
        assertTrue(props.containsKey("city"));
        assertTrue(props.containsKey("count"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) map.get("required");
        assertEquals(List.of("city"), required);
    }

    @Test
    void shouldHandleRequiredFields() {
        JsonSchema schema = JsonSchema.builder()
            .addProperty("a", SchemaProperty.STRING)
            .addProperty("b", SchemaProperty.STRING)
            .addRequired("a")
            .addRequired("b")
            .build();

        Map<String, Object> map = schema.toMap();
        assertEquals(List.of("a", "b"), map.get("required"));
    }

    @Test
    void shouldNotDuplicateRequired() {
        JsonSchema schema = JsonSchema.builder()
            .addProperty("a", SchemaProperty.STRING)
            .addRequired("a")
            .addRequired("a") // 重复
            .build();

        Map<String, Object> map = schema.toMap();
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) map.get("required");
        assertEquals(List.of("a"), required);
    }
}
