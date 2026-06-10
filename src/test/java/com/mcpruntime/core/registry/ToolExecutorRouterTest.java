package com.mcpruntime.core.registry;

import com.mcpruntime.core.registry.exception.ToolExecutionException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ToolExecutorRouterTest {

    private final ToolExecutorRouter router = new ToolExecutorRouter();

    @Test
    void shouldExecuteToolSuccessfully() {
        ToolDefinition def = ToolDefinition.builder()
            .name("echo")
            .executor(ctx -> ctx.getArg("msg"))
            .build();

        ToolExecutionContext ctx = ToolExecutionContext.builder()
            .sessionId("s1").traceId("t1")
            .args(java.util.Map.of("msg", "hello"))
            .build();

        ToolExecutionResult result = router.execute(def, ctx);

        assertTrue(result.isSuccess());
        assertEquals("hello", result.getResult());
        assertTrue(result.getElapsedNanos() > 0);
    }

    @Test
    void shouldReturnFailureOnExecutorException() {
        ToolDefinition def = ToolDefinition.builder()
            .name("failing")
            .executor(ctx -> { throw new RuntimeException("oops"); })
            .build();

        ToolExecutionContext ctx = ToolExecutionContext.builder()
            .sessionId("s1").traceId("t1")
            .build();

        ToolExecutionResult result = router.execute(def, ctx);

        assertFalse(result.isSuccess());
        assertEquals("oops", result.getErrorMessage());
    }

    @Test
    void shouldExecuteInterceptorsInOrder() {
        List<String> order = new ArrayList<>();

        ToolDefinition def = ToolDefinition.builder()
            .name("intercepted")
            .executor(ctx -> {
                order.add("exec");
                return "ok";
            })
            .withInterceptor(new ToolInterceptor() {
                @Override
                public void beforeExecute(ToolExecutionContext ctx) {
                    order.add("before");
                }
                @Override
                public void afterExecute(ToolExecutionContext ctx, ToolExecutionResult result) {
                    order.add("after");
                }
            })
            .build();

        ToolExecutionContext ctx = ToolExecutionContext.builder()
            .sessionId("s1").traceId("t1")
            .build();

        router.execute(def, ctx);

        assertEquals(List.of("before", "exec", "after"), order);
    }

    @Test
    void shouldExecuteErrorInterceptorOnFailure() {
        List<String> order = new ArrayList<>();

        ToolDefinition def = ToolDefinition.builder()
            .name("failing")
            .executor(ctx -> { throw new RuntimeException("fail"); })
            .withInterceptor(new ToolInterceptor() {
                @Override
                public void onError(ToolExecutionContext ctx, Exception e) {
                    order.add("error: " + e.getMessage());
                }
            })
            .build();

        ToolExecutionContext ctx = ToolExecutionContext.builder()
            .sessionId("s1").traceId("t1")
            .build();

        router.execute(def, ctx);

        assertEquals(List.of("error: fail"), order);
    }

    @Test
    void shouldRecordElapsedTime() {
        ToolDefinition def = ToolDefinition.builder()
            .name("slow")
            .executor(ctx -> {
                Thread.sleep(50);
                return "done";
            })
            .build();

        ToolExecutionContext ctx = ToolExecutionContext.builder()
            .sessionId("s1").traceId("t1")
            .build();

        ToolExecutionResult result = router.execute(def, ctx);

        assertTrue(result.isSuccess());
        assertTrue(result.getElapsedNanos() >= 50_000_000,
            "elapsed should be at least 50ms");
    }
}
