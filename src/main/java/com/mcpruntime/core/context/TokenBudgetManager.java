package com.mcpruntime.core.context;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks token usage per session, enforcing per-turn and per-session budgets.
 * <p>
 * Token counting here uses a coarse estimation (chars / 4) for non-streaming
 * scenarios. Real production usage should wire in a proper tokenizer (e.g.
 * tiktoken or JTokkit) for model-accurate counts.
 */
public class TokenBudgetManager {

    private final int maxTokensPerTurn;
    private final int maxTokensPerSession;
    private final ConcurrentMap<String, SessionBudget> sessions = new ConcurrentHashMap<>();

    public TokenBudgetManager(int maxTokensPerTurn, int maxTokensPerSession) {
        this.maxTokensPerTurn = maxTokensPerTurn;
        this.maxTokensPerSession = maxTokensPerSession;
    }

    /**
     * Estimate token count from a text string. Rough approximation:
     * 1 token ≈ 4 characters for English-heavy content,
     * ≈ 2 characters for CJK-heavy content.
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int cjkCount = 0;
        int asciiCount = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isIdeographic(c) ||
                (c >= 0x4E00 && c <= 0x9FFF) ||
                (c >= 0x3000 && c <= 0x303F)) {
                cjkCount++;
            } else {
                asciiCount++;
            }
        }
        return (cjkCount / 2) + (asciiCount / 4) + 1;
    }

    /**
     * Check whether adding {@code newTokens} to the given session is within budget.
     *
     * @return true if within budget, false if it would exceed either per-turn or
     * per-session limits.
     */
    public boolean tryAllocate(String sessionId, int newTokens) {
        SessionBudget budget = sessions.computeIfAbsent(sessionId,
                id -> new SessionBudget(maxTokensPerSession));
        if (newTokens > maxTokensPerTurn) {
            return false;
        }
        return budget.tryConsume(newTokens);
    }

    /**
     * Record actual consumption after a turn is complete.
     */
    public void record(String sessionId, int tokens) {
        SessionBudget budget = sessions.computeIfAbsent(sessionId,
                id -> new SessionBudget(maxTokensPerSession));
        budget.add(tokens);
    }

    public long getSessionUsage(String sessionId) {
        SessionBudget budget = sessions.get(sessionId);
        return budget == null ? 0L : budget.used.get();
    }

    public int getMaxTokensPerTurn() {
        return maxTokensPerTurn;
    }

    public int getMaxTokensPerSession() {
        return maxTokensPerSession;
    }

    public void resetSession(String sessionId) {
        sessions.remove(sessionId);
    }

    static class SessionBudget {
        final AtomicLong used = new AtomicLong(0);
        final long limit;

        SessionBudget(long limit) {
            this.limit = limit;
        }

        boolean tryConsume(int tokens) {
            while (true) {
                long current = used.get();
                if (current + tokens > limit) {
                    return false;
                }
                if (used.compareAndSet(current, current + tokens)) {
                    return true;
                }
            }
        }

        void add(int tokens) {
            used.addAndGet(tokens);
        }
    }
}
