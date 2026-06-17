package com.mcpruntime.agent.worker;

import com.mcpruntime.core.registry.ToolExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tracks all async tasks by session. Used by {@code OrchestratorAgent}
 * to check whether background work has completed for a given session
 * and to drain results into the LLM turn context.
 * <p>
 * Thread-safe. Designed for many-reads-few-writes workload:
 * completed results are typically drained once per LLM turn.
 */
@Component
public class AsyncTaskRegistry {

    private static final Logger log = LoggerFactory.getLogger(AsyncTaskRegistry.class);

    public enum TaskStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }

    public record TaskEntry(
        String taskId,
        String sessionId,
        String traceId,
        String toolName,
        TaskStatus status,
        ToolExecutionResult result
    ) {}

    // sessionId -> list of task entries
    private final Map<String, List<TaskEntry>> tasksBySession = new ConcurrentHashMap<>();

    /**
     * Register a new async task (PENDING state).
     */
    public void register(String taskId, String sessionId, String traceId, String toolName) {
        List<TaskEntry> entries = tasksBySession.computeIfAbsent(sessionId,
            k -> new CopyOnWriteArrayList<>());
        entries.add(new TaskEntry(taskId, sessionId, traceId, toolName, TaskStatus.PENDING, null));
        log.debug("Async task registered: {} (tool={}, session={})", taskId, toolName, sessionId);
    }

    /**
     * Mark a task as in-progress.
     */
    public void markInProgress(String taskId) {
        updateStatus(taskId, TaskStatus.IN_PROGRESS);
    }

    /**
     * Mark a task as completed and attach its result.
     */
    public void complete(String taskId, ToolExecutionResult result) {
        TaskEntry updated = updateEntry(taskId, entry ->
            new TaskEntry(entry.taskId, entry.sessionId, entry.traceId, entry.toolName,
                result.isSuccess() ? TaskStatus.COMPLETED : TaskStatus.FAILED,
                result));
        if (updated != null) {
            log.info("Async task completed: {} (success={})", taskId, result.isSuccess());
        }
    }

    /**
     * Drain all completed results for a session. After this call,
     * completed tasks are removed from the registry for this session.
     * PENDING and IN_PROGRESS tasks remain.
     */
    public List<ToolExecutionResult> drainCompleted(String sessionId) {
        List<TaskEntry> entries = tasksBySession.get(sessionId);
        if (entries == null) {
            return List.of();
        }

        List<ToolExecutionResult> completed = new ArrayList<>();
        List<TaskEntry> remaining = new ArrayList<>();

        for (TaskEntry entry : entries) {
            if (entry.status == TaskStatus.COMPLETED || entry.status == TaskStatus.FAILED) {
                if (entry.result != null) {
                    completed.add(entry.result);
                }
            } else {
                remaining.add(entry);
            }
        }

        if (remaining.isEmpty()) {
            tasksBySession.remove(sessionId);
        } else {
            tasksBySession.put(sessionId, List.copyOf(remaining));
        }

        return completed;
    }

    /**
     * Check if a session has any pending or in-progress tasks.
     */
    public boolean hasPendingWork(String sessionId) {
        List<TaskEntry> entries = tasksBySession.get(sessionId);
        if (entries == null) return false;
        return entries.stream().anyMatch(e ->
            e.status == TaskStatus.PENDING || e.status == TaskStatus.IN_PROGRESS);
    }

    /**
     * Get a summary of all active tasks for a session (for LLM context injection).
     */
    public List<TaskEntry> getActiveTasks(String sessionId) {
        List<TaskEntry> entries = tasksBySession.get(sessionId);
        if (entries == null) return List.of();
        return entries.stream()
            .filter(e -> e.status == TaskStatus.PENDING || e.status == TaskStatus.IN_PROGRESS)
            .toList();
    }

    // ---- internal helpers ----

    private void updateStatus(String taskId, TaskStatus newStatus) {
        updateEntry(taskId, entry ->
            new TaskEntry(entry.taskId, entry.sessionId, entry.traceId, entry.toolName,
                newStatus, entry.result));
    }

    private TaskEntry updateEntry(String taskId,
                                  java.util.function.Function<TaskEntry, TaskEntry> updater) {
        for (List<TaskEntry> entries : tasksBySession.values()) {
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).taskId.equals(taskId)) {
                    TaskEntry updated = updater.apply(entries.get(i));
                    entries.set(i, updated);
                    return updated;
                }
            }
        }
        return null;
    }

    // visible for testing
    Map<String, List<TaskEntry>> getTasksBySession() {
        return tasksBySession;
    }
}
