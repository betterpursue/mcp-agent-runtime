package com.mcpruntime.core.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Central span recorder for the Agent runtime's tracing system.
 * <p>
 * Collects {@link AgentSpan} instances from all execution paths
 * and delivers them to registered consumers in batches. By default,
 * spans are recorded to the SLF4J log as a fallback. In production,
 * consumers can send spans to Jaeger, Elastic APM, or any other
 * distributed tracing backend.
 * <p>
 * <b>Thread safety:</b> All recorder operations are lock-free.
 * Spans are enqueued via a {@link ConcurrentLinkedQueue} and
 * drained periodically by a scheduled executor.
 * <p>
 * <b>Design decision:</b> record() is fire-and-forget. The calling
 * thread pays O(1) enqueue cost regardless of consumer latency.
 */
public class AgentTracer {

    private static final Logger log = LoggerFactory.getLogger(AgentTracer.class);

    private final Queue<AgentSpan> pendingSpans = new ConcurrentLinkedQueue<>();
    private final List<Consumer<List<AgentSpan>>> consumers = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler;
    private final int batchSize;

    private volatile boolean running = true;

    /**
     * Create an AgentTracer with default settings.
     * <ul>
     *   <li>Flush interval: 5 seconds</li>
     *   <li>Batch size: 50 spans</li>
     *   <li>Default consumer: SLF4J logging</li>
     * </ul>
     */
    public AgentTracer() {
        this(5_000, 50);
    }

    /**
     * Create an AgentTracer with custom flush interval and batch size.
     */
    public AgentTracer(long flushIntervalMillis, int batchSize) {
        this.batchSize = batchSize;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "agent-tracer");
            t.setDaemon(true);
            return t;
        });

        // Default: log all spans
        addConsumer(spans -> {
            for (AgentSpan span : spans) {
                log.debug("[Trace: {}] {} ({}) — {} ({}ns)",
                        span.getTraceId(), span.getSpanName(),
                        span.getSpanType(), span.getStatus(),
                        span.getDurationNanos());
            }
        });

        this.scheduler.scheduleAtFixedRate(this::flush,
                flushIntervalMillis, flushIntervalMillis, TimeUnit.MILLISECONDS);
    }

    // ========================================================================
    // Recording
    // ========================================================================

    /**
     * Record a single span. Non-blocking — returns immediately.
     */
    public void record(AgentSpan span) {
        if (!running) {
            log.warn("AgentTracer is shutdown, dropping span: {}", span.getSpanId());
            return;
        }
        if (span != null) {
            pendingSpans.offer(span);
        }
    }

    // ========================================================================
    // Consumer management
    // ========================================================================

    /**
     * Register a consumer that receives batches of completed spans.
     */
    public void addConsumer(Consumer<List<AgentSpan>> consumer) {
        this.consumers.add(consumer);
    }

    /**
     * Remove a previously registered consumer.
     */
    public void removeConsumer(Consumer<List<AgentSpan>> consumer) {
        this.consumers.remove(consumer);
    }

    // ========================================================================
    // Flush
    // ========================================================================

    /**
     * Manually trigger a flush of pending spans to all consumers.
     * Drains as many batches as needed, up to {@code batchSize * 5}
     * spans per call, to prevent queue growth under high throughput.
     */
    public void flush() {
        int drained = 0;
        int maxDrain = batchSize * 5;

        while (drained < maxDrain) {
            List<AgentSpan> batch = new ArrayList<>(batchSize);
            for (int i = 0; i < batchSize; i++) {
                AgentSpan span = pendingSpans.poll();
                if (span == null) break;
                batch.add(span);
                drained++;
            }

            if (batch.isEmpty()) break;
            deliver(batch);
        }

        // If more spans still remain, schedule an immediate flush
        if (!pendingSpans.isEmpty()) {
            scheduler.schedule(this::flush, 100, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Flush all remaining spans regardless of batch size.
     */
    public void flushAll() {
        List<AgentSpan> remaining = new ArrayList<>();
        while (true) {
            AgentSpan span = pendingSpans.poll();
            if (span == null) break;
            remaining.add(span);
        }
        if (!remaining.isEmpty()) {
            deliver(remaining);
        }
    }

    // ========================================================================
    // Shutdown
    // ========================================================================

    /**
     * Shutdown the tracer. Flushes all remaining spans before shutting down.
     */
    public void shutdown() {
        this.running = false;
        flushAll();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Current number of pending (unflushed) spans.
     */
    public int pendingCount() {
        return pendingSpans.size();
    }

    // ========================================================================
    // Internal
    // ========================================================================

    private void deliver(List<AgentSpan> batch) {
        for (Consumer<List<AgentSpan>> consumer : consumers) {
            try {
                consumer.accept(batch);
            } catch (Exception e) {
                log.error("Span consumer threw exception", e);
            }
        }
    }
}
