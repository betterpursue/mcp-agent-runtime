package com.mcpruntime.core.state;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link RemoteSessionStore} for testing and
 * single-node deployments.
 * <p>
 * Stores sessions in a {@link ConcurrentHashMap} with TTL-based expiry.
 * Not suitable for production multi-node setups — use a Redis-backed
 * implementation for horizontal scaling.
 */
public class InMemoryRemoteStore implements RemoteSessionStore {

    private final Map<String, StoredSession> store = new ConcurrentHashMap<>();

    @Override
    public void save(String sessionId, byte[] state, long ttlMillis) {
        store.put(sessionId, new StoredSession(state, ttlMillis));
    }

    @Override
    public Optional<byte[]> load(String sessionId) {
        StoredSession stored = store.get(sessionId);
        if (stored == null) {
            return Optional.empty();
        }
        if (stored.isExpired()) {
            store.remove(sessionId);
            return Optional.empty();
        }
        return Optional.of(stored.data);
    }

    @Override
    public void remove(String sessionId) {
        store.remove(sessionId);
    }

    @Override
    public boolean exists(String sessionId) {
        StoredSession stored = store.get(sessionId);
        if (stored == null) {
            return false;
        }
        if (stored.isExpired()) {
            store.remove(sessionId);
            return false;
        }
        return true;
    }

    @Override
    public boolean isAvailable() {
        return true; // in-memory is always available
    }

    public int size() {
        // Expire stale entries before counting
        store.values().removeIf(StoredSession::isExpired);
        return store.size();
    }

    public void clear() {
        store.clear();
    }

    private static class StoredSession {
        final byte[] data;
        final long expiresAt;

        StoredSession(byte[] data, long ttlMillis) {
            this.data = data;
            this.expiresAt = Instant.now().toEpochMilli() + ttlMillis;
        }

        boolean isExpired() {
            return Instant.now().toEpochMilli() > expiresAt;
        }
    }
}
