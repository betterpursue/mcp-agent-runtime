package com.mcpruntime.core.registry;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolExecutionContextTest {

    @Test
    void shouldCreateWithAllFields() {
        Map<String, Object> args = Map.of("city", "北京", "unit", "celsius");
        Map<String, Object> ctxMap = Map.of("extra", "value");

        ToolExecutionContext ctx = ToolExecutionContext.builder()
            .sessionId("session-1")
            .traceId("trace-1")
            .callId("call-1")
            .args(args)
            .context(ctxMap)
            .build();

        assertEquals("session-1", ctx.getSessionId());
        assertEquals("trace-1", ctx.getTraceId());
        assertEquals("call-1", ctx.getCallId());
        assertEquals("北京", ctx.getArg("city"));
        assertEquals("celsius", ctx.getArg("unit"));
        assertEquals("北京", ctx.getArg("city"));
        assertEquals("value", ctx.getContext().get("extra"));
    }

    @Test
    void shouldReturnDefaultForMissingArg() {
        ToolExecutionContext ctx = ToolExecutionContext.builder()
            .sessionId("s1")
            .traceId("t1")
            .build();

        assertEquals("default", ctx.getArgOrDefault("missing", "default"));
    }

    @Test
    void shouldReturnNullForMissingArgWithoutDefault() {
        ToolExecutionContext ctx = ToolExecutionContext.builder()
            .sessionId("s1")
            .traceId("t1")
            .build();

        assertNull(ctx.getArg("missing"));
    }

    @Test
    void shouldHandleEmptyArgs() {
        ToolExecutionContext ctx = ToolExecutionContext.builder()
            .sessionId("s1")
            .traceId("t1")
            .build();

        assertTrue(ctx.getArgs().isEmpty());
    }

    @Test
    void shouldHandleEmptyContext() {
        ToolExecutionContext ctx = ToolExecutionContext.builder()
            .sessionId("s1")
            .traceId("t1")
            .build();

        assertTrue(ctx.getContext().isEmpty());
    }

    @Test
    void argsShouldBeImmutable() {
        ToolExecutionContext ctx = ToolExecutionContext.builder()
            .sessionId("s1")
            .traceId("t1")
            .args(Map.of("k", "v"))
            .build();

        assertThrows(UnsupportedOperationException.class,
            () -> ctx.getArgs().put("k2", "v2"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldHandleTypedArgRetrieval() {
        Map<String, Object> args = Map.of("count", 42, "ratio", 3.14);
        ToolExecutionContext ctx = ToolExecutionContext.builder()
            .sessionId("s1")
            .traceId("t1")
            .args(args)
            .build();

        int count = ctx.getArg("count");
        double ratio = ctx.getArg("ratio");

        assertEquals(42, count);
        assertEquals(3.14, ratio, 0.001);
    }
}
