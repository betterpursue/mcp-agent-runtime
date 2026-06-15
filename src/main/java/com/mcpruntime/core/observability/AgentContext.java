package com.mcpruntime.core.observability;

import java.util.Objects;
import java.util.UUID;

/**
 * Explicit context carrier for the Agent runtime's tracing system.
 * <p>
 * Every end-to-end request (user message → Agent response) starts with
 * an AgentContext that carries the traceId. Child contexts are created
 * by {@link #withChild}, linking back to the parent via parentSpanId.
 * <p>
 * <b>Why not ThreadLocal?</b> In Agent runtimes, tasks may switch threads
 * (virtual threads, async callbacks, fork-join). ThreadLocal-based trace
 * propagation is fragile in these scenarios. AgentContext is passed
 * explicitly through method parameters, guaranteeing no context loss.
 * <p>
 * Immutable after creation. All mutations return new instances.
 */
public final class AgentContext {

    private final String traceId;
    private final String spanId;
    private final String parentSpanId;
    private final String sessionId;
    private final int retryCount;

    private AgentContext(String traceId, String spanId, String parentSpanId,
                         String sessionId, int retryCount) {
        this.traceId = Objects.requireNonNull(traceId, "traceId");
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.sessionId = sessionId;
        this.retryCount = retryCount;
    }

    // ========================================================================
    // Factory methods
    // ========================================================================

    /**
     * Create a root context (entry point of a user request).
     */
    public static AgentContext root(String sessionId) {
        return new AgentContext(
                UUID.randomUUID().toString().replace("-", ""),
                null,
                null,
                sessionId,
                0);
    }

    /**
     * Create a root context with an explicit trace ID.
     * Useful when trace IDs are generated externally (e.g. API gateway).
     */
    public static AgentContext root(String sessionId, String traceId) {
        return new AgentContext(
                Objects.requireNonNull(traceId),
                null,
                null,
                sessionId,
                0);
    }

    // ========================================================================
    // Child context creation
    // ========================================================================

    /**
     * Create a child context for a sub-operation (tool call, LLM call, etc.).
     * <p>
     * The child shares the parent's traceId, gets a new spanId, and
     * links back to this context via parentSpanId.
     */
    public AgentContext withChild() {
        return new AgentContext(
                this.traceId,
                generateSpanId(),
                this.spanId,
                this.sessionId,
                this.retryCount);
    }

    /**
     * Create a child context that increments the retry counter.
     * Used for retry attempts — the new context has retryCount = parent + 1.
     */
    public AgentContext withRetry() {
        return new AgentContext(
                this.traceId,
                generateSpanId(),
                this.spanId,
                this.sessionId,
                this.retryCount + 1);
    }

    // ========================================================================
    // Accessors
    // ========================================================================

    public String getTraceId() {
        return traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public String getParentSpanId() {
        return parentSpanId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public boolean hasSpanId() {
        return spanId != null;
    }

    // ========================================================================
    // Internal
    // ========================================================================

    private static String generateSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
