package com.mcpruntime.core.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sliding-window rate limiter for tool invocations.
 * <p>
 * Counts calls per tool within a configurable time window.
 * When the limit is exceeded, throws {@link com.mcpruntime.core.security.exception.RateLimitException}.
 * <p>
 * Thread-safe. Uses per-tool counters — tool A's limit is independent of tool B's.
 * <p>
 * This is not a {@link SecurityPolicy} because rate limiting operates at a different
 * granularity (per-tool-name, not per-invocation context) and has different failure semantics.
 */
public class RateLimitPolicy {

    private static final Logger log = LoggerFactory.getLogger(RateLimitPolicy.class);

    private final int maxCalls;
    private final Duration window;
    private final Map<String, SlidingWindowCounter> counters = new ConcurrentHashMap<>();

    public RateLimitPolicy(int maxCalls, Duration window) {
        if (maxCalls <= 0) {
            throw new IllegalArgumentException("maxCalls must be positive, got: " + maxCalls);
        }
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window must be positive, got: " + window);
        }
        this.maxCalls = maxCalls;
        this.window = window;
    }

    /**
     * Attempt to acquire a permit for the given tool name.
     *
     * @param toolName the tool being called
     * @throws com.mcpruntime.core.security.exception.RateLimitException if rate limit exceeded
     */
    public void tryAcquire(String toolName) {
        SlidingWindowCounter counter = counters.computeIfAbsent(toolName, k -> new SlidingWindowCounter());
        int currentCount = counter.incrementAndGet();

        if (currentCount > maxCalls) {
            log.warn("Rate limit exceeded for tool {}: {} calls in {}s (max {})",
                    toolName, currentCount, window.toSeconds(), maxCalls);
            throw new com.mcpruntime.core.security.exception.RateLimitException(
                    "Tool " + toolName + " exceeded rate limit: " + maxCalls
                            + " calls per " + window.toSeconds() + "s");
        }
    }

    public int getMaxCalls() {
        return maxCalls;
    }

    public Duration getWindow() {
        return window;
    }

    /**
     * Reset all counters (useful in tests and when reconfiguring limits).
     */
    public void reset() {
        counters.clear();
    }

    /**
     * Get the current call count within the current window for a tool (for monitoring).
     */
    public int getCurrentCount(String toolName) {
        SlidingWindowCounter counter = counters.get(toolName);
        return counter != null ? counter.getCount() : 0;
    }

    /**
     * Sliding window counter with window-aligned reset.
     * <p>
     * Counters reset at every window boundary (e.g. every 60 seconds on the minute).
     * This means bursts are limited per-window, not per-sliding-moment — a deliberate
     * trade-off: simpler, more predictable, and good enough for tool-level rate limiting.
     */
    private class SlidingWindowCounter {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long windowStart = alignToWindow(Instant.now().toEpochMilli());

        synchronized int incrementAndGet() {
            long now = Instant.now().toEpochMilli();
            long currentWindow = alignToWindow(now);

            if (currentWindow != windowStart) {
                count.set(0);
                windowStart = currentWindow;
            }

            return count.incrementAndGet();
        }

        synchronized int getCount() {
            long now = Instant.now().toEpochMilli();
            long currentWindow = alignToWindow(now);
            if (currentWindow != windowStart) {
                count.set(0);
                windowStart = currentWindow;
            }
            return count.get();
        }

        private long alignToWindow(long epochMillis) {
            long windowMillis = window.toMillis();
            return (epochMillis / windowMillis) * windowMillis;
        }
    }
}
