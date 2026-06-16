package com.mcpruntime.core.state;

import com.mcpruntime.core.registry.ToolExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

/**
 * A hybrid session manager that combines local in-memory state with a
 * remote store for cross-node visibility.
 * <p>
 * Designed for horizontal scaling while minimizing changes to the existing
 * {@link StateManager} API. Key design decisions:
 * <ul>
 *   <li><b>Local StateManager is the source of truth</b> for active sessions</li>
 *   <li><b>Remote store tracks session existence</b> — allows other nodes to
 *       know a session exists even if this node doesn't have it cached locally</li>
 *   <li><b>Session metadata stored remotely</b> includes creation time, last
 *       access time, and turn count — enough for routing decisions and monitoring</li>
 * </ul>
 * <p>
 * This is the "Local Cache + Remote Fallback" pattern described in the series.
 * If a session is not found locally, the manager checks the remote store.
 * If the remote store confirms the session exists, a fresh local state is created
 * (conversation history is reconstructed from the conversation log stored elsewhere).
 * <p>
 * Thread-safe. Local operations are delegated to {@link StateManager} (concurrent map based).
 * Remote operations use an async executor to avoid blocking the caller.
 */
public class HybridSessionManager {

    private static final Logger log = LoggerFactory.getLogger(HybridSessionManager.class);

    private final StateManager localStateManager;
    private final RemoteSessionStore remoteStore;
    private final Executor asyncWriter;

    public HybridSessionManager(StateManager localStateManager,
                                RemoteSessionStore remoteStore) {
        this(localStateManager, remoteStore,
                Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "session-async-writer");
                    t.setDaemon(true);
                    return t;
                }));
    }

    HybridSessionManager(StateManager localStateManager,
                         RemoteSessionStore remoteStore,
                         Executor asyncWriter) {
        this.localStateManager = localStateManager;
        this.remoteStore = remoteStore;
        this.asyncWriter = asyncWriter;
    }

    // ========================================================================
    // Session lifecycle
    // ========================================================================

    /**
     * Get or create a session. Checks local StateManager first,
     * then falls back to the remote store for existence confirmation.
     * <p>
     * If the session exists remotely but not locally, a fresh local state
     * is created and marked as a "reconstructed" session.
     */
    public StateManager.SessionState getOrCreate(String sessionId) {
        // 1. Fast path: local state exists
        Optional<StateManager.SessionState> local = localStateManager.get(sessionId);
        if (local.isPresent()) {
            return local.get();
        }

        // 2. Remote check: does this session exist on another node?
        if (remoteStore.exists(sessionId)) {
            log.debug("Session {} exists remotely, creating fresh local state", sessionId);
            // Create new local state — conversation history should be loaded
            // from the conversation store (a separate concern beyond session state)
            return localStateManager.getOrCreate(sessionId);
        }

        // 3. Fresh session
        return localStateManager.getOrCreate(sessionId);
    }

    /**
     * Save session metadata to the remote store asynchronously.
     * <p>
     * The remote store keeps lightweight metadata (session exists, last accessed time).
     * Full conversation history and tool cache are managed locally.
     * Call this after each turn completes so other nodes can discover the session.
     */
    public void saveSessionMetadata(String sessionId, StateManager.SessionState state) {
        asyncWriter.execute(() -> {
            try {
                // Store minimal metadata: just enough to confirm existence
                // and for routing decisions
                byte[] metadata = serializeMetadata(state);
                long ttl = Duration.ofHours(1).toMillis();
                remoteStore.save(sessionId, metadata, ttl);
            } catch (Exception e) {
                log.error("Failed to save session {} metadata: {}", sessionId, e.getMessage());
            }
        });
    }

    /**
     * Remove a session locally and remotely.
     */
    public void remove(String sessionId) {
        localStateManager.remove(sessionId);
        asyncWriter.execute(() -> {
            try {
                remoteStore.remove(sessionId);
            } catch (Exception e) {
                log.warn("Failed to remove session {} from remote store: {}", sessionId, e.getMessage());
            }
        });
    }

    /**
     * Check if a session exists in any layer.
     */
    public boolean exists(String sessionId) {
        return localStateManager.get(sessionId).isPresent() || remoteStore.exists(sessionId);
    }

    // ========================================================================
    // Delegation to local StateManager
    // ========================================================================

    public StateManager.ConversationTurn recordTurn(String sessionId,
                                                StateManager.ConversationTurn turn) {
        return localStateManager.recordTurn(sessionId, turn);
    }

    public List<StateManager.ConversationTurn> getRecentTurns(String sessionId, int limit) {
        return localStateManager.getRecentTurns(sessionId, limit);
    }

    public List<StateManager.ConversationTurn> getAllTurns(String sessionId) {
        return localStateManager.getAllTurns(sessionId);
    }

    public int turnCount(String sessionId) {
        return localStateManager.turnCount(sessionId);
    }

    public Optional<ToolExecutionResult> getCachedResult(
            String sessionId, String toolName, Map<String, Object> args) {
        return localStateManager.getCachedResult(sessionId, toolName, args);
    }

    public void cacheResult(String sessionId, String toolName,
                            Map<String, Object> args, ToolExecutionResult result) {
        localStateManager.cacheResult(sessionId, toolName, args, result);
    }

    public int cacheSize(String sessionId) {
        return localStateManager.cacheSize(sessionId);
    }

    public void invalidateToolCache(String sessionId, String toolName) {
        localStateManager.invalidateToolCache(sessionId, toolName);
    }

    public void invalidateAllCache(String sessionId) {
        localStateManager.invalidateAllCache(sessionId);
    }

    public StateManager getLocalStateManager() {
        return localStateManager;
    }

    // ========================================================================
    // Metadata serialization (lightweight — just for cross-node routing)
    // ========================================================================

    /**
     * Serialize session metadata into a byte array.
     * <p>
     * The metadata is deliberately minimal: just session ID, creation time,
     * last access time, and turn count. Full state (conversation history,
     * tool cache) stays local.
     */
    private static byte[] serializeMetadata(StateManager.SessionState state) {
        try {
            String meta = String.join("|",
                    state.getSessionId(),
                    String.valueOf(state.getCreatedAt().toEpochMilli()),
                    String.valueOf(state.getLastAccessedAt().toEpochMilli()),
                    String.valueOf(state.getAgeMillis())
            );
            return meta.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to serialize session metadata: {}", e.getMessage());
            return new byte[0];
        }
    }
}
