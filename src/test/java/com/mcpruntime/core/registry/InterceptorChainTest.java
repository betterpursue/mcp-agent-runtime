package com.mcpruntime.core.registry;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class InterceptorChainTest {

    @Test
    void shouldExecuteAllBeforeInterceptors() {
        List<String> order = new ArrayList<>();

        InterceptorChain chain = new InterceptorChain(List.of(
            new TestInterceptor("A", order),
            new TestInterceptor("B", order),
            new TestInterceptor("C", order)
        ));

        ToolExecutionContext ctx = ToolExecutionContext.builder()
            .sessionId("s1").traceId("t1").build();

        chain.beforeExecute(ctx);

        assertEquals(List.of("A.before", "B.before", "C.before"), order);
    }

    @Test
    void shouldExecuteAfterInReverseOrder() {
        List<String> order = new ArrayList<>();

        InterceptorChain chain = new InterceptorChain(List.of(
            new TestInterceptor("A", order),
            new TestInterceptor("B", order),
            new TestInterceptor("C", order)
        ));

        ToolExecutionContext ctx = ToolExecutionContext.builder()
            .sessionId("s1").traceId("t1").build();
        ToolExecutionResult result = ToolExecutionResult.success(
            "test", "ok", 0L, ctx);

        chain.afterExecute(ctx, result);

        assertEquals(List.of("C.after", "B.after", "A.after"), order);
    }

    @Test
    void shouldExecuteErrorInReverseOrder() {
        List<String> order = new ArrayList<>();

        InterceptorChain chain = new InterceptorChain(List.of(
            new TestInterceptor("A", order),
            new TestInterceptor("B", order),
            new TestInterceptor("C", order)
        ));

        ToolExecutionContext ctx = ToolExecutionContext.builder()
            .sessionId("s1").traceId("t1").build();

        chain.onError(ctx, new RuntimeException("fail"));

        assertEquals(List.of("C.error", "B.error", "A.error"), order);
    }

    @Test
    void shouldHandleEmptyInterceptorList() {
        InterceptorChain chain = new InterceptorChain(List.of());

        ToolExecutionContext ctx = ToolExecutionContext.builder()
            .sessionId("s1").traceId("t1").build();
        ToolExecutionResult result = ToolExecutionResult.success(
            "test", "ok", 0L, ctx);

        assertDoesNotThrow(() -> chain.beforeExecute(ctx));
        assertDoesNotThrow(() -> chain.afterExecute(ctx, result));
        assertDoesNotThrow(() -> chain.onError(ctx, new RuntimeException()));
    }

    @Test
    void shouldPropagateInterceptorException() {
        InterceptorChain chain = new InterceptorChain(List.of(
            new ToolInterceptor() {
                @Override
                public void beforeExecute(ToolExecutionContext ctx) {
                    throw new RuntimeException("interceptor fail");
                }
            }
        ));

        ToolExecutionContext ctx = ToolExecutionContext.builder()
            .sessionId("s1").traceId("t1").build();

        assertThrows(RuntimeException.class,
            () -> chain.beforeExecute(ctx));
    }

    private static class TestInterceptor implements ToolInterceptor {
        private final String name;
        private final List<String> order;

        TestInterceptor(String name, List<String> order) {
            this.name = name;
            this.order = order;
        }

        @Override
        public void beforeExecute(ToolExecutionContext ctx) {
            order.add(name + ".before");
        }

        @Override
        public void afterExecute(ToolExecutionContext ctx, ToolExecutionResult result) {
            order.add(name + ".after");
        }

        @Override
        public void onError(ToolExecutionContext ctx, Exception e) {
            order.add(name + ".error");
        }
    }
}
