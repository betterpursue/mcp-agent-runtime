package com.mcpruntime.agent.orchestrator;

import com.mcpruntime.agent.event.TaskCompletedEvent;
import com.mcpruntime.agent.worker.AsyncTaskRegistry;
import com.mcpruntime.core.registry.ToolExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Coordinates the user-facing side of an Agent session. In the
 * Orchestrator-Worker decomposition, this is the "front" half:
 * <ul>
 *   <li>Listens for {@link TaskCompletedEvent} to know when background
 *       work has finished for a session.</li>
 *   <li>Provides a query API to drain completed task results so they
 *       can be injected into the LLM's turn context.</li>
 *   <li>Provides a summary of in-flight tasks so the orchestrator can
 *       inform the LLM about ongoing background work.</li>
 * </ul>
 * <p>
 * The actual LLM turn loop (user input → LLM → tool calls → output)
 * is expected to be implemented by a higher-level component that
 * calls the methods here between turns.
 */
@Component
public class OrchestratorAgent {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorAgent.class);

    private final AsyncTaskRegistry taskRegistry;

    public OrchestratorAgent(AsyncTaskRegistry taskRegistry) {
        this.taskRegistry = taskRegistry;
    }

    /**
     * Called when a background worker finishes a task. Logs the event;
     * the result will be picked up on the next LLM turn via
     * {@link #drainCompletedResults(String)}.
     */
    @EventListener
    public void onTaskCompleted(TaskCompletedEvent event) {
        ToolExecutionResult result = event.getResult();
        log.info("Orchestrator notified: task {} completed for session {} (success={})",
            event.getTaskId(), event.getSessionId(), result.isSuccess());
    }

    /**
     * Drain all completed/failed results for a session. Call this
     * at the start of each LLM turn to inject background results
     * into the conversation context.
     */
    public List<ToolExecutionResult> drainCompletedResults(String sessionId) {
        return taskRegistry.drainCompleted(sessionId);
    }

    /**
     * Check whether the session has pending background work.
     * If true, the orchestrator should inform the LLM about it.
     */
    public boolean hasBackgroundWork(String sessionId) {
        return taskRegistry.hasPendingWork(sessionId);
    }

    /**
     * Get a human-readable summary of active background tasks,
     * suitable for injecting into the LLM system prompt.
     */
    public String buildBackgroundSummary(String sessionId) {
        List<AsyncTaskRegistry.TaskEntry> active = taskRegistry.getActiveTasks(sessionId);
        if (active.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[Background Tasks]\n");
        for (AsyncTaskRegistry.TaskEntry entry : active) {
            sb.append("- Tool '").append(entry.toolName())
                .append("' is ").append(entry.status().name().toLowerCase())
                .append(" (taskId: ").append(entry.taskId()).append(")\n");
        }
        return sb.toString();
    }
}
