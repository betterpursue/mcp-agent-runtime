package com.mcpruntime.core.state;

import com.mcpruntime.core.registry.ToolExecutionContext;
import com.mcpruntime.core.registry.ToolExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link HybridSessionManager}.
 * <p>
 * Covers: local + remote session lifecycle, existence checks, delegation to
 * local StateManager, async remote persistence.
 */
class HybridSessionManagerTest {

    private static final long TTL = 60_000L;

    private StateManager localManager;
    private InMemoryRemoteStore remoteStore;
    private HybridSessionManager hybridManager;

    @BeforeEach
    void setUp() {
        localManager = new StateManager(TTL);
        remoteStore = new InMemoryRemoteStore();
        // Use a synchronous executor for deterministic test behavior
        hybridManager = new HybridSessionManager(localManager, remoteStore, Runnable::run);
    }

    // ========================================================================
    // Session lifecycle
    // ========================================================================

    @Test
    void shouldCreateNewSessionLocally() {
        StateManager.SessionState session = hybridManager.getOrCreate("session-1");
        assertNotNull(session);
        assertEquals("session-1", session.getSessionId());
    }

    @Test
    void shouldFallbackToRemoteWhenLocalMissing() {
        // Save session metadata remotely first
        StateManager.SessionState original = localManager.getOrCreate("session-1");
        hybridManager.saveSessionMetadata("session-1", original);

        // Clear local state (simulate another node's perspective)
        localManager.remove("session-1");

        // Should still find the session via remote store
        assertTrue(hybridManager.exists("session-1"));

        StateManager.SessionState restored = hybridManager.getOrCreate("session-1");
        assertNotNull(restored);
        assertEquals("session-1", restored.getSessionId());
    }

    @Test
    void shouldReturnExistingLocalSession() {
        StateManager.SessionState first = hybridManager.getOrCreate("session-1");
        StateManager.SessionState second = hybridManager.getOrCreate("session-1");
        assertSame(first, second);
    }

    @Test
    void shouldRemoveSessionFromAllLayers() {
        hybridManager.getOrCreate("session-1");
        hybridManager.saveSessionMetadata("session-1",
                localManager.getOrCreate("session-1"));

        hybridManager.remove("session-1");

        assertFalse(hybridManager.exists("session-1"));
        assertFalse(remoteStore.exists("session-1"));
    }

    // ========================================================================
    // Remote store interaction
    // ========================================================================

    @Test
    void shouldSaveMetadataToRemoteStore() {
        StateManager.SessionState session = hybridManager.getOrCreate("session-1");
        hybridManager.saveSessionMetadata("session-1", session);

        assertTrue(remoteStore.exists("session-1"));
    }

    @Test
    void shouldNotExistRemotelyBeforeSaving() {
        assertFalse(remoteStore.exists("never-created"));
    }

    @Test
    void shouldCheckExistenceAcrossLayers() {
        assertFalse(hybridManager.exists("unknown"));

        hybridManager.getOrCreate("session-1");
        assertTrue(hybridManager.exists("session-1"));
    }

    @Test
    void shouldCheckExistenceAcrossLayersWhenOnlyRemote() {
        // Directly add to remote store
        hybridManager.saveSessionMetadata("remote-only",
                hybridManager.getOrCreate("remote-only"));

        // Clear local
        localManager.remove("remote-only");

        assertTrue(hybridManager.exists("remote-only"));
    }

    // ========================================================================
    // Delegation to StateManager
    // ========================================================================

    @Test
    void shouldDelegateGetRecentTurns() {
        hybridManager.recordTurn("session-1",
                new StateManager.ConversationTurn(1, "hello", "hi", List.of()));

        List<StateManager.ConversationTurn> turns = hybridManager.getRecentTurns("session-1", 10);
        assertEquals(1, turns.size());
        assertEquals("hello", turns.get(0).getUserMessage());
    }

    @Test
    void shouldDelegateGetAllTurns() {
        hybridManager.recordTurn("session-1",
                new StateManager.ConversationTurn(1, "msg1", "resp1", List.of()));
        hybridManager.recordTurn("session-1",
                new StateManager.ConversationTurn(2, "msg2", "resp2", List.of()));

        assertEquals(2, hybridManager.getAllTurns("session-1").size());
        assertEquals(2, hybridManager.turnCount("session-1"));
    }

    @Test
    void shouldReturnEmptyForSessionWithoutTurns() {
        assertTrue(hybridManager.getRecentTurns("nonexistent", 10).isEmpty());
        assertTrue(hybridManager.getAllTurns("nonexistent").isEmpty());
        assertEquals(0, hybridManager.turnCount("nonexistent"));
    }

    // ========================================================================
    // Tool cache delegation
    // ========================================================================

    @Test
    void shouldCacheAndRetrieveToolResult() {
        ToolExecutionContext ctx = ToolExecutionContext.builder()
                .sessionId("session-1").traceId("trace-1").build();
        ToolExecutionResult result = ToolExecutionResult.success(
                "weather", "sunny", 100L, ctx);

        hybridManager.cacheResult("session-1", "weather",
                Map.of("city", "Beijing"), result);

        Optional<ToolExecutionResult> cached =
                hybridManager.getCachedResult("session-1", "weather",
                        Map.of("city", "Beijing"));
        assertTrue(cached.isPresent());
        assertEquals("sunny", cached.get().getResult());
    }

    @Test
    void shouldReturnEmptyForUncachedTool() {
        Optional<ToolExecutionResult> cached =
                hybridManager.getCachedResult("session-1", "weather",
                        Map.of("city", "Tokyo"));
        assertTrue(cached.isEmpty());
    }

    @Test
    void shouldInvalidateToolCache() {
        ToolExecutionContext ctx = ToolExecutionContext.builder()
                .sessionId("session-1").traceId("trace-1").build();
        hybridManager.cacheResult("session-1", "weather",
                Map.of("city", "Beijing"),
                ToolExecutionResult.success("weather", "sunny", 100L, ctx));

        hybridManager.invalidateToolCache("session-1", "weather");

        assertTrue(hybridManager.getCachedResult("session-1", "weather",
                Map.of("city", "Beijing")).isEmpty());
    }

    @Test
    void shouldInvalidateAllCache() {
        ToolExecutionContext ctx = ToolExecutionContext.builder()
                .sessionId("session-1").traceId("trace-1").build();
        hybridManager.cacheResult("session-1", "weather",
                Map.of("city", "Beijing"),
                ToolExecutionResult.success("weather", "sunny", 100L, ctx));
        hybridManager.cacheResult("session-1", "search",
                Map.of("q", "test"),
                ToolExecutionResult.success("search", "results", 200L, ctx));

        hybridManager.invalidateAllCache("session-1");

        assertEquals(0, hybridManager.cacheSize("session-1"));
    }

    @Test
    void shouldReturnZeroCacheSizeForEmptySession() {
        assertEquals(0, hybridManager.cacheSize("nonexistent"));
    }

    // ========================================================================
    // Edge cases
    // ========================================================================

    @Test
    void shouldHandleConcurrentAccess() throws InterruptedException {
        int threadCount = 10;
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            new Thread(() -> {
                try {
                    String sessionId = "concurrent-" + (idx % 3);
                    StateManager.SessionState session = hybridManager.getOrCreate(sessionId);
                    assertNotNull(session);
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await();
        assertTrue(hybridManager.exists("concurrent-0"));
        assertTrue(hybridManager.exists("concurrent-1"));
        assertTrue(hybridManager.exists("concurrent-2"));
    }
}
