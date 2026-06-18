package com.mcpruntime.benchmark;

/**
 * A single test case for tool selection accuracy benchmarking.
 * Each case has a natural language query and the name of the
 * tool that a human would consider the correct choice.
 */
public record TestCase(String query, String expectedTool, String description) {

    /** Convenience factory for readable code. */
    public static TestCase of(String query, String expectedTool, String description) {
        return new TestCase(query, expectedTool, description);
    }
}
