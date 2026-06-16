package com.mcpruntime.core.security;

import com.mcpruntime.core.registry.ToolExecutionContext;
import com.mcpruntime.core.registry.ToolExecutionResult;
import com.mcpruntime.core.registry.ToolInterceptor;
import com.mcpruntime.core.security.exception.ToolSecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * Security interceptor that guards tool execution with layered policies.
 * <p>
 * Composes three types of checks, applied in order:
 * <ol>
 *   <li><b>Validation</b> — {@link ValidationPolicy}: argument correctness,
 *       type/range checks, injection pattern detection</li>
 *   <li><b>Permission</b> — {@link SecurityPolicy}: authorization, blacklist/whitelist</li>
 *   <li><b>Rate limiting</b> — {@link RateLimitPolicy}: per-tool call frequency</li>
 * </ol>
 * <p>
 * Implements {@link ToolInterceptor} and plugs into the existing
 * {@link com.mcpruntime.core.registry.InterceptorChain}.
 */
public class SandboxInterceptor implements ToolInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SandboxInterceptor.class);

    private final List<ValidationPolicy> validationPolicies;
    private final List<SecurityPolicy> securityPolicies;
    private final RateLimitPolicy rateLimitPolicy;

    public SandboxInterceptor(List<ValidationPolicy> validationPolicies,
                              List<SecurityPolicy> securityPolicies,
                              RateLimitPolicy rateLimitPolicy) {
        this.validationPolicies = validationPolicies != null
                ? List.copyOf(validationPolicies) : List.of();
        this.securityPolicies = securityPolicies != null
                ? List.copyOf(securityPolicies) : List.of();
        this.rateLimitPolicy = rateLimitPolicy;
    }

    public SandboxInterceptor() {
        this(Collections.emptyList(), Collections.emptyList(), null);
    }

    @Override
    public void beforeExecute(ToolExecutionContext ctx) {
        // Derive tool name from context metadata
        String toolName = resolveToolName(ctx);

        // 1. Argument validation
        for (ValidationPolicy policy : validationPolicies) {
            try {
                policy.validate(toolName, ctx.getArgs());
            } catch (SecurityException e) {
                log.warn("Validation blocked tool '{}': {}", toolName, e.getMessage());
                throw new ToolSecurityException(toolName,
                        policy.getClass().getSimpleName(), e.getMessage(), e);
            }
        }

        // 2. Permission checks
        for (SecurityPolicy policy : securityPolicies) {
            try {
                policy.check(ctx);
            } catch (SecurityException e) {
                log.warn("Security policy blocked tool '{}': {}", toolName, e.getMessage());
                throw new ToolSecurityException(toolName,
                        policy.getClass().getSimpleName(), e.getMessage(), e);
            }
        }

        // 3. Rate limiting
        if (rateLimitPolicy != null) {
            rateLimitPolicy.tryAcquire(toolName);
        }
    }

    /**
     * Resolve the tool name from the execution context.
     * <p>
     * The tool name is stored in the context metadata map under the key {@code "tool.name"}.
     * This is set by the execution engine before invoking the interceptor chain.
     */
    private String resolveToolName(ToolExecutionContext ctx) {
        Object name = ctx.getContext().get("tool.name");
        if (name instanceof String) {
            return (String) name;
        }
        // Fallback: try to extract from "tool" key
        Object tool = ctx.getContext().get("tool");
        return tool != null ? tool.toString() : "unknown";
    }

    // ========================================================================
    // Builder
    // ========================================================================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<ValidationPolicy> validationPolicies = List.of();
        private List<SecurityPolicy> securityPolicies = List.of();
        private RateLimitPolicy rateLimitPolicy;

        public Builder validationPolicies(List<ValidationPolicy> policies) {
            this.validationPolicies = policies;
            return this;
        }

        public Builder addValidationPolicy(ValidationPolicy policy) {
            this.validationPolicies = List.copyOf(
                    java.util.stream.Stream.concat(
                            this.validationPolicies.stream(),
                            java.util.stream.Stream.of(policy)
                    ).toList()
            );
            return this;
        }

        public Builder securityPolicies(List<SecurityPolicy> policies) {
            this.securityPolicies = policies;
            return this;
        }

        public Builder addSecurityPolicy(SecurityPolicy policy) {
            this.securityPolicies = List.copyOf(
                    java.util.stream.Stream.concat(
                            this.securityPolicies.stream(),
                            java.util.stream.Stream.of(policy)
                    ).toList()
            );
            return this;
        }

        public Builder rateLimitPolicy(RateLimitPolicy policy) {
            this.rateLimitPolicy = policy;
            return this;
        }

        public SandboxInterceptor build() {
            return new SandboxInterceptor(validationPolicies, securityPolicies, rateLimitPolicy);
        }
    }
}
