package com.mcpruntime.core.state;

import java.util.Optional;

/**
 * Abstraction for remote session storage backing the {@link HybridSessionManager}.
 * <p>
 * Implementations can be Redis, PostgreSQL, or any durable store.
 * The interface assumes serialized session states — the HybridSessionManager
 * handles local caching and deserialization.
 * <p>
 * All operations should be idempotent. Implementations must be thread-safe.
 */
public interface RemoteSessionStore {

    /**
     * Persist a session state to remote storage.
     *
     * @param sessionId the session identifier
     * @param state     the serializable session state data
     * @param ttlMillis time-to-live in milliseconds
     */
    void save(String sessionId, byte[] state, long ttlMillis);

    /**
     * Load a session state from remote storage.
     *
     * @param sessionId the session identifier
     * @return the serialized session state, or empty if not found or expired
     */
    Optional<byte[]> load(String sessionId);

    /**
     * Remove a session from remote storage.
     *
     * @param sessionId the session identifier
     */
    void remove(String sessionId);

    /**
     * Check if a session exists in remote storage.
     */
    boolean exists(String sessionId);

    /**
     * Health check — returns true if the remote store is reachable.
     */
    boolean isAvailable();
}
