package com.mcpruntime.core.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Micrometer-based monitoring for Tool executions.
 * <p>
 * Exposes per-tool metrics:
 * <ul>
 *   <li>Execution latency (Timer) — P50, P95, P99 percentiles</li>
 *   <li>Error count (Counter) — per tool failure rate</li>
 *   <li>Execution count (Counter) — total invocation count</li>
 * </ul>
 * <p>
 * <b>Percentiles are expensive.</b> By default, only frequently
 * invoked tools ({@link #HIGH_FREQUENCY_TOOLS}) get percentile
 * tracking. Other tools get average-only timers.
 */
public class ToolMonitor implements ToolStatsProvider {

    private static final Logger log = LoggerFactory.getLogger(ToolMonitor.class);

    private final MeterRegistry meterRegistry;
    private final Set<String> highFrequencyTools;
    private final Map<String, Timer> toolTimers = new ConcurrentHashMap<>();
    private final Map<String, Counter> toolErrorCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> toolExecutionCounters = new ConcurrentHashMap<>();
    private final Map<String, WindowedStats> windowedStats = new ConcurrentHashMap<>();

    /**
     * Create a ToolMonitor with a default set of high-frequency tools
     * that get full percentile histograms.
     */
    public ToolMonitor(MeterRegistry meterRegistry) {
        this(meterRegistry, Set.of("search", "get_weather", "read_file", "write_file"));
    }

    /**
     * Create a ToolMonitor with a custom set of high-frequency tools.
     *
     * @param highFrequencyTools tool names that should get P50/P95/P99 tracking;
     *                           other tools only record average latency
     */
    public ToolMonitor(MeterRegistry meterRegistry, Set<String> highFrequencyTools) {
        this.meterRegistry = meterRegistry;
        this.highFrequencyTools = highFrequencyTools;
    }

    // ========================================================================
    // Recording
    // ========================================================================

    /**
     * Record a tool execution.
     *
     * @param toolName      the tool that was executed
     * @param durationNanos wall-clock execution duration in nanoseconds
     * @param success       whether the execution completed without error
     */
    public void recordExecution(String toolName, long durationNanos, boolean success) {
        Timer timer = toolTimers.computeIfAbsent(toolName, this::createTimer);
        timer.record(durationNanos, TimeUnit.NANOSECONDS);

        Counter execCounter = toolExecutionCounters.computeIfAbsent(toolName,
                n -> Counter.builder("mcp.tool.executions")
                        .tag("tool", n)
                        .description("Total number of tool executions")
                        .register(meterRegistry));
        execCounter.increment();

        if (!success) {
            Counter errorCounter = toolErrorCounters.computeIfAbsent(toolName,
                    n -> Counter.builder("mcp.tool.errors")
                            .tag("tool", n)
                            .description("Total number of tool execution errors")
                            .register(meterRegistry));
            errorCounter.increment();
        }

        // Track windowed stats for ToolStatsProvider
        windowedStats.computeIfAbsent(toolName, n -> new WindowedStats())
                .record(durationNanos, success);
    }

    // ========================================================================
    // Query
    // ========================================================================

    /**
     * Get the average latency for a tool over the last N milliseconds.
     */
    @Override
    public double getAverageLatency(String toolName, Duration window) {
        return getAverageLatency(toolName, window.toMillis());
    }

    @Override
    public double getErrorRate(String toolName, Duration window) {
        return getErrorRate(toolName, window.toMillis());
    }

    /**
     * Get the average latency for a tool over the last N milliseconds.
     */
    public double getAverageLatency(String toolName, long windowMillis) {
        WindowedStats stats = windowedStats.get(toolName);
        if (stats == null) return 0.0;
        return stats.averageLatency(windowMillis);
    }

    /**
     * Get the error rate for a tool over the last N milliseconds.
     * Returns a value between 0.0 and 1.0.
     */
    public double getErrorRate(String toolName, long windowMillis) {
        WindowedStats stats = windowedStats.get(toolName);
        if (stats == null) return 0.0;
        return stats.errorRate(windowMillis);
    }

    /**
     * Get names of all tools that have been monitored.
     */
    public Set<String> getTrackedTools() {
        return Set.copyOf(toolTimers.keySet());
    }

    // ========================================================================
    // Internal
    // ========================================================================

    private Timer createTimer(String toolName) {
        Timer.Builder builder = Timer.builder("mcp.tool.execution")
                .tag("tool", toolName)
                .description("Tool execution latency");

        if (shouldTrackPercentiles(toolName)) {
            builder.publishPercentiles(0.5, 0.95, 0.99);
        }

        return builder.register(meterRegistry);
    }

    private boolean shouldTrackPercentiles(String toolName) {
        return highFrequencyTools.contains(toolName);
    }

    /**
     * Simple sliding-window statistics for recent tool performance.
     * <p>
     * Not perfectly precise but good enough for cache policy decisions
     * and dashboards. Uses a fixed-size ring buffer internally.
     */
    static class WindowedStats {

        private static final int WINDOW_SIZE = 1000;

        private final long[] timestamps = new long[WINDOW_SIZE];
        private final long[] latencies = new long[WINDOW_SIZE];
        private final boolean[] successes = new boolean[WINDOW_SIZE];
        private int index = 0;
        private int count = 0;

        synchronized void record(long latencyNanos, boolean success) {
            int slot = index % WINDOW_SIZE;
            timestamps[slot] = System.currentTimeMillis();
            latencies[slot] = latencyNanos;
            successes[slot] = success;
            index++;
            if (count < WINDOW_SIZE) count++;
        }

        synchronized double averageLatency(long windowMillis) {
            long cutoff = System.currentTimeMillis() - windowMillis;
            long totalNanos = 0;
            int samples = 0;
            int start = Math.max(0, index - count);
            for (int i = start; i < index; i++) {
                int slot = i % WINDOW_SIZE;
                if (timestamps[slot] >= cutoff) {
                    totalNanos += latencies[slot];
                    samples++;
                }
            }
            return samples == 0 ? 0.0 : (double) totalNanos / samples;
        }

        synchronized double errorRate(long windowMillis) {
            long cutoff = System.currentTimeMillis() - windowMillis;
            int errors = 0;
            int total = 0;
            int start = Math.max(0, index - count);
            for (int i = start; i < index; i++) {
                int slot = i % WINDOW_SIZE;
                if (timestamps[slot] >= cutoff) {
                    if (!successes[slot]) errors++;
                    total++;
                }
            }
            return total == 0 ? 0.0 : (double) errors / total;
        }
    }
}
