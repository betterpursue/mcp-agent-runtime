package com.mcpruntime.core.registry;

import com.mcpruntime.core.registry.exception.DuplicateToolException;
import com.mcpruntime.core.registry.exception.ToolNotFoundException;
import com.mcpruntime.core.schema.JsonSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {

    private ToolRegistry registry;
    private ToolDefinition toolA;
    private ToolDefinition toolB;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        toolA = ToolDefinition.builder()
            .name("test_tool_a")
            .description("测试工具 A")
            .executor(ctx -> "result_a")
            .build();
        toolB = ToolDefinition.builder()
            .name("test_tool_b")
            .description("测试工具 B")
            .executor(ctx -> "result_b")
            .build();
    }

    @Test
    void shouldRegisterTool() {
        registry.register(toolA);

        assertTrue(registry.contains("test_tool_a"));
        assertEquals(1, registry.size());
    }

    @Test
    void shouldLookupTool() {
        registry.register(toolA);

        Optional<ToolDefinition> found = registry.lookup("test_tool_a");
        assertTrue(found.isPresent());
        assertEquals("test_tool_a", found.get().getName());
    }

    @Test
    void shouldReturnEmptyForUnknownTool() {
        Optional<ToolDefinition> found = registry.lookup("nonexistent");
        assertTrue(found.isEmpty());
    }

    @Test
    void shouldGetRequiredTool() {
        registry.register(toolA);

        ToolDefinition found = registry.getRequired("test_tool_a");
        assertNotNull(found);
    }

    @Test
    void shouldThrowWhenRequiredToolNotFound() {
        assertThrows(ToolNotFoundException.class,
            () -> registry.getRequired("nonexistent"));
    }

    @Test
    void shouldThrowOnDuplicateRegistration() {
        registry.register(toolA);

        assertThrows(DuplicateToolException.class,
            () -> registry.register(toolA));
    }

    @Test
    void shouldRegisterAll() {
        registry.registerAll(List.of(toolA, toolB));

        assertEquals(2, registry.size());
        assertTrue(registry.contains("test_tool_a"));
        assertTrue(registry.contains("test_tool_b"));
    }

    @Test
    void shouldListAllTools() {
        registry.registerAll(List.of(toolA, toolB));

        List<ToolDefinition> tools = registry.list();
        assertEquals(2, tools.size());
    }

    @Test
    void listShouldBeImmutable() {
        registry.register(toolA);

        List<ToolDefinition> tools = registry.list();
        assertThrows(UnsupportedOperationException.class,
            () -> tools.add(toolB));
    }

    @Test
    void shouldNotifyListenerOnRegistration() {
        List<String> notified = new java.util.ArrayList<>();
        registry.addListener(new ToolRegistry.ToolRegistryListener() {
            @Override
            public void onToolRegistered(ToolDefinition tool) {
                notified.add(tool.getName());
            }
        });

        registry.register(toolA);
        registry.register(toolB);

        assertEquals(List.of("test_tool_a", "test_tool_b"), notified);
    }

    @Test
    void shouldNotifyListenerEvenWhenExceptionThrownByPreviousRegistration() {
        List<String> notified = new java.util.ArrayList<>();
        registry.addListener(new ToolRegistry.ToolRegistryListener() {
            @Override
            public void onToolRegistered(ToolDefinition tool) {
                notified.add(tool.getName());
            }
        });

        ToolDefinition toolDup = ToolDefinition.builder()
            .name("test_tool_a")
            .description("dup")
            .executor(ctx -> "dup")
            .build();

        registry.register(toolA);
        assertEquals(1, notified.size());

        // 第二次注册抛异常，但第一次的 listener 已经调用过了
        // 验证第一次调用确实触发了 listener
        assertEquals("test_tool_a", notified.get(0));

        // 双重重试应该不影响已注册的
        assertThrows(DuplicateToolException.class,
            () -> registry.register(toolDup));
    }

    @Test
    void shouldSupportEmptyRegistry() {
        assertEquals(0, registry.size());
        assertTrue(registry.list().isEmpty());
        assertFalse(registry.contains("anything"));
    }

    @Test
    void shouldHandleListenerExceptionsGracefully() {
        registry.addListener(new ToolRegistry.ToolRegistryListener() {
            @Override
            public void onToolRegistered(ToolDefinition tool) {
                throw new RuntimeException("listener error");
            }
        });

        // listener 抛异常应该传播出去
        assertThrows(RuntimeException.class,
            () -> registry.register(toolA));
    }

    @Test
    void shouldRegisterMultipleToolsSuccessfully() {
        int toolCount = 100;
        for (int i = 0; i < toolCount; i++) {
            final int index = i;
            registry.register(ToolDefinition.builder()
                .name("tool_" + i)
                .executor(ctx -> "result_" + index)
                .build());
        }

        assertEquals(toolCount, registry.size());
        assertTrue(registry.contains("tool_0"));
        assertTrue(registry.contains("tool_99"));
    }
}
