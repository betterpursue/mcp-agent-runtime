package com.mcpruntime.core.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for the observability module.
 * <p>
 * Covers: AgentSpan lifecycle, AgentContext propagation, AgentTracer
 * recording and flushing, ToolMonitor metrics, LlmIORecorder recording
 * and redaction.
 */
class ObservabilityTest {

    // ========================================================================
    // AgentSpan
    // ========================================================================

    @Test
    void shouldCreateRootSpan() {
        AgentSpan span = AgentSpan.root("test-span", AgentSpan.SpanType.LLM_CALL);

        assertNotNull(span.getTraceId());
        assertNotNull(span.getSpanId());
        assertNull(span.getParentSpanId());
        assertEquals("test-span", span.getSpanName());
        assertEquals(AgentSpan.SpanType.LLM_CALL, span.getSpanType());
        assertEquals(AgentSpan.Status.IN_FLIGHT, span.getStatus());
        assertFalse(span.isCompleted());
    }

    @Test
    void shouldCompleteSpan() {
        AgentSpan span = AgentSpan.root("op", AgentSpan.SpanType.TOOL_EXECUTION);
        span.complete();

        assertEquals(AgentSpan.Status.SUCCESS, span.getStatus());
        assertTrue(span.isCompleted());
        assertNotNull(span.getEndTime());
        assertTrue(span.getDurationNanos() >= 0);
    }

    @Test
    void shouldCompleteSpanWithDuration() {
        AgentSpan span = AgentSpan.root("op", AgentSpan.SpanType.TOOL_EXECUTION);
        span.complete(1_000_000L);

        assertEquals(AgentSpan.Status.SUCCESS, span.getStatus());
        assertEquals(1_000_000L, span.getDurationNanos());
    }

    @Test
    void shouldFailSpan() {
        AgentSpan span = AgentSpan.root("op", AgentSpan.SpanType.LLM_CALL);
        span.fail("Connection timeout");

        assertEquals(AgentSpan.Status.FAILURE, span.getStatus());
        assertEquals("Connection timeout", span.getErrorMessage());
        assertTrue(span.isCompleted());
    }

    @Test
    void shouldFailSpanWithDuration() {
        AgentSpan span = AgentSpan.root("op", AgentSpan.SpanType.RETRY);
        span.fail("Rate limited", 500_000L);

        assertEquals(AgentSpan.Status.FAILURE, span.getStatus());
        assertEquals(500_000L, span.getDurationNanos());
    }

    @Test
    void spanShouldRejectNullTraceId() {
        assertThrows(NullPointerException.class,
                () -> new AgentSpan(null, "span1", null, "name",
                        AgentSpan.SpanType.LLM_CALL));
    }

    @Test
    void spanShouldRejectNullSpanId() {
        assertThrows(NullPointerException.class,
                () -> new AgentSpan("trace1", null, null, "name",
                        AgentSpan.SpanType.LLM_CALL));
    }

    @Test
    void spanShouldRejectNullName() {
        assertThrows(NullPointerException.class,
                () -> new AgentSpan("trace1", "span1", null, null,
                        AgentSpan.SpanType.LLM_CALL));
    }

    // ========================================================================
    // AgentContext
    // ========================================================================

    @Test
    void shouldCreateRootContext() {
        AgentContext ctx = AgentContext.root("session-1");

        assertNotNull(ctx.getTraceId());
        assertEquals("session-1", ctx.getSessionId());
        assertFalse(ctx.hasSpanId());
        assertEquals(0, ctx.getRetryCount());
    }

    @Test
    void shouldCreateRootContextWithExplicitTraceId() {
        AgentContext ctx = AgentContext.root("session-1", "my-trace-id");

        assertEquals("my-trace-id", ctx.getTraceId());
        assertEquals("session-1", ctx.getSessionId());
    }

    @Test
    void shouldCreateChildContext() {
        AgentContext parent = AgentContext.root("session-1");
        AgentContext child = parent.withChild();

        assertEquals(parent.getTraceId(), child.getTraceId());
        assertEquals("session-1", child.getSessionId());
        assertTrue(child.hasSpanId());
        assertEquals(parent.getSpanId(), child.getParentSpanId());
        assertEquals(0, child.getRetryCount());
    }

    @Test
    void shouldCreateRetryContext() {
        AgentContext parent = AgentContext.root("session-1");
        AgentContext retry = parent.withRetry();

        assertEquals(parent.getTraceId(), retry.getTraceId());
        assertEquals(1, retry.getRetryCount());
    }

    @Test
    void retryContextShouldIncrementCounter() {
        AgentContext ctx = AgentContext.root("s1");
        ctx = ctx.withRetry();
        ctx = ctx.withRetry();

        assertEquals(2, ctx.getRetryCount());
    }

    @Test
    void contextShouldPropagateSessionIdThroughChildren() {
        AgentContext root = AgentContext.root("my-session");
        AgentContext child = root.withChild();
        AgentContext grandchild = child.withChild();

        assertEquals("my-session", grandchild.getSessionId());
    }

    @Test
    void childShouldHaveUniqueSpanId() {
        AgentContext root = AgentContext.root("s1");
        AgentContext c1 = root.withChild();
        AgentContext c2 = root.withChild();

        assertNotNull(c1.getSpanId());
        assertNotNull(c2.getSpanId());
        assertNotEquals(c1.getSpanId(), c2.getSpanId());
    }

    @Test
    void shouldRejectNullTraceId() {
        assertThrows(NullPointerException.class,
                () -> AgentContext.root("s1", null));
    }

    // ========================================================================
    // AgentTracer
    // ========================================================================

    @Test
    void shouldRecordSpan() {
        AgentTracer tracer = new AgentTracer(10_000, 50);
        AgentSpan span = AgentSpan.root("test", AgentSpan.SpanType.LLM_CALL);
        span.complete();

        tracer.record(span);

        assertTrue(tracer.pendingCount() > 0);
        tracer.shutdown();
    }

    @Test
    void shouldFlushSpans() {
        AgentTracer tracer = new AgentTracer(10_000, 50);

        AgentSpan span = AgentSpan.root("test", AgentSpan.SpanType.TOOL_EXECUTION);
        span.complete();
        tracer.record(span);

        tracer.flush();

        assertEquals(0, tracer.pendingCount());
        tracer.shutdown();
    }

    @Test
    void shouldFlushAllSpans() {
        AgentTracer tracer = new AgentTracer(10_000, 50);

        for (int i = 0; i < 10; i++) {
            AgentSpan span = AgentSpan.root("op-" + i, AgentSpan.SpanType.LLM_CALL);
            span.complete();
            tracer.record(span);
        }

        tracer.flushAll();
        assertEquals(0, tracer.pendingCount());
        tracer.shutdown();
    }

    @Test
    void shouldDeliverSpansToConsumer() throws InterruptedException {
        AgentTracer tracer = new AgentTracer(10_000, 50);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedSpanName = new AtomicReference<>();

        tracer.addConsumer(spans -> {
            if (!spans.isEmpty()) {
                receivedSpanName.set(spans.get(0).getSpanName());
                latch.countDown();
            }
        });

        AgentSpan span = AgentSpan.root("delivery-test", AgentSpan.SpanType.CONTEXT_BUILD);
        span.complete();
        tracer.record(span);
        tracer.flush();

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals("delivery-test", receivedSpanName.get());
        tracer.shutdown();
    }

    @Test
    void shouldDropSpansAfterShutdown() {
        AgentTracer tracer = new AgentTracer(10_000, 50);
        tracer.shutdown();

        AgentSpan span = AgentSpan.root("late", AgentSpan.SpanType.LLM_CALL);
        span.complete();
        // Should not throw
        tracer.record(span);
    }

    @Test
    void shouldHandleNullRecord() {
        AgentTracer tracer = new AgentTracer(10_000, 50);

        tracer.record(null);
        assertEquals(0, tracer.pendingCount());

        tracer.shutdown();
    }

    @Test
    void shouldSupportMultipleConsumers() throws InterruptedException {
        AgentTracer tracer = new AgentTracer(10_000, 50);
        CountDownLatch latch = new CountDownLatch(2);

        tracer.addConsumer(spans -> latch.countDown());
        tracer.addConsumer(spans -> latch.countDown());

        AgentSpan span = AgentSpan.root("multi", AgentSpan.SpanType.LLM_CALL);
        span.complete();
        tracer.record(span);
        tracer.flushAll();

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        tracer.shutdown();
    }

    // ========================================================================
    // ToolMonitor
    // ========================================================================

    @Test
    void shouldRecordSuccessfulExecution() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ToolMonitor monitor = new ToolMonitor(registry);

        monitor.recordExecution("search", 1_000_000L, true);

        assertFalse(monitor.getTrackedTools().isEmpty());
        assertTrue(monitor.getTrackedTools().contains("search"));
    }

    @Test
    void shouldRecordFailedExecution() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ToolMonitor monitor = new ToolMonitor(registry);

        monitor.recordExecution("search", 2_000_000L, false);

        double errorRate = monitor.getErrorRate("search", Duration.ofMinutes(5));
        assertEquals(1.0, errorRate, 0.01);
    }

    @Test
    void shouldReportAverageLatency() throws InterruptedException {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ToolMonitor monitor = new ToolMonitor(registry);

        monitor.recordExecution("search", 10_000_000L, true);  // 10ms
        monitor.recordExecution("search", 20_000_000L, true);  // 20ms

        // Wait briefly to ensure records are in window
        Thread.sleep(10);

        double avg = monitor.getAverageLatency("search", Duration.ofMinutes(5));
        // Average should be ~15ms (15,000,000 nanos)
        assertTrue(avg > 0);
    }

    @Test
    void shouldReturnZeroForUnknownTool() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ToolMonitor monitor = new ToolMonitor(registry);

        assertEquals(0.0, monitor.getAverageLatency("nonexistent", Duration.ofMinutes(1)));
        assertEquals(0.0, monitor.getErrorRate("nonexistent", Duration.ofMinutes(1)));
    }

    @Test
    void shouldReturnEmptyTrackedToolsInitially() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ToolMonitor monitor = new ToolMonitor(registry);

        assertTrue(monitor.getTrackedTools().isEmpty());
    }

    @Test
    void shouldComputeMixedErrorRate() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ToolMonitor monitor = new ToolMonitor(registry);

        monitor.recordExecution("api_call", 100_000L, true);
        monitor.recordExecution("api_call", 200_000L, true);
        monitor.recordExecution("api_call", 300_000L, false);  // 1 out of 3 failed

        double errorRate = monitor.getErrorRate("api_call", Duration.ofMinutes(5));
        assertEquals(1.0 / 3.0, errorRate, 0.05);
    }

    @Test
    void shouldImplementToolStatsProvider() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ToolMonitor monitor = new ToolMonitor(registry);

        assertInstanceOf(ToolStatsProvider.class, monitor);
    }

    // ========================================================================
    // LlmIORecorder
    // ========================================================================

    @Test
    void shouldRecordPrompt() throws InterruptedException {
        LlmIORecorder recorder = new LlmIORecorder(1);
        CountDownLatch latch = new CountDownLatch(1);

        recorder.addConsumer(record -> {
            if (record.getType() == LlmIORecorder.LlmIORecord.RecordType.PROMPT
                    && "test-trace".equals(record.getTraceId())) {
                latch.countDown();
            }
        });

        LlmIORecorder.LlmIORecord record = LlmIORecorder.LlmIORecord.prompt()
                .traceId("test-trace")
                .sessionId("s1")
                .turnNumber(1)
                .modelProvider("openai")
                .modelName("gpt-4")
                .content("Hello, how are you?")
                .tokenCount(10)
                .build();

        recorder.record(record);

        assertTrue(latch.await(2, TimeUnit.SECONDS),
                "Consumer should have received the record");
        recorder.shutdown();
    }

    @Test
    void shouldRecordCompletion() throws InterruptedException {
        LlmIORecorder recorder = new LlmIORecorder(1);
        CountDownLatch latch = new CountDownLatch(1);

        recorder.addConsumer(record -> {
            if (record.getType() == LlmIORecorder.LlmIORecord.RecordType.COMPLETION) {
                latch.countDown();
            }
        });

        LlmIORecorder.LlmIORecord record = LlmIORecorder.LlmIORecord.completion()
                .traceId("trace-2")
                .sessionId("s1")
                .turnNumber(1)
                .modelProvider("anthropic")
                .modelName("claude-3")
                .content("I'm doing well, thanks!")
                .tokenCount(8)
                .build();

        recorder.record(record);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        recorder.shutdown();
    }

    @Test
    void shouldApplyRedaction() throws InterruptedException {
        LlmIORecorder recorder = new LlmIORecorder(1);
        // Install a redactor that masks email addresses
        recorder.setRedactor((content, ctx) ->
                content.replaceAll("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", "[REDACTED]"));

        CountDownLatch latch = new CountDownLatch(1);

        recorder.addConsumer(record -> {
            assertFalse(record.getContent().contains("user@example.com"));
            assertTrue(record.getContent().contains("[REDACTED]"));
            latch.countDown();
        });

        LlmIORecorder.LlmIORecord record = LlmIORecorder.LlmIORecord.prompt()
                .traceId("t1")
                .sessionId("s1")
                .turnNumber(1)
                .modelProvider("openai")
                .modelName("gpt-4")
                .content("My email is user@example.com")
                .tokenCount(10)
                .build();

        recorder.record(record);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        recorder.shutdown();
    }

    @Test
    void shouldDropRecordsAfterShutdown() {
        LlmIORecorder recorder = new LlmIORecorder(1);
        recorder.shutdown();

        LlmIORecorder.LlmIORecord record = LlmIORecorder.LlmIORecord.prompt()
                .traceId("t1").sessionId("s1").turnNumber(1)
                .content("hello").build();

        assertFalse(recorder.record(record));
    }

    @Test
    void shouldUseNoopRedactorByDefault() {
        LlmIORecorder recorder = new LlmIORecorder(1);

        assertInstanceOf(LlmContentRedactor.class, recorder.getRedactor());

        String result = recorder.getRedactor().redact("hello world",
                new LlmContentRedactor.RedactContext("t1", "s1", "PROMPT"));

        assertEquals("hello world", result);
        recorder.shutdown();
    }

    @Test
    void noopRedactorShouldBeAvailableViaStaticMethod() {
        LlmContentRedactor noop = LlmContentRedactor.noop();

        String result = noop.redact("sensitive data",
                new LlmContentRedactor.RedactContext("t1", "s1", "PROMPT"));

        assertEquals("sensitive data", result);
    }

    @Test
    void shouldHandleNullRedactorSetting() {
        LlmIORecorder recorder = new LlmIORecorder(1);
        recorder.setRedactor(null);

        assertNotNull(recorder.getRedactor());
        recorder.shutdown();
    }

    @Test
    void llmIoRecordShouldCarryMetadata() {
        Map<String, Object> metadata = Map.of("temperature", 0.7, "max_tokens", 4096);

        LlmIORecorder.LlmIORecord record = LlmIORecorder.LlmIORecord.completion()
                .traceId("t1").sessionId("s1").turnNumber(1)
                .modelProvider("openai").modelName("gpt-4")
                .content("response").tokenCount(5)
                .metadata(metadata)
                .build();

        assertEquals(0.7, record.getMetadata().get("temperature"));
        assertEquals(4096, record.getMetadata().get("max_tokens"));
    }

    @Test
    void llmIoRecordShouldDefaultMetadataToEmpty() {
        LlmIORecorder.LlmIORecord record = LlmIORecorder.LlmIORecord.prompt()
                .traceId("t1").sessionId("s1").turnNumber(1)
                .content("hello").build();

        assertTrue(record.getMetadata().isEmpty());
    }

    @Test
    void llmIoRecordShouldHaveTimestamp() {
        LlmIORecorder.LlmIORecord record = LlmIORecorder.LlmIORecord.prompt()
                .traceId("t1").sessionId("s1").turnNumber(1)
                .content("hi").build();

        assertNotNull(record.getTimestamp());
    }
}
