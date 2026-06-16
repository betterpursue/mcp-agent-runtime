package com.mcpruntime.core.security;

import com.mcpruntime.core.security.exception.RateLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RateLimitPolicy}.
 */
class RateLimitPolicyTest {

    private RateLimitPolicy policy;

    @BeforeEach
    void setUp() {
        // 3 calls per second
        policy = new RateLimitPolicy(3, Duration.ofSeconds(1));
    }

    @Test
    void shouldAllowCallsWithinLimit() {
        assertDoesNotThrow(() -> policy.tryAcquire("weather"));
        assertDoesNotThrow(() -> policy.tryAcquire("weather"));
        assertDoesNotThrow(() -> policy.tryAcquire("weather"));
    }

    @Test
    void shouldThrowWhenLimitExceeded() {
        policy.tryAcquire("weather");
        policy.tryAcquire("weather");
        policy.tryAcquire("weather");

        assertThrows(RateLimitException.class, () -> policy.tryAcquire("weather"));
    }

    @Test
    void shouldTrackSeparateCountsPerTool() {
        // "weather" can be called 3 times
        policy.tryAcquire("weather");
        policy.tryAcquire("weather");
        policy.tryAcquire("weather");
        assertThrows(RateLimitException.class, () -> policy.tryAcquire("weather"));

        // "search" has its own counter
        assertDoesNotThrow(() -> policy.tryAcquire("search"));
    }

    @Test
    void shouldResetCounters() {
        policy.tryAcquire("weather");
        policy.tryAcquire("weather");
        policy.tryAcquire("weather");

        policy.reset();

        assertDoesNotThrow(() -> policy.tryAcquire("weather"));
    }

    @Test
    void shouldReportCurrentCount() {
        assertEquals(0, policy.getCurrentCount("weather"));

        policy.tryAcquire("weather");
        assertEquals(1, policy.getCurrentCount("weather"));

        policy.tryAcquire("weather");
        assertEquals(2, policy.getCurrentCount("weather"));
    }

    @Test
    void shouldRejectInvalidConstruction() {
        assertThrows(IllegalArgumentException.class,
                () -> new RateLimitPolicy(0, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new RateLimitPolicy(-1, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new RateLimitPolicy(5, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new RateLimitPolicy(5, Duration.ofMillis(-1)));
    }

    @Test
    void shouldReportConfiguredLimits() {
        assertEquals(3, policy.getMaxCalls());
        assertEquals(Duration.ofSeconds(1), policy.getWindow());
    }

    @Test
    void shouldAllowDifferentToolsWithDifferentLimits() {
        RateLimitPolicy strictPolicy = new RateLimitPolicy(1, Duration.ofSeconds(10));

        // Each tool can only be called once
        assertDoesNotThrow(() -> strictPolicy.tryAcquire("tool-a"));
        assertThrows(RateLimitException.class, () -> strictPolicy.tryAcquire("tool-a"));

        // Different tool still works
        assertDoesNotThrow(() -> strictPolicy.tryAcquire("tool-b"));
        assertThrows(RateLimitException.class, () -> strictPolicy.tryAcquire("tool-b"));
    }
}
