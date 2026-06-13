package com.mcpruntime.core.context;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Compresses context entries using configurable strategies.
 * <p>
 * Three built-in strategies:
 * <ul>
 *   <li>{@link CompressionStrategy#TRIM_OLDEST} — drop the oldest entries first</li>
 *   <li>{@link CompressionStrategy#TRIM_LOWEST_SCORE} — drop entries with the
 *       lowest relevance score</li>
 *   <li>{@link CompressionStrategy#SUMMARIZE} — replace a block of entries with
 *       a condensed summary (requires a summarizer function)</li>
 * </ul>
 */
public class ContextCompressor {

    public enum CompressionStrategy {
        TRIM_OLDEST,
        TRIM_LOWEST_SCORE,
        SUMMARIZE
    }

    private final CompressionStrategy strategy;
    private final int targetEntryCount;
    private final Function<List<ContextEntry>, ContextEntry> summarizer;

    public ContextCompressor(CompressionStrategy strategy, int targetEntryCount) {
        this(strategy, targetEntryCount, null);
    }

    public ContextCompressor(CompressionStrategy strategy, int targetEntryCount,
                             Function<List<ContextEntry>, ContextEntry> summarizer) {
        this.strategy = strategy;
        this.targetEntryCount = targetEntryCount;
        this.summarizer = summarizer;
    }

    /**
     * Compress a list of context entries down to {@code targetEntryCount}.
     *
     * @param entries mutable list of entries (will be modified)
     */
    public void compress(List<ContextEntry> entries) {
        if (entries.size() <= targetEntryCount) {
            return;
        }
        int toRemove = entries.size() - targetEntryCount;

        switch (strategy) {
            case TRIM_OLDEST:
                entries.sort(Comparator.comparingLong(ContextEntry::getTimestamp));
                entries.subList(0, toRemove).clear();
                break;

            case TRIM_LOWEST_SCORE:
                entries.sort(Comparator.comparingDouble(ContextEntry::getRelevanceScore));
                entries.subList(0, toRemove).clear();
                break;

            case SUMMARIZE:
                if (summarizer == null) {
                    throw new IllegalStateException(
                            "SUMMARIZE strategy requires a summarizer function");
                }
                entries.sort(Comparator.comparingLong(ContextEntry::getTimestamp));
                int summarizeCount = Math.min(toRemove + 1, entries.size());
                List<ContextEntry> toSummarize = new ArrayList<>(
                        entries.subList(0, summarizeCount));
                ContextEntry summary = summarizer.apply(toSummarize);
                entries.subList(0, summarizeCount).clear();
                entries.add(0, summary);
                break;
        }
    }
}
