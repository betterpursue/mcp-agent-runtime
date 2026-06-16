package com.mcpruntime.core.security;

import java.util.Map;

/**
 * Validates tool arguments before execution.
 * <p>
 * Unlike {@link SecurityPolicy} which guards invocation permissions,
 * a ValidationPolicy focuses on argument correctness: type checking,
 * range constraints, injection pattern detection.
 */
@FunctionalInterface
public interface ValidationPolicy {

    /**
     * Validate the arguments for a tool invocation.
     *
     * @param toolName the name of the tool being called
     * @param args     the arguments to validate
     * @throws SecurityException if validation fails
     */
    void validate(String toolName, Map<String, Object> args);
}
