package com.mcpruntime.core.registry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolExecutionResultTest {

    @Test
    void shouldCreateSuccessResult() {
        ToolExecutionContext ctx = ToolExecutionContext.builder()
            .sessionId("s1")
            .traceId("t1")
            .build();

        ToolExecutionResult result = ToolExecutionResult.success(
            "my_tool", "done", 1_000_000L, ctx);

        assertTrue(result.isSuccess());
        assertEquals("my_tool", result.getToolName());
        assertEquals("done", result.getResult());
        assertEquals(1_000_000L, result.getElapsedNanos());
        assertNull(result.getErrorMessage());
    }

    @Test
    void shouldCreateFailureResult() {
        ToolExecutionContext ctx = ToolExecutionContext.builder()
            .sessionId("s1")
            .traceId("t1")
            .build();

        Exception error = new RuntimeException("connection refused");
        ToolExecutionResult result = ToolExecutionResult.failure(
            "my_tool", error, ctx);

        assertFalse(result.isSuccess());
        assertEquals("my_tool", result.getToolName());
        assertEquals("connection refused", result.getErrorMessage());
        assertNull(result.getResult());
    }

    @Test
    void shouldHandleNullResult() {
        ToolExecutionContext ctx = ToolExecutionContext.builder()
            .sessionId("s1")
            .traceId("t1")
            .build();

        ToolExecutionResult result = ToolExecutionResult.success(
            "my_tool", null, 0L, ctx);

        assertTrue(result.isSuccess());
        assertNull(result.getResult());
    }
}
