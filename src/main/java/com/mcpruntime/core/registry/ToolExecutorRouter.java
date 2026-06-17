package com.mcpruntime.core.registry;

import com.mcpruntime.agent.event.AsyncTaskEvent;
import com.mcpruntime.agent.worker.AsyncTaskRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ToolExecutorRouter {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutorRouter.class);

    private final ApplicationEventPublisher eventPublisher;
    private final AsyncTaskRegistry taskRegistry;

    public ToolExecutorRouter(ApplicationEventPublisher eventPublisher,
                              AsyncTaskRegistry taskRegistry) {
        this.eventPublisher = eventPublisher;
        this.taskRegistry = taskRegistry;
    }

    public ToolExecutionResult execute(ToolDefinition def, ToolExecutionContext ctx) {
        // ---- Async (long-running) path ----
        if (def.getMetadata().isLongRunning()) {
            String taskId = UUID.randomUUID().toString().replace("-", "");
            taskRegistry.register(taskId, ctx.getSessionId(), ctx.getTraceId(), def.getName());

            eventPublisher.publishEvent(new AsyncTaskEvent(
                this, taskId, ctx.getSessionId(), ctx.getTraceId(),
                def, ctx.getArgs(), ctx.getContext()));

            log.info("Dispatched long-running tool '{}' as async task {}", def.getName(), taskId);
            return ToolExecutionResult.asyncSubmitted(def.getName(), taskId, ctx);
        }

        // ---- Sync (standard) path ----
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
