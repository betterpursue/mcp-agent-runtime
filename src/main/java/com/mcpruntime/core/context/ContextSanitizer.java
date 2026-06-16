package com.mcpruntime.core.context;

import java.util.List;

/**
 * Sanitizes and wraps context entries to defend against prompt injection.
 * <p>
 * Each context entry is tagged with an {@link ContextEntry.Role origin} —
 * SYSTEM, USER, TOOL_RESULT, etc. The sanitizer wraps entries with isolation
 * markers based on their origin, helping the LLM distinguish between
 * "instructions" and "content" even when messages are flattened into a single prompt.
 * <p>
 * This is a defense-in-depth measure. It does not replace proper message-role
 * separation at the API level, but provides an additional layer when the
 * underlying model or platform doesn't support multi-role message formatting.
 */
public class ContextSanitizer {

    /**
     * Wrap a context entry with origin-based isolation markers.
     * <p>
     * SYSTEM entries pass through unchanged (they are trusted).
     * TOOL_RESULT and USER entries are wrapped with isolation tags.
     */
    public static ContextEntry sanitize(ContextEntry entry) {
        switch (entry.getRole()) {
            case USER:
                return wrapUserEntry(entry);
            case TOOL_RESULT:
                return wrapToolResultEntry(entry);
            case ASSISTANT:
                return wrapAssistantEntry(entry);
            default:
                return entry;
        }
    }

    /**
     * Sanitize a context window before sending to the LLM.
     */
    public static List<ContextEntry> sanitizeWindow(List<ContextEntry> window) {
        return window.stream()
                .map(ContextSanitizer::sanitize)
                .toList();
    }

    private static ContextEntry wrapUserEntry(ContextEntry entry) {
        return ContextEntry.builder()
                .role(entry.getRole())
                .content("<user_input>\n" + entry.getContent() + "\n</user_input>")
                .timestamp(entry.getTimestamp())
                .relevanceScore(entry.getRelevanceScore())
                .estimatedTokens(entry.getEstimatedTokens() + 2) // two tag lines
                .build();
    }

    private static ContextEntry wrapToolResultEntry(ContextEntry entry) {
        return ContextEntry.builder()
                .role(entry.getRole())
                .content("<tool_result>\n" + entry.getContent() + "\n</tool_result>")
                .timestamp(entry.getTimestamp())
                .relevanceScore(entry.getRelevanceScore())
                .estimatedTokens(entry.getEstimatedTokens() + 2)
                .build();
    }

    private static ContextEntry wrapAssistantEntry(ContextEntry entry) {
        return ContextEntry.builder()
                .role(entry.getRole())
                .content("<assistant>\n" + entry.getContent() + "\n</assistant>")
                .timestamp(entry.getTimestamp())
                .relevanceScore(entry.getRelevanceScore())
                .estimatedTokens(entry.getEstimatedTokens() + 2)
                .build();
    }

    private ContextSanitizer() {}
}
