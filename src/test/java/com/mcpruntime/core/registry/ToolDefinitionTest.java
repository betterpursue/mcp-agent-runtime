package com.mcpruntime.core.registry;

import com.mcpruntime.core.registry.exception.DuplicateToolException;
import com.mcpruntime.core.schema.JsonSchema;
import com.mcpruntime.core.schema.SchemaProperty;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ToolDefinitionTest {

    @Test
    void shouldBuildWithRequiredFields() {
        ToolDefinition def = ToolDefinition.builder()
            .name("test")
            .executor(ctx -> "ok")
            .build();

        assertEquals("test", def.getName());
        assertNotNull(def.getExecutor());
        assertNotNull(def.getInputSchema());
        assertNotNull(def.getInterceptors());
        assertNotNull(def.getMetadata());
    }

    @Test
    void shouldThrowWhenNameIsNull() {
        assertThrows(NullPointerException.class,
            () -> ToolDefinition.builder()
                .executor(ctx -> "ok")
                .build());
    }

    @Test
    void shouldThrowWhenExecutorIsNull() {
        assertThrows(NullPointerException.class,
            () -> ToolDefinition.builder()
                .name("test")
                .build());
    }

    @Test
    void shouldSetOptionalFields() {
        JsonSchema schema = JsonSchema.builder()
            .addProperty("name", SchemaProperty.STRING)
            .build();

        ToolInterceptor interceptor = new ToolInterceptor() {};

        ToolDefinition def = ToolDefinition.builder()
            .name("full")
            .description("完整定义")
            .parameterSchema(schema)
            .executor(ctx -> "ok")
            .withInterceptor(interceptor)
            .metadata(ToolMetadata.builder()
                .timeout(Duration.ofSeconds(5))
                .owner("test-team")
                .version("2.0.0")
                .build())
            .build();

        assertEquals("full", def.getName());
        assertEquals("完整定义", def.getDescription());
        assertEquals(schema, def.getInputSchema());
        assertEquals(1, def.getInterceptors().size());
        assertEquals(Duration.ofSeconds(5), def.getMetadata().getTimeout());
        assertEquals("test-team", def.getMetadata().getOwner());
        assertEquals("2.0.0", def.getMetadata().getVersion());
    }

    @Test
    void interceptorsShouldBeImmutable() {
        ToolDefinition def = ToolDefinition.builder()
            .name("test")
            .executor(ctx -> "ok")
            .withInterceptor(new ToolInterceptor() {})
            .build();

        assertThrows(UnsupportedOperationException.class,
            () -> def.getInterceptors().add(new ToolInterceptor() {}));
    }
}
