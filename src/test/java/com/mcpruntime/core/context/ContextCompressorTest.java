package com.mcpruntime.core.context;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class ContextCompressorTest {

    private List<ContextEntry> createEntries(int count) {
        List<ContextEntry> entries = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            entries.add(ContextEntry.builder()
                    .role(ContextEntry.Role.USER)
                    .content("Message " + i)
                    .timestamp(i * 1000L)
                    .relevanceScore(1.0 / i) // earlier entries have lower scores
                    .build());
        }
        return entries;
    }

    @Test
    void testNoCompressionNeeded() {
        List<ContextEntry> entries = createEntries(3);
        ContextCompressor compressor = new ContextCompressor(
                ContextCompressor.CompressionStrategy.TRIM_OLDEST, 5);
        compressor.compress(entries);
        assertEquals(3, entries.size());
    }

    @Test
    void testTrimOldest() {
        List<ContextEntry> entries = createEntries(10);
        ContextCompressor compressor = new ContextCompressor(
                ContextCompressor.CompressionStrategy.TRIM_OLDEST, 5);
        compressor.compress(entries);
        assertEquals(5, entries.size());
        // After sorting by timestamp ascending and dropping oldest 5,
        // remaining entries should have timestamps 6000-10000
        assertTrue(entries.stream().allMatch(e -> e.getTimestamp() >= 6000));
    }

    @Test
    void testTrimLowestScore() {
        List<ContextEntry> entries = createEntries(10);
        ContextCompressor compressor = new ContextCompressor(
                ContextCompressor.CompressionStrategy.TRIM_LOWEST_SCORE, 5);
        compressor.compress(entries);
        assertEquals(5, entries.size());
        // After trimming lowest 5 scores, remaining should have high relevance
        assertTrue(entries.stream().allMatch(e -> e.getRelevanceScore() >= 0.2));
    }

    @Test
    void testSummarize() {
        List<ContextEntry> entries = createEntries(10);
        Function<List<ContextEntry>, ContextEntry> summarizer = list -> {
            StringBuilder sb = new StringBuilder("Summary of ");
            sb.append(list.size()).append(" messages: ");
            for (ContextEntry e : list) {
                sb.append(e.getContent()).append("; ");
            }
            return ContextEntry.builder()
                    .role(ContextEntry.Role.SUMMARY)
                    .content(sb.toString())
                    .build();
        };

        ContextCompressor compressor = new ContextCompressor(
                ContextCompressor.CompressionStrategy.SUMMARIZE, 5, summarizer);
        compressor.compress(entries);
        assertEquals(5, entries.size());
        // First entry should be the summary
        assertEquals(ContextEntry.Role.SUMMARY, entries.get(0).getRole());
        assertTrue(entries.get(0).getContent().startsWith("Summary of"));
    }

    @Test
    void testSummarizeWithExactTarget() {
        List<ContextEntry> entries = createEntries(5);
        ContextCompressor compressor = new ContextCompressor(
                ContextCompressor.CompressionStrategy.TRIM_OLDEST, 5);
        compressor.compress(entries);
        assertEquals(5, entries.size());
    }

    @Test
    void testSummarizeThrowsWithoutSummarizer() {
        List<ContextEntry> entries = createEntries(10);
        ContextCompressor compressor = new ContextCompressor(
                ContextCompressor.CompressionStrategy.SUMMARIZE, 5);
        assertThrows(IllegalStateException.class, () -> compressor.compress(entries));
    }
}
