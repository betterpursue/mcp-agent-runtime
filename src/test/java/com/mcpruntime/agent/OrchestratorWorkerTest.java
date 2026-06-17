package com.mcpruntime.agent;

import com.mcpruntime.agent.event.AsyncTaskEvent;
import com.mcpruntime.agent.event.TaskCompletedEvent;
import com.mcpruntime.agent.orchestrator.OrchestratorAgent;
import com.mcpruntime.agent.worker.AsyncTaskRegistry;
import com.mcpruntime.core.registry.*;
import com.mcpruntime.core.schema.JsonSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Orchestrator-Worker async decomposition.
 */
class OrchestratorWorkerTest {

    private AsyncTaskRegistry taskRegistry;
    private OrchestratorAgent orchestrator;
    private TestEventPublisher eventPublisher;
    private ToolExecutorRouter router;

    private final String sessionId = "test-session-1";
    private final String traceId = "test-trace-1";

    @BeforeEach
    void setUp() {
        taskRegistry = new AsyncTaskRegistry();
        eventPublisher = new TestEventPublisher();
        router = new ToolExecutorRouter(eventPublisher, taskRegistry);
        orchestrator = new OrchestratorAgent(taskRegistry);
    }

    // ========================================================================
    // ToolMetadata
    // ========================================================================

    @Test
    void toolMetadataDefaultIsNotLongRunning() {
        ToolMetadata meta = ToolMetadata.builder().build();
        assertFalse(meta.isLongRunning());
    }

    @Test
    void toolMetadataCanBeMarkedLongRunning() {
        ToolMetadata meta = ToolMetadata.builder().longRunning(true).build();
        assertTrue(meta.isLongRunning());
    }

    // ========================================================================
    // ToolExecutorRouter — async dispatch
    // ========================================================================

    @Test
    void syncToolExecutesDirectly() {
        ToolDefinition tool = syncTool("greet", ctx -> "hello");

        ToolExecutionContext ctx = ctx();
        ToolExecutionResult result = router.execute(tool, ctx);

        assertTrue(result.isSuccess());
        assertEquals("hello", result.getResult());
        assertFalse(eventPublisher.lastEvent instanceof AsyncTaskEvent);
    }

    @Test
    void longRunningToolReturnsImmediatelyAndDispatchesEvent() {
        ToolDefinition tool = longRunningTool("web_scrape", ctx -> {
            Thread.sleep(500);
            return "done";
        });

        ToolExecutionContext ctx = ctx();
        ToolExecutionResult result = router.execute(tool, ctx);

        // Should return immediately with a placeholder
        assertTrue(result.isSuccess());
        assertTrue(result.getResult().toString().contains("[Task submitted:"));

        // Should have published an AsyncTaskEvent
        assertNotNull(eventPublisher.lastEvent);
        assertInstanceOf(AsyncTaskEvent.class, eventPublisher.lastEvent);

        AsyncTaskEvent event = (AsyncTaskEvent) eventPublisher.lastEvent;
        assertEquals("web_scrape", event.getToolDefinition().getName());
        assertEquals(sessionId, event.getSessionId());
        assertEquals(traceId, event.getTraceId());

        // Should be registered in the task registry
        assertTrue(taskRegistry.hasPendingWork(sessionId));
    }

    @Test
    void longRunningToolTaskIdIsUniquePerCall() {
        ToolDefinition tool = longRunningTool("web_scrape", ctx -> "done");

        ToolExecutionContext ctx = ctx();
        ToolExecutionResult r1 = router.execute(tool, ctx);
        ToolExecutionResult r2 = router.execute(tool, ctx);

        String taskId1 = extractTaskId(r1);
        String taskId2 = extractTaskId(r2);

        assertNotNull(taskId1);
        assertNotNull(taskId2);
        assertNotEquals(taskId1, taskId2);
    }

    // ========================================================================
    // AsyncTaskRegistry
    // ========================================================================

    @Test
    void registryTracksTaskLifecycle() {
        taskRegistry.register("task-1", sessionId, traceId, "web_scrape");
        assertTrue(taskRegistry.hasPendingWork(sessionId));

        taskRegistry.markInProgress("task-1");
        assertEquals(1, taskRegistry.getActiveTasks(sessionId).size());
        assertEquals(AsyncTaskRegistry.TaskStatus.IN_PROGRESS,
            taskRegistry.getActiveTasks(sessionId).get(0).status());

        ToolExecutionResult result = ToolExecutionResult.success(
            "web_scrape", "page content", 1000, ctx());
        taskRegistry.complete("task-1", result);

        assertFalse(taskRegistry.hasPendingWork(sessionId));

        // Drain
        List<ToolExecutionResult> completed = taskRegistry.drainCompleted(sessionId);
        assertEquals(1, completed.size());
        assertEquals("page content", completed.get(0).getResult());

        // Second drain should be empty
        assertTrue(taskRegistry.drainCompleted(sessionId).isEmpty());
    }

    @Test
    void registryHandlesMultipleTasksPerSession() {
        taskRegistry.register("task-1", sessionId, traceId, "tool_a");
        taskRegistry.register("task-2", sessionId, traceId, "tool_b");

        taskRegistry.complete("task-1", ToolExecutionResult.success("tool_a", "result_a", 100, ctx()));
        // task-2 is still pending

        assertTrue(taskRegistry.hasPendingWork(sessionId));

        List<ToolExecutionResult> completed = taskRegistry.drainCompleted(sessionId);
        assertEquals(1, completed.size());
        assertEquals("result_a", completed.get(0).getResult());

        // task-2 remains
        assertTrue(taskRegistry.hasPendingWork(sessionId));
    }

    @Test
    void registryDrainRemovesOnlyCompleted() {
        taskRegistry.register("task-1", sessionId, traceId, "tool_a");
        taskRegistry.markInProgress("task-1");

        // Drain should not remove in-progress tasks
        assertTrue(taskRegistry.drainCompleted(sessionId).isEmpty());
        assertTrue(taskRegistry.hasPendingWork(sessionId));
    }

    // ========================================================================
    // OrchestratorAgent
    // ========================================================================

    @Test
    void orchestratorDrainReturnsCompletedResults() {
        taskRegistry.register("t1", sessionId, traceId, "tool_x");
        taskRegistry.complete("t1", ToolExecutionResult.success("tool_x", "data", 500, ctx()));

        List<ToolExecutionResult> results = orchestrator.drainCompletedResults(sessionId);
        assertEquals(1, results.size());
        assertEquals("data", results.get(0).getResult());
    }

    @Test
    void orchestratorBackgroundSummaryReturnsEmptyWhenNoWork() {
        assertEquals("", orchestrator.buildBackgroundSummary(sessionId));
    }

    @Test
    void orchestratorBackgroundSummaryIncludesActiveTasks() {
        taskRegistry.register("t1", sessionId, traceId, "web_scrape");
        taskRegistry.markInProgress("t1");

        String summary = orchestrator.buildBackgroundSummary(sessionId);
        assertTrue(summary.contains("web_scrape"));
        assertTrue(summary.contains("in_progress"));
        assertTrue(summary.contains("t1"));
    }

    @Test
    void orchestratorDetectsBackgroundWork() {
        assertFalse(orchestrator.hasBackgroundWork(sessionId));

        taskRegistry.register("t1", sessionId, traceId, "tool_y");
        assertTrue(orchestrator.hasBackgroundWork(sessionId));
    }

    // ========================================================================
    // Integration: Full async flow
    // ========================================================================

    @Test
    void fullAsyncFlow() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<TaskCompletedEvent> capturedEvent = new AtomicReference<>();

        // Create a worker that captures the completion event
        // We'll use a simple approach: manually trigger the flow
        ToolDefinition tool = longRunningTool("slow_tool", ctx -> {
            Thread.sleep(100);
            return "slow result";
        });

        ToolExecutionContext ctx = ctx();
        ToolExecutionResult initial = router.execute(tool, ctx);

        // Verify immediate response
        assertTrue(initial.getResult().toString().contains("[Task submitted:"));

        // The AsyncTaskEvent was published. In a real system, WorkerAgent
        // would pick it up. Let's simulate the worker:
        AsyncTaskEvent event = (AsyncTaskEvent) eventPublisher.lastEvent;
        assertNotNull(event);

        // Manually execute the tool directly (worker behavior — bypass router to avoid re-dispatch)
        taskRegistry.markInProgress(event.getTaskId());
        Object output;
        try {
            output = tool.getExecutor().execute(ToolExecutionContext.builder()
                .sessionId(event.getSessionId())
                .traceId(event.getTraceId())
                .callId(event.getTaskId())
                .args(event.getArgs())
                .build());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        ToolExecutionResult workerResult = ToolExecutionResult.success(
            tool.getName(), output, 500,
            ToolExecutionContext.builder()
                .sessionId(event.getSessionId())
                .traceId(event.getTraceId())
                .callId(event.getTaskId())
                .build());

        // Simulate worker posting completion
        assertTrue(workerResult.isSuccess());
        assertEquals("slow result", workerResult.getResult());

        taskRegistry.complete(event.getTaskId(), workerResult);

        // Orchestrator drains
        List<ToolExecutionResult> drained = orchestrator.drainCompletedResults(sessionId);
        assertEquals(1, drained.size());
        assertEquals("slow result", drained.get(0).getResult());
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private ToolDefinition syncTool(String name, ToolExecutor executor) {
        return ToolDefinition.builder()
            .name(name)
            .description("sync tool")
            .executor(executor)
            .build();
    }

    private ToolDefinition longRunningTool(String name, ToolExecutor executor) {
        return ToolDefinition.builder()
            .name(name)
            .description("long-running tool")
            .executor(executor)
            .metadata(ToolMetadata.builder()
                .timeout(Duration.ofSeconds(30))
                .longRunning(true)
                .build())
            .build();
    }

    private ToolExecutionContext ctx() {
        return ToolExecutionContext.builder()
            .sessionId(sessionId)
            .traceId(traceId)
            .callId("test-call")
            .args(Map.of("url", "https://example.com"))
            .build();
    }

    private String extractTaskId(ToolExecutionResult result) {
        String msg = result.getResult().toString();
        int start = msg.indexOf("[Task submitted: ") + "[Task submitted: ".length();
        int end = msg.indexOf(". The result will be available shortly.]");
        if (start < 0 || end < 0) return null;
        return msg.substring(start, end);
    }

    /**
     * A simple ApplicationEventPublisher that captures the last published event.
     */
    static class TestEventPublisher implements ApplicationEventPublisher {
        Object lastEvent;

        @Override
        public void publishEvent(Object event) {
            this.lastEvent = event;
        }

        @Override
        public void publishEvent(org.springframework.context.ApplicationEvent event) {
            this.lastEvent = event;
        }
    }
}
