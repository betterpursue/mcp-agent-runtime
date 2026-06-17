package com.mcpruntime.agent.event;

import com.mcpruntime.core.registry.ToolDefinition;
import com.mcpruntime.core.registry.ToolExecutionContext;
import org.springframework.context.ApplicationEvent;

import java.util.Map;

/**
 * Event published when the runtime decides a Tool should execute
 * asynchronously (long-running path). {@link com.mcpruntime.agent.worker.WorkerAgent}
 * picks this up and performs the actual execution in a background thread pool.
 */
public class AsyncTaskEvent extends ApplicationEvent {

    private final String taskId;
    private final String sessionId;
    private final String traceId;
    private final ToolDefinition toolDefinition;
    private final Map<String, Object> args;
    private final Map<String, Object> context;

    public AsyncTaskEvent(Object source, String taskId, String sessionId,
                          String traceId, ToolDefinition toolDefinition,
                          Map<String, Object> args, Map<String, Object> context) {
        super(source);
        this.taskId = taskId;
        this.sessionId = sessionId;
        this.traceId = traceId;
        this.toolDefinition = toolDefinition;
        this.args = args;
        this.context = context;
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

    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    public Map<String, Object> getArgs() {
        return args;
    }

    public Map<String, Object> getContext() {
        return context;
    }
}
