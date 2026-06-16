package com.mcpruntime.core.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Property holder for the MCP Agent Runtime configuration.
 * <p>
 * Acts as the bridge between Spring's {@code application.yml} / {@code Environment}
 * and the {@link RuntimeConfig} resolution layer. In production, bind this
 * via {@code @ConfigurationProperties(prefix = "mcp.runtime")}. For testing,
 * construct directly with a map.
 */
public class RuntimeProperties {

    private final Map<String, Object> props;

    public RuntimeProperties() {
        this.props = new ConcurrentHashMap<>();
    }

    public RuntimeProperties(Map<String, Object> props) {
        this.props = new ConcurrentHashMap<>(props != null ? props : Map.of());
    }

    /**
     * Get a typed property value.
     * Supports nested key lookup with dot notation, e.g. "tools.weather.timeout".
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        // Try direct access first
        Object val = props.get(key);
        if (val != null && type.isInstance(val)) {
            return (T) val;
        }

        // Try nested dot-notation: top.middle.leaf
        String[] parts = key.split("\\.");
        if (parts.length > 1) {
            Object current = props;
            for (String part : parts) {
                if (current instanceof Map) {
                    current = ((Map<String, Object>) current).get(part);
                } else {
                    return null;
                }
            }
            if (current != null && type.isInstance(current)) {
                return (T) current;
            }
        }

        return null;
    }

    public void set(String key, Object value) {
        props.put(key, value);
    }

    public boolean contains(String key) {
        return props.containsKey(key);
    }
}
