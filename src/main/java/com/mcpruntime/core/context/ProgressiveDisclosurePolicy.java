package com.mcpruntime.core.context;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides which context entries are relevant enough to include in
 * the current turn's prompt.
 * <p>
 * Progressive disclosure means: not every stored entry needs to be
 * sent to the LLM every time. We keep a rich history, but selectively
 * disclose only what's pertinent to the current query.
 */
public class ProgressiveDisclosurePolicy {

    private final int maxEntriesPerWindow;
    private final boolean alwaysIncludeSystem;

    public ProgressiveDisclosurePolicy(int maxEntriesPerWindow, boolean alwaysIncludeSystem) {
        this.maxEntriesPerWindow = maxEntriesPerWindow;
        this.alwaysIncludeSystem = alwaysIncludeSystem;
    }

    /**
     * Select entries relevant to the current query from the full history.
     * <p>
     * Implementation: always include system entries, then include the most
     * recent N non-summary entries, then fill remaining slots with high-relevance
     * historical entries. This is a simplified heuristic; a production system
     * might use embedding-based semantic similarity for true relevance ranking.
     */
    public List<ContextEntry> select(List<ContextEntry> allEntries, String currentQuery) {
        List<ContextEntry> systemEntries = new ArrayList<>();
        List<ContextEntry> recentEntries = new ArrayList<>();
        List<ContextEntry> historicalEntries = new ArrayList<>();

        for (ContextEntry entry : allEntries) {
            if (entry.getRole() == ContextEntry.Role.SYSTEM) {
                systemEntries.add(entry);
            } else {
                // Anything within the last 3 entries is "recent"
                int index = allEntries.indexOf(entry);
                if (index >= allEntries.size() - 3) {
                    recentEntries.add(entry);
                } else {
                    historicalEntries.add(entry);
                }
            }
        }

        List<ContextEntry> result = new ArrayList<>();
        int remaining = maxEntriesPerWindow;

        // 1. System entries (always included)
        if (alwaysIncludeSystem) {
            result.addAll(systemEntries);
            remaining -= systemEntries.size();
        }

        // 2. Recent entries (high priority)
        if (remaining > 0 && !recentEntries.isEmpty()) {
            int take = Math.min(remaining, recentEntries.size());
            result.addAll(recentEntries.subList(0, take));
            remaining -= take;
        }

        // 3. High-relevance historical entries (fill remaining slots)
        if (remaining > 0 && !historicalEntries.isEmpty()) {
            historicalEntries.sort((a, b) ->
                    Double.compare(b.getRelevanceScore(), a.getRelevanceScore()));
            int take = Math.min(remaining, historicalEntries.size());
            result.addAll(historicalEntries.subList(0, take));
        }

        return result;
    }

    public int getMaxEntriesPerWindow() {
        return maxEntriesPerWindow;
    }
}
