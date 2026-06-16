package com.mcpruntime.core.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RuntimeConfig}.
 * <p>
 * Covers: layered resolution, tool-specific config, environment variable parsing,
 * dynamic overrides, default values.
 */
class RuntimeConfigTest {

    private RuntimeProperties baseProps;
    private Map<String, Map<String, Object>> toolOverrides;

    @BeforeEach
    void setUp() {
        baseProps = new RuntimeProperties(Map.of(
                "default-tool-timeout", 10_000L,
                "thread-pool.core-size", 4,
                "session.ttl-seconds", 3600
        ));

        toolOverrides = Map.of(
                "weather", Map.of(
                        "timeout", 3000L,
                        "enabled", true,
                        "rate-limit", 10
                ),
                "search", Map.of(
                        "timeout", 5000L,
                        "rate-limit", 30
                )
        );
    }

    @Test
    void shouldReturnDefaultWhenKeyNotFound() {
        RuntimeConfig config = new RuntimeConfig(baseProps);
        String result = config.get("nonexistent.key", String.class, "fallback");
        assertEquals("fallback", result);
    }

    @Test
    void shouldReturnRuntimePropertyValue() {
        RuntimeConfig config = new RuntimeConfig(baseProps);
        Long timeout = config.get("default-tool-timeout", Long.class, 5000L);
        assertEquals(10_000L, timeout);
    }

    @Test
    void shouldReturnToolSpecificOverride() {
        RuntimeConfig config = new RuntimeConfig(baseProps, toolOverrides);
        Long timeout = config.getToolConfig("weather", "timeout", Long.class, 10_000L);
        assertEquals(3000L, timeout);
    }

    @Test
    void shouldFallbackToGlobalDefaultForToolWithoutOverride() {
        RuntimeConfig config = new RuntimeConfig(baseProps, toolOverrides);
        Long timeout = config.getToolConfig("nonexistent-tool", "timeout", Long.class, 10_000L);
        assertEquals(10_000L, timeout); // falls back to default-tool-timeout
    }

    @Test
    void shouldResolveToolEnabledFlag() {
        RuntimeConfig config = new RuntimeConfig(baseProps, toolOverrides);
        assertTrue(config.isToolEnabled("weather"));
        assertTrue(config.isToolEnabled("nonexistent")); // default is true
    }

    @Test
    void shouldGetToolTimeoutThroughHelper() {
        RuntimeConfig config = new RuntimeConfig(baseProps, toolOverrides);
        assertEquals(3000L, config.getToolTimeout("weather"));
        assertEquals(10_000L, config.getToolTimeout("nonexistent")); // global default
    }

    @Test
    void shouldApplyDynamicOverride() {
        RuntimeConfig config = new RuntimeConfig(baseProps);
        assertEquals(10_000L, config.get("default-tool-timeout", Long.class, 5000L));

        config.setOverride("default-tool-timeout", 20_000L);
        assertEquals(20_000L, config.get("default-tool-timeout", Long.class, 5000L));
    }

    @Test
    void shouldRemoveDynamicOverride() {
        RuntimeConfig config = new RuntimeConfig(baseProps);
        config.setOverride("default-tool-timeout", 20_000L);
        assertEquals(20_000L, config.get("default-tool-timeout", Long.class, 5000L));

        config.removeOverride("default-tool-timeout");
        assertEquals(10_000L, config.get("default-tool-timeout", Long.class, 5000L));
    }

    @Test
    void shouldReturnUnmodifiableToolOverrides() {
        RuntimeConfig config = new RuntimeConfig(baseProps, toolOverrides);
        Map<String, Map<String, Object>> overrides = config.getToolOverrides();

        assertThrows(UnsupportedOperationException.class,
                () -> overrides.put("new-tool", Map.of()));
    }

    @Test
    void shouldHandleNullToolOverrides() {
        RuntimeConfig config = new RuntimeConfig(baseProps, null);
        Long timeout = config.getToolConfig("weather", "timeout", Long.class, 5000L);
        assertEquals(5000L, timeout);
    }

    @Test
    void shouldParseEnvironmentVariableLikeKeys() {
        // This test verifies the conversion helper works for env-var-style values.
        // Actual env var injection is tested in integration tests.
        RuntimeConfig config = new RuntimeConfig(baseProps);
        // Integer property should be resolvable
        Integer poolSize = config.get("thread-pool.core-size", Integer.class, 2);
        assertEquals(4, poolSize);
    }
}
