package com.mcpruntime.agent.event;

import com.mcpruntime.core.registry.ToolExecutionResult;
import org.springframework.context.ApplicationEvent;

/**
 * Event published when a {@link WorkerAgent} finishes executing an async task.
 * {@link com.mcpruntime.agent.orchestrator.OrchestratorAgent} consumes this to
 * notify the user session that results are available.
 */
public class TaskCompletedEvent extends ApplicationEvent {

    private final String taskId;
    private final String sessionId;
    private final String traceId;
    private final ToolExecutionResult result;

    public TaskCompletedEvent(Object source, String taskId, String sessionId,
                              String traceId, ToolExecutionResult result) {
        super(source);
        this.taskId = taskId;
        this.sessionId = sessionId;
        this.traceId = traceId;
        this.result = result;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getTraceId() {
        return traceId;
    }

    public ToolExecutionResult getResult() {
        return result;
    }
}
