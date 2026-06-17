package com.mcpruntime.agent.worker;

import com.mcpruntime.agent.event.AsyncTaskEvent;
import com.mcpruntime.agent.event.TaskCompletedEvent;
import com.mcpruntime.core.registry.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Consumes {@link AsyncTaskEvent} and executes the associated Tool
 * on a dedicated background thread pool. Results are published as
 * {@link TaskCompletedEvent} for the {@code OrchestratorAgent} (or any
 * other listener) to consume.
 * <p>
 * This is the "Worker" half of the Orchestrator-Worker decomposition.
 * It has no direct user interaction — it just runs tools and reports back.
 */
@Component
public class WorkerAgent {

    private static final Logger log = LoggerFactory.getLogger(WorkerAgent.class);

    private final ToolExecutorRouter executorRouter;
    private final AsyncTaskRegistry taskRegistry;
    private final ApplicationEventPublisher eventPublisher;

    public WorkerAgent(ToolExecutorRouter executorRouter,
                       AsyncTaskRegistry taskRegistry,
                       ApplicationEventPublisher eventPublisher) {
        this.executorRouter = executorRouter;
        this.taskRegistry = taskRegistry;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Handle an async tool execution request. Runs on the "toolTaskExecutor"
     * thread pool (see {@link com.mcpruntime.core.config.AsyncConfig}).
     */
    @EventListener
    @Async("toolTaskExecutor")
    public void handleAsyncTask(AsyncTaskEvent event) {
        String taskId = event.getTaskId();
        log.info("Worker picked up async task: {} (tool={})", taskId, event.getToolDefinition().getName());

        taskRegistry.markInProgress(taskId);

        try {
            ToolDefinition def = event.getToolDefinition();
            ToolExecutionContext ctx = ToolExecutionContext.builder()
                .sessionId(event.getSessionId())
                .traceId(event.getTraceId())
                .callId(taskId)
                .args(event.getArgs())
                .context(event.getContext())
                .build();

            ToolExecutionResult result = executorRouter.execute(def, ctx);

            taskRegistry.complete(taskId, result);

            eventPublisher.publishEvent(new TaskCompletedEvent(
                this, taskId, event.getSessionId(), event.getTraceId(), result));

            log.info("Worker completed async task: {} (success={})", taskId, result.isSuccess());

        } catch (Exception e) {
            log.error("Worker failed to execute async task: {}", taskId, e);
            ToolExecutionResult failed = ToolExecutionResult.failure(
                event.getToolDefinition().getName(), e,
                ToolExecutionContext.builder()
                    .sessionId(event.getSessionId())
                    .traceId(event.getTraceId())
                    .callId(taskId)
                    .build());
            taskRegistry.complete(taskId, failed);
            eventPublisher.publishEvent(new TaskCompletedEvent(
                this, taskId, event.getSessionId(), event.getTraceId(), failed));
        }
    }
}
