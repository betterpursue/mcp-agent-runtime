package com.mcpruntime.core.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Central state management for the Agent runtime.
 * <p>
 * Three responsibilities wrapped into one cohesive manager:
 * <ol>
 *   <li><b>Session lifecycle</b> — create, track, expire sessions with TTL</li>
 *   <li><b>Conversation history</b> — incremental turn recording, delta-only queries</li>
 *   <li><b>Tool result cache</b> — keyed by {@code (sessionId, toolName, args hash)} to avoid re-execution</li>
 * </ol>
 * <p>
 * Thread-safe. Uses ConcurrentHashMap internally; session access is lock-free,
 * turn recording and cache operations are lock-per-key.
 * <p>
 * Design principle: what goes <em>into</em> a session (turns, cache) is managed here.
 * What goes <em>out</em> (context window assembly, progressive disclosure) belongs
 * in {@link com.mcpruntime.core.context.ContextManager}. The boundary is clear:
 * StateManager owns the raw data, ContextManager owns the LLM-facing view.
 */
public class StateManager {

    private static final Logger log = LoggerFactory.getLogger(StateManager.class);

    private final ConcurrentMap<String, SessionState> sessions = new ConcurrentHashMap<>();
    private final long sessionTtlMillis;

    public StateManager(long sessionTtlMillis) {
        this.sessionTtlMillis = sessionTtlMillis;
    }

    // ========================================================================
    // Session Lifecycle
    // ========================================================================

    /**
     * Get an existing session or create a new one. Automatically handles
     * expiry: if a session exists but has exceeded its TTL since last access,
     * it is replaced with a fresh one.
     */
    public SessionState getOrCreate(String sessionId) {
        return sessions.compute(sessionId, (id, existing) -> {
            if (existing != null && !existing.isExpired(sessionTtlMillis)) {
                existing.touch();
                return existing;
            }
            if (existing != null) {
                log.debug("Session {} expired, creating fresh state", sessionId);
            }
            return new SessionState(sessionId);
        });
    }

    /**
     * Retrieve a session only if it exists and hasn't expired.
     */
    public Optional<SessionState> get(String sessionId) {
        SessionState state = sessions.get(sessionId);
        if (state == null) {
            return Optional.empty();
        }
        if (state.isExpired(sessionTtlMillis)) {
            sessions.remove(sessionId, state);
            return Optional.empty();
        }
        return Optional.of(state);
    }

    /**
     * Remove a session and all associated state (turns + cache).
     */
    public void remove(String sessionId) {
        sessions.remove(sessionId);
        log.debug("Removed session {}", sessionId);
    }

    /**
     * Evict all expired sessions.
     */
    public void expireAll() {
        sessions.values().removeIf(s -> s.isExpired(sessionTtlMillis));
    }

    /**
     * Return the number of currently active (non-expired) sessions.
     */
    public int activeSessionCount() {
        expireAll();
        return sessions.size();
    }

    // ========================================================================
    // Conversation History — Incremental Turn Management
    // ========================================================================

    /**
     * Record a conversation turn for a session. Turns are stored sequentially;
     * only delta (the new turn) is persisted each time — no full-history rewrite.
     */
    public ConversationTurn recordTurn(String sessionId, ConversationTurn turn) {
        SessionState state = getOrCreate(sessionId);
        state.addTurn(turn);
        return turn;
    }

    /**
     * Get the most recent N turns for a session. Retrieval is O(N) on the
     * sublist — cheap for typical Agent interaction depths (5-20 turns).
     */
    public List<ConversationTurn> getRecentTurns(String sessionId, int limit) {
        return get(sessionId)
                .map(state -> state.getRecentTurns(limit))
                .orElse(List.of());
    }

    /**
     * Get all turns for a session (useful for full history export or
     * summarization).
     */
    public List<ConversationTurn> getAllTurns(String sessionId) {
        return get(sessionId)
                .map(SessionState::getAllTurns)
                .orElse(List.of());
    }

    /**
     * Total number of turns recorded for a session.
     */
    public int turnCount(String sessionId) {
        return get(sessionId).map(SessionState::getTurnCount).orElse(0);
    }

    /**
     * Find turns that involved a specific tool call (reverse lookup by tool name).
     */
    public List<ConversationTurn> findTurnsByTool(String sessionId, String toolName) {
        return get(sessionId)
                .map(state -> state.getAllTurns().stream()
                        .filter(t -> t.getToolCalls().contains(toolName))
                        .collect(Collectors.toList()))
                .orElse(List.of());
    }

    // ========================================================================
    // Tool Result Cache
    // ========================================================================

    /**
     * Retrieve a cached tool execution result if one exists.
     * Cache key is (sessionId, toolName, args hash).
     */
    public Optional<com.mcpruntime.core.registry.ToolExecutionResult>
    getCachedResult(String sessionId, String toolName, Map<String, Object> args) {
        return get(sessionId)
                .flatMap(state -> state.getCachedResult(toolName, args));
    }

    /**
     * Store a tool execution result in the session cache.
     * Subsequent calls with identical (toolName, args) will return this result
     * without re-executing the tool.
     */
    public void cacheResult(String sessionId, String toolName,
                            Map<String, Object> args,
                            com.mcpruntime.core.registry.ToolExecutionResult result) {
        SessionState state = getOrCreate(sessionId);
        state.cacheResult(toolName, args, result);
    }

    /**
     * Invalidate all cached results for a specific tool within a session.
     * Useful when the session context changes such that cached results are stale.
     */
    public void invalidateToolCache(String sessionId, String toolName) {
        get(sessionId).ifPresent(state -> state.invalidateToolCache(toolName));
    }

    /**
     * Invalidate the entire cache for a session.
     */
    public void invalidateAllCache(String sessionId) {
        get(sessionId).ifPresent(SessionState::invalidateAllCache);
    }

    /**
     * Return a count of currently cached results for a session (for monitoring/debug).
     */
    public int cacheSize(String sessionId) {
        return get(sessionId).map(SessionState::getCacheSize).orElse(0);
    }

    // ========================================================================
    // Inner Types
    // ========================================================================

    /**
     * Holds all mutable state for a single session. Package-private — all
     * public operations go through {@link StateManager}.
     */
    public static class SessionState {

        private final String sessionId;
        private final Instant createdAt;
        private volatile Instant lastAccessedAt;
        private final List<ConversationTurn> turns;
        private final ConcurrentMap<String, com.mcpruntime.core.registry.ToolExecutionResult> toolCache;
        private final AtomicInteger turnCounter;

        SessionState(String sessionId) {
            this.sessionId = sessionId;
            this.createdAt = Instant.now();
            this.lastAccessedAt = Instant.now();
            this.turns = Collections.synchronizedList(new ArrayList<>());
            this.toolCache = new ConcurrentHashMap<>();
            this.turnCounter = new AtomicInteger(0);
        }

        void touch() {
            this.lastAccessedAt = Instant.now();
        }

        boolean isExpired(long ttlMillis) {
            return Instant.now().toEpochMilli()
                    - lastAccessedAt.toEpochMilli() > ttlMillis;
        }

        public String getSessionId()                     { return sessionId; }
        public Instant getCreatedAt()                    { return createdAt; }
        public Instant getLastAccessedAt()               { return lastAccessedAt; }
        public long getAgeMillis() {
            return Instant.now().toEpochMilli() - createdAt.toEpochMilli();
        }

        // -- Turns --

        void addTurn(ConversationTurn turn) {
            turns.add(turn);
        }

        List<ConversationTurn> getRecentTurns(int limit) {
            synchronized (turns) {
                int size = turns.size();
                if (size <= limit) {
                    return List.copyOf(turns);
                }
                return List.copyOf(turns.subList(size - limit, size));
            }
        }

        List<ConversationTurn> getAllTurns() {
            synchronized (turns) {
                return List.copyOf(turns);
            }
        }

        int getTurnCount() { return turns.size(); }

        int nextTurnNumber() { return turnCounter.incrementAndGet(); }

        // -- Cache --

        Optional<com.mcpruntime.core.registry.ToolExecutionResult>
        getCachedResult(String toolName, Map<String, Object> args) {
            return Optional.ofNullable(toolCache.get(cacheKey(toolName, args)));
        }

        void cacheResult(String toolName, Map<String, Object> args,
                         com.mcpruntime.core.registry.ToolExecutionResult result) {
            toolCache.put(cacheKey(toolName, args), result);
        }

        void invalidateToolCache(String toolName) {
            toolCache.entrySet()
                    .removeIf(e -> e.getKey().startsWith(toolName + "::"));
        }

        void invalidateAllCache() {
            toolCache.clear();
        }

        int getCacheSize() { return toolCache.size(); }

        private static String cacheKey(String toolName, Map<String, Object> args) {
            return toolName + "::" + Objects.hash(
                    args != null ? args.entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .map(e -> e.getKey() + "=" + e.getValue())
                            .collect(Collectors.joining("&"))
                            : "");
        }
    }

    /**
     * A single conversation turn — the atomic unit of conversation history.
     * <p>
     * Immutable after creation. Stores only the delta: what was said, what was
     * responded, and which tools were called.
     */
    public static class ConversationTurn {

        private final int turnNumber;
        private final String userMessage;
        private final String assistantResponse;
        private final List<String> toolCalls;
        private final Instant timestamp;

        public ConversationTurn(int turnNumber, String userMessage,
                                String assistantResponse, List<String> toolCalls) {
            this.turnNumber = turnNumber;
            this.userMessage = userMessage;
            this.assistantResponse = assistantResponse;
            this.toolCalls = toolCalls != null ? List.copyOf(toolCalls) : List.of();
            this.timestamp = Instant.now();
        }

        public int getTurnNumber()              { return turnNumber; }
        public String getUserMessage()          { return userMessage; }
        public String getAssistantResponse()    { return assistantResponse; }
        public List<String> getToolCalls()      { return toolCalls; }
        public Instant getTimestamp()           { return timestamp; }
    }
}
