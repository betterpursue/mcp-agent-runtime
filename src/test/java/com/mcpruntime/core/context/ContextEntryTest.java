package com.mcpruntime.core.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContextEntryTest {

    @Test
    void testBuilderWithAllFields() {
        ContextEntry entry = ContextEntry.builder()
                .role(ContextEntry.Role.USER)
                .content("Hello")
                .timestamp(1000L)
                .relevanceScore(0.8)
                .estimatedTokens(5)
                .build();
        assertEquals(ContextEntry.Role.USER, entry.getRole());
        assertEquals("Hello", entry.getContent());
        assertEquals(1000L, entry.getTimestamp());
        assertEquals(0.8, entry.getRelevanceScore(), 0.001);
        assertEquals(5, entry.getEstimatedTokens());
    }

    @Test
    void testBuilderAutoEstimatesTokens() {
        ContextEntry entry = ContextEntry.builder()
                .role(ContextEntry.Role.USER)
                .content("test")
                .build();
        assertTrue(entry.getEstimatedTokens() > 0);
    }

    @Test
    void testEntryWithNullContent() {
        ContextEntry entry = ContextEntry.builder()
                .role(ContextEntry.Role.SYSTEM)
                .build();
        assertEquals("", entry.getContent());
    }

    @Test
    void testStaticFactoryUserMessage() {
        ContextEntry entry = ContextEntry.userMessage("Hello");
        assertEquals(ContextEntry.Role.USER, entry.getRole());
        assertEquals("Hello", entry.getContent());
    }

    @Test
    void testStaticFactorySystem() {
        ContextEntry entry = ContextEntry.system("Be helpful");
        assertEquals(ContextEntry.Role.SYSTEM, entry.getRole());
        assertEquals("Be helpful", entry.getContent());
    }

    @Test
    void testStaticFactoryToolResult() {
        ContextEntry entry = ContextEntry.toolResult("get_weather", "25°C, sunny");
        assertEquals(ContextEntry.Role.TOOL_RESULT, entry.getRole());
        assertTrue(entry.getContent().contains("get_weather"));
        assertTrue(entry.getContent().contains("25°C, sunny"));
    }

    @Test
    void testDefaultRelevanceScore() {
        ContextEntry entry = ContextEntry.userMessage("Hi");
        assertEquals(1.0, entry.getRelevanceScore(), 0.001);
    }

    @Test
    void testDefaultTimestampIsRecent() {
        ContextEntry entry = ContextEntry.userMessage("Hi");
        long now = System.currentTimeMillis();
        assertTrue(entry.getTimestamp() <= now);
        assertTrue(entry.getTimestamp() > now - 5000);
    }
}
