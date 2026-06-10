package com.mcpruntime.core.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ToolExecutorRouter {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutorRouter.class);

    public ToolExecutionResult execute(ToolDefinition def, ToolExecutionContext ctx) {
        InterceptorChain chain = new InterceptorChain(def.getInterceptors());

        try {
            chain.beforeExecute(ctx);

            long start = System.nanoTime();
            Object result = def.getExecutor().execute(ctx);
            long elapsed = System.nanoTime() - start;

            ToolExecutionResult executionResult = ToolExecutionResult.success(
                def.getName(), result, elapsed, ctx);

            chain.afterExecute(ctx, executionResult);
            return executionResult;

        } catch (Exception e) {
            chain.onError(ctx, e);
            return ToolExecutionResult.failure(def.getName(), e, ctx);
        }
    }
}
