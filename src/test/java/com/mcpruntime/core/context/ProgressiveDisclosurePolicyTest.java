package com.mcpruntime.core.context;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProgressiveDisclosurePolicyTest {

    @Test
    void testAlwaysIncludesSystemEntries() {
        ProgressiveDisclosurePolicy policy = new ProgressiveDisclosurePolicy(10, true);
        List<ContextEntry> entries = new ArrayList<>();
        entries.add(ContextEntry.system("You are a helpful assistant."));
        entries.add(ContextEntry.userMessage("Hello"));
        entries.add(ContextEntry.builder().role(ContextEntry.Role.ASSISTANT).content("Hi!").build());

        List<ContextEntry> selected = policy.select(entries, "What's the weather?");
        assertTrue(selected.stream().anyMatch(e -> e.getRole() == ContextEntry.Role.SYSTEM));
    }

    @Test
    void testRecentEntriesAreIncluded() {
        ProgressiveDisclosurePolicy policy = new ProgressiveDisclosurePolicy(10, false);
        List<ContextEntry> entries = new ArrayList<>();
        // Add 10 old messages
        for (int i = 0; i < 10; i++) {
            entries.add(ContextEntry.userMessage("Old message " + i));
        }
        // Add 3 recent messages
        entries.add(ContextEntry.userMessage("Recent 1"));
        entries.add(ContextEntry.builder().role(ContextEntry.Role.ASSISTANT).content("Recent 2").build());
        entries.add(ContextEntry.userMessage("Recent 3"));

        List<ContextEntry> selected = policy.select(entries, "New query");
        assertTrue(selected.size() <= 10);
        // Recent entries should be included (they are at the end)
        assertTrue(selected.stream().anyMatch(e -> "Recent 3".equals(e.getContent())));
    }

    @Test
    void testMaxEntriesLimit() {
        ProgressiveDisclosurePolicy policy = new ProgressiveDisclosurePolicy(5, false);
        List<ContextEntry> entries = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            entries.add(ContextEntry.userMessage("Message " + i));
        }
        List<ContextEntry> selected = policy.select(entries, "Query");
        assertTrue(selected.size() <= 5);
    }

    @Test
    void testEmptyEntries() {
        ProgressiveDisclosurePolicy policy = new ProgressiveDisclosurePolicy(10, true);
        List<ContextEntry> selected = policy.select(List.of(), "Query");
        assertTrue(selected.isEmpty());
    }

    @Test
    void testHighRelevanceHistoricalEntriesIncluded() {
        ProgressiveDisclosurePolicy policy = new ProgressiveDisclosurePolicy(10, false);
        List<ContextEntry> entries = new ArrayList<>();
        // Old entries with high relevance
        entries.add(ContextEntry.builder()
                .role(ContextEntry.Role.USER)
                .content("Important old question")
                .relevanceScore(0.9)
                .build());
        // Many filler entries
        for (int i = 0; i < 15; i++) {
            entries.add(ContextEntry.builder()
                    .role(ContextEntry.Role.USER)
                    .content("Filler " + i)
                    .relevanceScore(0.1)
                    .build());
        }
        // Recent
        entries.add(ContextEntry.userMessage("Latest question"));

        List<ContextEntry> selected = policy.select(entries, "Query");
        // The high-relevance historical entry should be included
        assertTrue(selected.stream().anyMatch(e -> "Important old question".equals(e.getContent())));
    }
}
