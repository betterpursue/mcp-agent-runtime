package com.mcpruntime.core.context;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContextManagerTest {

    private ContextManager createManager() {
        TokenBudgetManager budget = new TokenBudgetManager(1000, 10000);
        ContextCompressor compressor = new ContextCompressor(
                ContextCompressor.CompressionStrategy.TRIM_OLDEST, 20);
        ProgressiveDisclosurePolicy policy = new ProgressiveDisclosurePolicy(50, true);
        return new ContextManager(budget, compressor, policy);
    }

    @Test
    void testAddEntry() {
        ContextManager mgr = createManager();
        ContextEntry entry = ContextEntry.userMessage("Hello");
        assertTrue(mgr.addEntry("session-1", entry));
        assertEquals(1, mgr.getEntryCount("session-1"));
    }

    @Test
    void testAddEntryRejectedWhenBudgetExceeded() {
        TokenBudgetManager tight = new TokenBudgetManager(5, 100);
        ContextCompressor compressor = new ContextCompressor(
                ContextCompressor.CompressionStrategy.TRIM_OLDEST, 20);
        ProgressiveDisclosurePolicy policy = new ProgressiveDisclosurePolicy(50, true);
        ContextManager mgr = new ContextManager(tight, compressor, policy);

        ContextEntry largeEntry = ContextEntry.builder()
                .role(ContextEntry.Role.USER)
                .content("A".repeat(100)) // ~25 tokens
                .build();

        assertFalse(mgr.addEntry("session-1", largeEntry));
    }

    @Test
    void testBuildWindow() {
        ContextManager mgr = createManager();
        mgr.addEntry("session-1", ContextEntry.system("System prompt"));
        mgr.addEntry("session-1", ContextEntry.userMessage("Hello"));
        mgr.addEntry("session-1", ContextEntry.builder()
                .role(ContextEntry.Role.ASSISTANT)
                .content("Hi there!")
                .build());

        List<ContextEntry> window = mgr.buildWindow("session-1", "How are you?");
        assertFalse(window.isEmpty());
        assertTrue(window.stream().anyMatch(e -> e.getRole() == ContextEntry.Role.SYSTEM));
    }

    @Test
    void testBuildPrompt() {
        ContextManager mgr = createManager();
        mgr.addEntry("session-1", ContextEntry.system("Be helpful."));
        mgr.addEntry("session-1", ContextEntry.userMessage("Hi"));

        String prompt = mgr.buildPrompt("session-1", "Tell me a joke");
        assertTrue(prompt.contains("System"));
        assertTrue(prompt.contains("Be helpful."));
        assertTrue(prompt.contains("Tell me a joke"));
    }

    @Test
    void testBuildWindowForUnknownSession() {
        ContextManager mgr = createManager();
        List<ContextEntry> window = mgr.buildWindow("nonexistent", "Hello");
        assertTrue(window.isEmpty());
    }

    @Test
    void testResetSession() {
        ContextManager mgr = createManager();
        mgr.addEntry("session-1", ContextEntry.userMessage("Hello"));
        assertEquals(1, mgr.getEntryCount("session-1"));
        mgr.resetSession("session-1");
        assertEquals(0, mgr.getEntryCount("session-1"));
    }

    @Test
    void testSessionTokenUsage() {
        ContextManager mgr = createManager();
        mgr.addEntry("session-1", ContextEntry.userMessage("Hello world"));
        mgr.addEntry("session-1", ContextEntry.userMessage("How are you?"));
        assertTrue(mgr.getSessionTokenUsage("session-1") > 0);
    }
}
