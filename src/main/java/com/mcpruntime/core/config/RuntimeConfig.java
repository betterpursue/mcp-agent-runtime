package com.mcpruntime.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized, layered configuration for the MCP Agent Runtime.
 * <p>
 * Configuration resolution order (lowest to highest priority):
 * <ol>
 *   <li>Default values hardcoded in {@link ConfigKeys}</li>
 *   <li>{@code application.yml} properties (loaded via {@code RuntimeProperties})</li>
 *   <li>Tool-specific overrides (per-tool settings)</li>
 *   <li>Environment variables ({@code MCP_RUNTIME_*} prefix)</li>
 *   <li>Runtime dynamic overrides (set programmatically via {@link #setOverride(String, Object)})</li>
 * </ol>
 * <p>
 * This class is thread-safe after construction. Dynamic overrides are
 * synchronized but intended for rare use (e.g. feature flags, emergency config changes).
 */
public class RuntimeConfig {

    private static final Logger log = LoggerFactory.getLogger(RuntimeConfig.class);

    private final RuntimeProperties runtimeProps;
    private final Map<String, Map<String, Object>> toolOverrides;
    private final Map<String, Object> dynamicOverrides;

    public RuntimeConfig(RuntimeProperties runtimeProps) {
        this(runtimeProps, Map.of());
    }

    public RuntimeConfig(RuntimeProperties runtimeProps,
                         Map<String, Map<String, Object>> toolOverrides) {
        this.runtimeProps = runtimeProps;
        this.toolOverrides = toolOverrides != null ? toolOverrides : Map.of();
        this.dynamicOverrides = new ConcurrentHashMap<>();
    }

    // ========================================================================
    // Lookup API
    // ========================================================================

    /**
     * Resolve a configuration value using the full layered resolution.
     * Priority: dynamic override > env var > tool override > runtime property > default.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type, T defaultValue) {
        // 1. Dynamic overrides (highest priority)
        Object dynamic = dynamicOverrides.get(key);
        if (dynamic != null && type.isInstance(dynamic)) {
            return (T) dynamic;
        }

        // 2. Environment variable: MCP_RUNTIME_<KEY>
        String envKey = "MCP_RUNTIME_" + key.toUpperCase()
                .replace('.', '_')
                .replace('-', '_');
        String envVal = System.getenv(envKey);
        if (envVal != null) {
            return convertEnvValue(envVal, type, key);
        }

        // 3. Runtime property
        T propVal = runtimeProps != null ? runtimeProps.get(key, type) : null;
        if (propVal != null) {
            return propVal;
        }

        return defaultValue;
    }

    /**
     * Resolve a tool-specific configuration value.
     * Priority: tool override > runtime property (with "tools.<toolName>." prefix) > global default.
     */
    @SuppressWarnings("unchecked")
    public <T> T getToolConfig(String toolName, String configKey, Class<T> type, T defaultValue) {
        // 1. Tool-specific override
        Map<String, Object> toolCfg = toolOverrides.get(toolName);
        if (toolCfg != null) {
            Object val = toolCfg.get(configKey);
            if (val != null && type.isInstance(val)) {
                return (T) val;
            }
        }

        // 2. Runtime property scoped to tool
        String scopedKey = "tools." + toolName + "." + configKey;
        T propVal = runtimeProps != null ? runtimeProps.get(scopedKey, type) : null;
        if (propVal != null) {
            return propVal;
        }

        // 3. Global default fallback
        return get(configKey, type, defaultValue);
    }

    /**
     * Set a dynamic override at runtime. These override all static sources
     * and are intended for feature flags or emergency configuration changes.
     */
    public void setOverride(String key, Object value) {
        dynamicOverrides.put(key, value);
        log.info("Dynamic config override set: {} = {}", key, value);
    }

    /**
     * Remove a dynamic override (reverts to static resolution).
     */
    public void removeOverride(String key) {
        dynamicOverrides.remove(key);
        log.info("Dynamic config override removed: {}", key);
    }

    /**
     * Check whether a tool is enabled in configuration.
     */
    public boolean isToolEnabled(String toolName) {
        return getToolConfig(toolName, "enabled", Boolean.class, true);
    }

    /**
     * Get the timeout in milliseconds for a specific tool.
     */
    public long getToolTimeout(String toolName) {
        Long timeout = getToolConfig(toolName, "timeout", Long.class, null);
        if (timeout != null) return timeout;
        return get("default-tool-timeout", Long.class, 10_000L);
    }

    /**
     * Return all tool overrides (unmodifiable view).
     */
    public Map<String, Map<String, Object>> getToolOverrides() {
        return Collections.unmodifiableMap(toolOverrides);
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    @SuppressWarnings("unchecked")
    private <T> T convertEnvValue(String raw, Class<T> type, String key) {
        try {
            if (type == String.class) {
                return (T) raw;
            } else if (type == Integer.class || type == int.class) {
                return (T) Integer.valueOf(raw);
            } else if (type == Long.class || type == long.class) {
                return (T) Long.valueOf(raw);
            } else if (type == Boolean.class || type == boolean.class) {
                return (T) Boolean.valueOf(raw);
            } else if (type == Double.class || type == double.class) {
                return (T) Double.valueOf(raw);
            }
        } catch (Exception e) {
            log.warn("Failed to convert env var {}={} to type {}", key, raw, type.getSimpleName());
        }
        return null;
    }

    // ========================================================================
    // Config keys
    // ========================================================================

    public static final class ConfigKeys {
        public static final String DEFAULT_TOOL_TIMEOUT = "default-tool-timeout";
        public static final String THREAD_POOL_CORE_SIZE = "thread-pool.core-size";
        public static final String THREAD_POOL_MAX_SIZE = "thread-pool.max-size";
        public static final String SESSION_TTL = "session.ttl-seconds";
        public static final String CACHE_LOCAL_TTL = "cache.local-ttl-seconds";

        private ConfigKeys() {}
    }
}
