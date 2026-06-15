package com.mcpruntime.core.observability;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A single span in the Agent runtime's trace tree.
 * <p>
 * Unlike standard HTTP-distributed tracing, Agent spans represent
 * logical units of Agent execution: LLM calls, tool executions,
 * context builds, and retries. Each span carries a traceId that
 * links it to a single user request, and a parentSpanId that
 * expresses the parent-child relationship in the span tree.
 * <p>
 * Immutable after creation. Created via {@link AgentContext#withChild}
 * and closed via {@link #complete}.
 */
public final class AgentSpan {

    private final String traceId;
    private final String spanId;
    private final String parentSpanId;
    private final String spanName;
    private final SpanType spanType;
    private final Instant startTime;
    private volatile Instant endTime;
    private volatile Status status;
    private volatile String errorMessage;
    private volatile long durationNanos;

    // ========================================================================
    // Construction
    // ========================================================================

    AgentSpan(String traceId, String spanId, String parentSpanId,
              String spanName, SpanType spanType) {
        this.traceId = Objects.requireNonNull(traceId, "traceId");
        this.spanId = Objects.requireNonNull(spanId, "spanId");
        this.parentSpanId = parentSpanId;
        this.spanName = Objects.requireNonNull(spanName, "spanName");
        this.spanType = Objects.requireNonNull(spanType, "spanType");
        this.startTime = Instant.now();
        this.status = Status.IN_FLIGHT;
    }

    /**
     * Create a root span (no parent).
     */
    public static AgentSpan root(String spanName, SpanType spanType) {
        return new AgentSpan(
                UUID.randomUUID().toString().replace("-", ""),
                UUID.randomUUID().toString().replace("-", ""),
                null,
                spanName,
                spanType);
    }

    // ========================================================================
    // Lifecycle
    // ========================================================================

    /**
     * Mark this span as completed successfully.
     */
    public void complete() {
        this.status = Status.SUCCESS;
        this.endTime = Instant.now();
        this.durationNanos = java.time.Duration.between(startTime, endTime).toNanos();
    }

    /**
     * Mark this span as completed successfully with a known duration.
     * <p>
     * Prefer this overload when the duration was measured externally
     * (e.g. by the calling code before/after an operation). Note that
     * {@code endTime} is set to the current system clock at the time
     * of this call, which may differ slightly from {@code startTime +
     * durationNanos} due to scheduling delays. If sub-microsecond
     * consistency is required, use the no-arg {@link #complete()}.
     */
    public void complete(long durationNanos) {
        this.status = Status.SUCCESS;
        this.endTime = Instant.now();
        this.durationNanos = durationNanos;
    }

    /**
     * Mark this span as failed with an error.
     */
    public void fail(String errorMessage) {
        this.status = Status.FAILURE;
        this.errorMessage = errorMessage;
        this.endTime = Instant.now();
        this.durationNanos = java.time.Duration.between(startTime, endTime).toNanos();
    }

    /**
     * Mark this span as failed with a known duration.
     */
    public void fail(String errorMessage, long durationNanos) {
        this.status = Status.FAILURE;
        this.errorMessage = errorMessage;
        this.endTime = Instant.now();
        this.durationNanos = durationNanos;
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

    public String getSpanName() {
        return spanName;
    }

    public SpanType getSpanType() {
        return spanType;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public Status getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public long getDurationNanos() {
        return durationNanos;
    }

    public boolean isCompleted() {
        return status != Status.IN_FLIGHT;
    }

    // ========================================================================
    // Enums
    // ========================================================================

    /**
     * Types of spans in the Agent runtime trace tree.
     */
    public enum SpanType {
        /** A call to an LLM (prompt → completion). */
        LLM_CALL,
        /** Execution of a single tool. */
        TOOL_EXECUTION,
        /** Building the context window for an LLM call. */
        CONTEXT_BUILD,
        /** A retry attempt following a failed tool call. */
        RETRY
    }

    /**
     * Span completion status.
     */
    public enum Status {
        IN_FLIGHT,
        SUCCESS,
        FAILURE
    }
}
