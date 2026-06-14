package com.mcpruntime.core.state;

import com.mcpruntime.core.registry.ToolExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for {@link StateManager}.
 * <p>
 * Covers: session lifecycle, turn recording, delta-only retrieval,
 * tool result caching, cache invalidation, expiry, concurrent access.
 */
class StateManagerTest {

    private static final long TTL = 60_000L; // 1 minute TTL for tests

    private StateManager stateManager;

    @BeforeEach
    void setUp() {
        stateManager = new StateManager(TTL);
    }

    // ========================================================================
    // Session Lifecycle
    // ========================================================================

    @Test
    void shouldCreateNewSession() {
        StateManager.SessionState session = stateManager.getOrCreate("session-1");
        assertNotNull(session);
        assertEquals("session-1", session.getSessionId());
        assertNotNull(session.getCreatedAt());
        assertNotNull(session.getLastAccessedAt());
    }

    @Test
    void shouldReturnExistingSession() {
        StateManager.SessionState first = stateManager.getOrCreate("session-1");
        StateManager.SessionState second = stateManager.getOrCreate("session-1");
        assertSame(first, second);
    }

    @Test
    void shouldGetExistingSession() {
        stateManager.getOrCreate("session-1");
        Optional<StateManager.SessionState> result = stateManager.get("session-1");
        assertTrue(result.isPresent());
    }

    @Test
    void shouldReturnEmptyForUnknownSession() {
        Optional<StateManager.SessionState> result = stateManager.get("nonexistent");
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldRemoveSession() {
        stateManager.getOrCreate("session-1");
        stateManager.remove("session-1");
        assertTrue(stateManager.get("session-1").isEmpty());
    }

    @Test
    void shouldReturnActiveSessionCount() {
        assertEquals(0, stateManager.activeSessionCount());
        stateManager.getOrCreate("s1");
        stateManager.getOrCreate("s2");
        assertEquals(2, stateManager.activeSessionCount());
    }

    @Test
    void shouldNotCountExpiredSessions() {
        StateManager shortTtl = new StateManager(1); // 1ms TTL
        shortTtl.getOrCreate("s1");
        shortTtl.getOrCreate("s2");
        // After TTL expires, sessions should be evicted
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertEquals(0, shortTtl.activeSessionCount());
    }

    // ========================================================================
    // Conversation History
    // ========================================================================

    @Test
    void shouldRecordTurn() {
        StateManager.ConversationTurn turn = new StateManager.ConversationTurn(
                1, "Hello", "Hi there!", List.of());
        stateManager.recordTurn("session-1", turn);

        assertEquals(1, stateManager.turnCount("session-1"));
    }

    @Test
    void shouldRetrieveRecentTurns() {
        for (int i = 1; i <= 5; i++) {
            StateManager.ConversationTurn turn = new StateManager.ConversationTurn(
                    i, "msg-" + i, "resp-" + i, List.of());
            stateManager.recordTurn("session-1", turn);
        }

        List<StateManager.ConversationTurn> recent = stateManager
                .getRecentTurns("session-1", 3);
        assertEquals(3, recent.size());
        assertEquals(3, recent.get(0).getTurnNumber());
        assertEquals(5, recent.get(2).getTurnNumber());
    }

    @Test
    void shouldReturnAllTurnsWhenLimitExceedsCount() {
        for (int i = 1; i <= 3; i++) {
            stateManager.recordTurn("session-1",
                    new StateManager.ConversationTurn(i, "q-" + i, "a-" + i, List.of()));
        }

        List<StateManager.ConversationTurn> all = stateManager
                .getRecentTurns("session-1", 100);
        assertEquals(3, all.size());
    }

    @Test
    void shouldReturnEmptyTurnsForUnknownSession() {
        List<StateManager.ConversationTurn> turns = stateManager
                .getRecentTurns("nonexistent", 10);
        assertTrue(turns.isEmpty());
    }

    @Test
    void shouldRecordTurnWithToolCalls() {
        StateManager.ConversationTurn turn = new StateManager.ConversationTurn(
                1, "What's the weather?", "Let me check...",
                List.of("get_weather", "get_temperature"));

        stateManager.recordTurn("session-1", turn);

        List<StateManager.ConversationTurn> turns = stateManager
                .getRecentTurns("session-1", 1);
        assertEquals(1, turns.size());
        assertEquals(2, turns.get(0).getToolCalls().size());
        assertTrue(turns.get(0).getToolCalls().contains("get_weather"));
    }

    @Test
    void shouldFindTurnsByToolName() {
        stateManager.recordTurn("session-1",
                new StateManager.ConversationTurn(1, "Hello", "Hi", List.of()));
        stateManager.recordTurn("session-1",
                new StateManager.ConversationTurn(2, "Weather?",
                        "Checking...", List.of("get_weather")));
        stateManager.recordTurn("session-1",
                new StateManager.ConversationTurn(3, "And news?",
                        "Searching...", List.of("search_news")));
        stateManager.recordTurn("session-1",
                new StateManager.ConversationTurn(4, "Again?",
                        "Sure", List.of("get_weather")));

        List<StateManager.ConversationTurn> weatherTurns = stateManager
                .findTurnsByTool("session-1", "get_weather");
        assertEquals(2, weatherTurns.size());
    }

    @Test
    void shouldRetrieveAllTurns() {
        stateManager.recordTurn("session-1",
                new StateManager.ConversationTurn(1, "a", "A", List.of()));
        stateManager.recordTurn("session-1",
                new StateManager.ConversationTurn(2, "b", "B", List.of()));

        List<StateManager.ConversationTurn> all = stateManager
                .getAllTurns("session-1");
        assertEquals(2, all.size());
    }

    // ========================================================================
    // Tool Result Cache
    // ========================================================================

    @Test
    void shouldCacheToolResult() {
        Map<String, Object> args = Map.of("city", "Beijing");
        ToolExecutionContext ctx = ToolExecutionContext.builder()
                .sessionId("s1")
                .traceId("t1")
                .callId("c1")
                .args(args)
                .build();

        com.mcpruntime.core.registry.ToolExecutionResult result =
                com.mcpruntime.core.registry.ToolExecutionResult.success(
                        "get_weather", "Sunny, 25C", 100_000L, ctx);

        stateManager.cacheResult("s1", "get_weather", args, result);

        Optional<com.mcpruntime.core.registry.ToolExecutionResult> cached =
                stateManager.getCachedResult("s1", "get_weather", args);
        assertTrue(cached.isPresent());
        assertEquals("Sunny, 25C", cached.get().getResult());
    }

    @Test
    void shouldReturnEmptyForUncachedTool() {
        Map<String, Object> args = Map.of("city", "Beijing");
        Optional<com.mcpruntime.core.registry.ToolExecutionResult> cached =
                stateManager.getCachedResult("s1", "nonexistent", args);
        assertTrue(cached.isEmpty());
    }

    @Test
    void cacheKeyShouldDistinguishDifferentArgs() {
        Map<String, Object> args1 = Map.of("city", "Beijing");
        Map<String, Object> args2 = Map.of("city", "Shanghai");
        ToolExecutionContext ctx = ToolExecutionContext.builder()
                .sessionId("s1").traceId("t1").build();

        com.mcpruntime.core.registry.ToolExecutionResult r1 =
                com.mcpruntime.core.registry.ToolExecutionResult.success(
                        "get_weather", "Beijing result", 100L, ctx);
        com.mcpruntime.core.registry.ToolExecutionResult r2 =
                com.mcpruntime.core.registry.ToolExecutionResult.success(
                        "get_weather", "Shanghai result", 100L, ctx);

        stateManager.cacheResult("s1", "get_weather", args1, r1);
        stateManager.cacheResult("s1", "get_weather", args2, r2);

        Optional<com.mcpruntime.core.registry.ToolExecutionResult> cached1 =
                stateManager.getCachedResult("s1", "get_weather", args1);
        Optional<com.mcpruntime.core.registry.ToolExecutionResult> cached2 =
                stateManager.getCachedResult("s1", "get_weather", args2);

        assertTrue(cached1.isPresent());
        assertTrue(cached2.isPresent());
        assertEquals("Beijing result", cached1.get().getResult());
        assertEquals("Shanghai result", cached2.get().getResult());
    }

    @Test
    void shouldInvalidateToolCache() {
        Map<String, Object> args = Map.of("q", "hello");
        ToolExecutionContext ctx = ToolExecutionContext.builder()
                .sessionId("s1").traceId("t1").build();
        com.mcpruntime.core.registry.ToolExecutionResult result =
                com.mcpruntime.core.registry.ToolExecutionResult.success(
                        "search", "result", 100L, ctx);

        stateManager.cacheResult("s1", "search", args, result);
        stateManager.invalidateToolCache("s1", "search");

        assertTrue(stateManager.getCachedResult("s1", "search", args).isEmpty());
    }

    @Test
    void shouldInvalidateAllCache() {
        ToolExecutionContext ctx = ToolExecutionContext.builder()
                .sessionId("s1").traceId("t1").build();

        stateManager.cacheResult("s1", "tool_a", Map.of("x", 1),
                com.mcpruntime.core.registry.ToolExecutionResult.success(
                        "tool_a", "a", 100L, ctx));
        stateManager.cacheResult("s1", "tool_b", Map.of("y", 2),
                com.mcpruntime.core.registry.ToolExecutionResult.success(
                        "tool_b", "b", 100L, ctx));

        stateManager.invalidateAllCache("s1");
        assertEquals(0, stateManager.cacheSize("s1"));
    }

    @Test
    void shouldReturnZeroCacheSizeForUnknownSession() {
        assertEquals(0, stateManager.cacheSize("nonexistent"));
    }

    @Test
    void cacheShouldBePerSession() {
        ToolExecutionContext ctx = ToolExecutionContext.builder()
                .sessionId("s1").traceId("t1").build();
        Map<String, Object> args = Map.of("q", "hello");

        stateManager.cacheResult("s1", "search", args,
                com.mcpruntime.core.registry.ToolExecutionResult.success(
                        "search", "s1 result", 100L, ctx));

        assertTrue(stateManager.getCachedResult("s2", "search", args).isEmpty());
    }

    // ========================================================================
    // Cross-feature: Session Removal Cleans Everything
    // ========================================================================

    @Test
    void shouldRemoveSessionAndAllState() {
        stateManager.recordTurn("s1",
                new StateManager.ConversationTurn(1, "hi", "hello", List.of()));
        stateManager.cacheResult("s1", "tool", Map.of(),
                com.mcpruntime.core.registry.ToolExecutionResult.success(
                        "tool", "result", 100L,
                        ToolExecutionContext.builder().sessionId("s1").traceId("t1").build()));

        assertEquals(1, stateManager.turnCount("s1"));
        assertEquals(1, stateManager.cacheSize("s1"));

        stateManager.remove("s1");

        assertTrue(stateManager.get("s1").isEmpty());
        assertEquals(0, stateManager.turnCount("s1"));
        assertEquals(0, stateManager.cacheSize("s1"));
    }

    // ========================================================================
    // ConversationTurn Immutability
    // ========================================================================

    @Test
    void conversationTurnShouldBeImmutable() {
        List<String> toolCalls = new java.util.ArrayList<>(List.of("tool_a"));
        StateManager.ConversationTurn turn = new StateManager.ConversationTurn(
                1, "hello", "world", toolCalls);

        toolCalls.add("tool_b");

        assertEquals(1, turn.getToolCalls().size());
        assertFalse(turn.getToolCalls().contains("tool_b"));
    }

    @Test
    void conversationTurnShouldHandleNullToolCalls() {
        StateManager.ConversationTurn turn = new StateManager.ConversationTurn(
                1, "hello", "world", null);
        assertNotNull(turn.getToolCalls());
        assertTrue(turn.getToolCalls().isEmpty());
    }

    // ========================================================================
    // Concurrent Access Smoke Test
    // ========================================================================

    @Test
    void shouldHandleConcurrentSessionAccess() throws InterruptedException {
        int threadCount = 10;
        int turnsPerThread = 20;
        Thread[] threads = new Thread[threadCount];

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < turnsPerThread; i++) {
                    stateManager.recordTurn("concurrent-session",
                            new StateManager.ConversationTurn(
                                    threadId * turnsPerThread + i,
                                    "msg", "resp", List.of("tool_" + threadId)));
                }
            });
        }

        for (Thread thread : threads) thread.start();
        for (Thread thread : threads) thread.join();

        assertEquals(threadCount * turnsPerThread,
                stateManager.turnCount("concurrent-session"));
    }
}
