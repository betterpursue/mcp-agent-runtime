package com.mcpruntime.core.security;

import com.mcpruntime.core.registry.ToolExecutionContext;

/**
 * A security check applied before a tool is allowed to execute.
 * <p>
 * Implementations should throw a runtime exception (typically
 * {@link SecurityException} or {@link com.mcpruntime.core.security.exception.ToolSecurityException})
 * when the check fails.
 */
@FunctionalInterface
public interface SecurityPolicy {

    /**
     * Perform a security check on the given tool invocation.
     *
     * @param invocation the tool execution context
     * @throws SecurityException if the check fails
     */
    void check(ToolExecutionContext invocation);
}
