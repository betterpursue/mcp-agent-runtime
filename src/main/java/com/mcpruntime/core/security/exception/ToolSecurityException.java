package com.mcpruntime.core.security.exception;

/**
 * Base exception for tool security violations.
 * <p>
 * Thrown when a tool invocation is blocked by a security policy
 * (validation, permission, or rate limit check).
 */
public class ToolSecurityException extends RuntimeException {

    private final String toolName;
    private final String policyName;

    public ToolSecurityException(String toolName, String policyName, String message) {
        super(message);
        this.toolName = toolName;
        this.policyName = policyName;
    }

    public ToolSecurityException(String toolName, String policyName, String message, Throwable cause) {
        super(message, cause);
        this.toolName = toolName;
        this.policyName = policyName;
    }

    public String getToolName() {
        return toolName;
    }

    public String getPolicyName() {
        return policyName;
    }
}
