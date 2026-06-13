package com.mcpruntime.core.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenBudgetManagerTest {

    @Test
    void testEstimateTokensEmpty() {
        assertEquals(0, TokenBudgetManager.estimateTokens(""));
        assertEquals(0, TokenBudgetManager.estimateTokens(null));
    }

    @Test
    void testEstimateTokensAscii() {
        // "hello world" = 11 chars / 4 = 2 + 1 = 3
        int tokens = TokenBudgetManager.estimateTokens("hello world");
        assertTrue(tokens > 0);
        assertEquals(3, tokens);
    }

    @Test
    void testEstimateTokensCjk() {
        // CJK chars count as ~2 chars per token
        int tokens = TokenBudgetManager.estimateTokens("你好世界");
        assertEquals(2 + 1, tokens); // 4 CJK chars / 2 = 2, + 1 base
    }

    @Test
    void testTryAllocateWithinBudget() {
        TokenBudgetManager mgr = new TokenBudgetManager(100, 1000);
        assertTrue(mgr.tryAllocate("session-1", 50));
        assertTrue(mgr.tryAllocate("session-1", 30));
    }

    @Test
    void testTryAllocateExceedsTurnBudget() {
        TokenBudgetManager mgr = new TokenBudgetManager(100, 1000);
        assertFalse(mgr.tryAllocate("session-1", 150));
    }

    @Test
    void testTryAllocateExceedsSessionBudget() {
        TokenBudgetManager mgr = new TokenBudgetManager(100, 100);
        assertTrue(mgr.tryAllocate("session-1", 60));
        assertTrue(mgr.tryAllocate("session-1", 40));
        assertFalse(mgr.tryAllocate("session-1", 10));
    }

    @Test
    void testRecordAndGetUsage() {
        TokenBudgetManager mgr = new TokenBudgetManager(100, 1000);
        mgr.record("session-1", 100);
        mgr.record("session-1", 200);
        assertEquals(300, mgr.getSessionUsage("session-1"));
    }

    @Test
    void testResetSession() {
        TokenBudgetManager mgr = new TokenBudgetManager(100, 1000);
        mgr.record("session-1", 500);
        assertEquals(500, mgr.getSessionUsage("session-1"));
        mgr.resetSession("session-1");
        assertEquals(0, mgr.getSessionUsage("session-1"));
    }

    @Test
    void testDifferentSessionsIndependent() {
        TokenBudgetManager mgr = new TokenBudgetManager(100, 200);
        mgr.record("session-a", 150);
        mgr.record("session-b", 150);
        assertEquals(150, mgr.getSessionUsage("session-a"));
        assertEquals(150, mgr.getSessionUsage("session-b"));
        assertFalse(mgr.tryAllocate("session-a", 100)); // would exceed session budget
        assertFalse(mgr.tryAllocate("session-b", 100)); // same
    }
}
