package com.mcpruntime.core.security;

import com.mcpruntime.core.registry.ToolExecutionContext;
import com.mcpruntime.core.security.exception.RateLimitException;
import com.mcpruntime.core.security.exception.ToolSecurityException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SandboxInterceptor}.
 */
class SandboxInterceptorTest {

    @Test
    void shouldPassWhenAllPoliciesPass() {
        SandboxInterceptor interceptor = SandboxInterceptor.builder()
                .addValidationPolicy(new SqlInjectionPolicy("queryDatabase"))
                .rateLimitPolicy(new RateLimitPolicy(5, Duration.ofSeconds(1)))
                .build();

        ToolExecutionContext ctx = ToolExecutionContext.builder()
                .sessionId("s1")
                .traceId("t1")
                .args(Map.of("sql", "SELECT * FROM users"))
                .context(Map.of("tool.name", "queryDatabase"))
                .build();

        assertDoesNotThrow(() -> interceptor.beforeExecute(ctx));
    }

    @Test
    void shouldBlockWhenValidationFails() {
        SandboxInterceptor interceptor = SandboxInterceptor.builder()
                .addValidationPolicy(new SqlInjectionPolicy("queryDatabase"))
                .build();

        ToolExecutionContext ctx = ToolExecutionContext.builder()
                .sessionId("s1")
                .traceId("t1")
                .args(Map.of("sql", "DROP TABLE users"))
                .context(Map.of("tool.name", "queryDatabase"))
                .build();

        ToolSecurityException ex = assertThrows(ToolSecurityException.class,
                () -> interceptor.beforeExecute(ctx));
        assertTrue(ex.getMessage().contains("SQL contains forbidden"));
    }

    @Test
    void shouldBlockWhenRateLimitExceeded() {
        SandboxInterceptor interceptor = SandboxInterceptor.builder()
                .rateLimitPolicy(new RateLimitPolicy(1, Duration.ofSeconds(1)))
                .build();

        ToolExecutionContext ctx = ToolExecutionContext.builder()
                .sessionId("s1")
                .traceId("t1")
                .args(Map.of("q", "query"))
                .context(Map.of("tool.name", "search"))
                .build();

        // First call passes
        assertDoesNotThrow(() -> interceptor.beforeExecute(ctx));

        // Second call exceeds rate limit
        assertThrows(RateLimitException.class,
                () -> interceptor.beforeExecute(ctx));
    }

    @Test
    void shouldBlockWhenSecurityPolicyFails() {
        SecurityPolicy denyAll = invocation -> {
            throw new SecurityException("Tool not authorized");
        };

        SandboxInterceptor interceptor = SandboxInterceptor.builder()
                .addSecurityPolicy(denyAll)
                .build();

        ToolExecutionContext ctx = ToolExecutionContext.builder()
                .sessionId("s1")
                .traceId("t1")
                .args(Map.of())
                .context(Map.of("tool.name", "any"))
                .build();

        ToolSecurityException ex = assertThrows(ToolSecurityException.class,
                () -> interceptor.beforeExecute(ctx));
        assertTrue(ex.getMessage().contains("Tool not authorized"));
        assertEquals("any", ex.getToolName());
    }

    @Test
    void shouldHandleEmptyPolicies() {
        SandboxInterceptor interceptor = new SandboxInterceptor();

        ToolExecutionContext ctx = ToolExecutionContext.builder()
                .sessionId("s1")
                .traceId("t1")
                .args(Map.of("sql", "DROP TABLE users"))
                .context(Map.of("tool.name", "queryDatabase"))
                .build();

        // No policies means no blocking
        assertDoesNotThrow(() -> interceptor.beforeExecute(ctx));
    }

    @Test
    void shouldApplyMultiplePoliciesInOrder() {
        ValidationPolicy blockingPolicy = (toolName, args) -> {
            throw new SecurityException("Arg validation failed");
        };

        SecurityPolicy blockingSecurity = invocation -> {
            throw new SecurityException("Security check failed");
        };

        SandboxInterceptor interceptor = SandboxInterceptor.builder()
                .addValidationPolicy(blockingPolicy)  // fails first
                .addSecurityPolicy(blockingSecurity)
                .build();

        ToolExecutionContext ctx = ToolExecutionContext.builder()
                .sessionId("s1")
                .traceId("t1")
                .args(Map.of("key", "value"))
                .context(Map.of("tool.name", "test"))
                .build();

        // Validation blocks before security check
        ToolSecurityException ex = assertThrows(ToolSecurityException.class,
                () -> interceptor.beforeExecute(ctx));
        assertTrue(ex.getMessage().contains("Arg validation"));
    }

    @Test
    void shouldResolveToolNameFromContext() {
        SecurityPolicy capturingPolicy = invocation -> {
            Map<String, Object> ctx = invocation.getContext();
            assertEquals("weather", ctx.get("tool.name"));
        };

        SandboxInterceptor interceptor = SandboxInterceptor.builder()
                .addSecurityPolicy(capturingPolicy)
                .build();

        ToolExecutionContext ctx = ToolExecutionContext.builder()
                .sessionId("s1")
                .traceId("t1")
                .args(Map.of())
                .context(Map.of("tool.name", "weather"))
                .build();

        assertDoesNotThrow(() -> interceptor.beforeExecute(ctx));
    }

    @Test
    void shouldFallbackToUnknownToolName() {
        SecurityPolicy capturingPolicy = invocation -> {
            // Ensure no exception is thrown even without tool name
        };

        SandboxInterceptor interceptor = SandboxInterceptor.builder()
                .addSecurityPolicy(capturingPolicy)
                .build();

        ToolExecutionContext ctx = ToolExecutionContext.builder()
                .sessionId("s1")
                .traceId("t1")
                .args(Map.of())
                .build(); // no tool.name in context

        assertDoesNotThrow(() -> interceptor.beforeExecute(ctx));
    }
}
