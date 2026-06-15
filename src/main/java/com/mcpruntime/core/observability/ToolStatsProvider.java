package com.mcpruntime.core.observability;

import java.time.Duration;

/**
 * Provider interface for tool execution statistics.
 * <p>
 * Exposes recent latency and error rate data for individual tools.
 * Used by {@link com.mcpruntime.core.state.StateManager} to make
 * caching decisions: if a tool has high latency, caching its results
 * is more beneficial than if it runs in < 50ms.
 * <p>
 * Implementations should use sliding windows (e.g. the last 5 minutes
 * of executions) rather than global averages, because tool performance
 * characteristics change over time.
 */
public interface ToolStatsProvider {

    /**
     * Get the average execution latency for a tool over a recent time window.
     *
     * @param toolName the tool to query
     * @param window   the time window to consider
     * @return average latency in milliseconds, or 0 if no data available
     */
    double getAverageLatency(String toolName, Duration window);

    /**
     * Get the error rate for a tool over a recent time window.
     *
     * @param toolName the tool to query
     * @param window   the time window to consider
     * @return error rate between 0.0 (no errors) and 1.0 (all failed)
     */
    double getErrorRate(String toolName, Duration window);
}
