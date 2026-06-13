package com.mcpruntime.core.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central context orchestration for Agent sessions.
 * <p>
 * Manages the lifecycle of context entries per session: adding entries,
 * enforcing token budgets, deciding when to compress, and assembling the
 * final context window for LLM consumption.
 * <p>
 * Designed as the backbone for the MCP Agent Runtime's multi-turn
 * conversation handling. Integrates with {@link TokenBudgetManager} for
 * budget enforcement and {@link ContextCompressor} for compression.
 */
public class ContextManager {

    private static final Logger log = LoggerFactory.getLogger(ContextManager.class);

    private final TokenBudgetManager tokenBudget;
    private final ContextCompressor compressor;
    private final ProgressiveDisclosurePolicy disclosurePolicy;
    private final ConcurrentMap<String, List<ContextEntry>> sessions = new ConcurrentHashMap<>();

    public ContextManager(TokenBudgetManager tokenBudget,
                          ContextCompressor compressor,
                          ProgressiveDisclosurePolicy disclosurePolicy) {
        this.tokenBudget = tokenBudget;
        this.compressor = compressor;
        this.disclosurePolicy = disclosurePolicy;
    }

    /**
     * Add a new context entry to a session. Automatically checks token budget
     * and triggers compression if needed.
     *
     * @return true if the entry was added, false if it was rejected (budget exceeded)
     */
    public boolean addEntry(String sessionId, ContextEntry entry) {
        List<ContextEntry> entries = sessions.computeIfAbsent(sessionId,
                id -> new CopyOnWriteArrayList<>());

        int estimated = entry.getEstimatedTokens();
        if (!tokenBudget.tryAllocate(sessionId, estimated)) {
            log.warn("Token budget exceeded for session {} (turn budget: {}, entry: {} tokens)",
                    sessionId, tokenBudget.getMaxTokensPerTurn(), estimated);
            return false;
        }

        entries.add(entry);
        tokenBudget.record(sessionId, estimated);

        // Trigger compression if session has grown too large
        if (entries.size() > getMaxEntriesBeforeCompression()) {
            compressor.compress(entries);
            log.info("Compressed context for session {} to {} entries", sessionId, entries.size());
        }

        return true;
    }

    /**
     * Build the final context window — the list of entries that will actually
     * be sent to the LLM. Applies progressive disclosure: not all stored entries
     * make it into every turn.
     */
    public List<ContextEntry> buildWindow(String sessionId, String currentQuery) {
        List<ContextEntry> allEntries = sessions.get(sessionId);
        if (allEntries == null || allEntries.isEmpty()) {
            return List.of();
        }
        return disclosurePolicy.select(allEntries, currentQuery);
    }

    /**
     * Build the window and flatten to a single prompt string.
     */
    public String buildPrompt(String sessionId, String currentQuery) {
        List<ContextEntry> window = buildWindow(sessionId, currentQuery);
        StringBuilder sb = new StringBuilder();
        for (ContextEntry entry : window) {
            switch (entry.getRole()) {
                case SYSTEM -> sb.append("[System]\n").append(entry.getContent()).append("\n\n");
                case USER -> sb.append("[User]\n").append(entry.getContent()).append("\n\n");
                case ASSISTANT -> sb.append("[Assistant]\n").append(entry.getContent()).append("\n\n");
                case TOOL_CALL -> sb.append("[Tool Call]\n").append(entry.getContent()).append("\n\n");
                case TOOL_RESULT -> sb.append("[Tool Result]\n").append(entry.getContent()).append("\n\n");
                case SUMMARY ->
                    sb.append("[Summary of previous conversation]\n").append(entry.getContent()).append("\n\n");
            }
        }
        sb.append("[User]\n").append(currentQuery);
        return sb.toString();
    }

    public int getEntryCount(String sessionId) {
        List<ContextEntry> entries = sessions.get(sessionId);
        return entries == null ? 0 : entries.size();
    }

    public long getSessionTokenUsage(String sessionId) {
        return tokenBudget.getSessionUsage(sessionId);
    }

    public void resetSession(String sessionId) {
        sessions.remove(sessionId);
        tokenBudget.resetSession(sessionId);
    }

    private int getMaxEntriesBeforeCompression() {
        return 50;
    }
}
