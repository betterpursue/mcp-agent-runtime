package com.mcpruntime.benchmark;

import com.mcpruntime.core.registry.ToolDefinition;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Runs the Tool Selection accuracy benchmark using {@link ChallengingTestSuite}
 * which includes semantically overlapping tool names and harder test cases.
 * <p>
 * Usage:
 * <pre>
 *   java -Ddeepseek.api.key=sk-xxx com.mcpruntime.benchmark.ChallengingBenchmark
 * </pre>
 */
public class ChallengingBenchmark {

    private final DeepSeekClient client;

    public ChallengingBenchmark(String apiKey) {
        this.client = new DeepSeekClient(apiKey);
    }

    public void run() throws Exception {
        var testCases = ChallengingTestSuite.testCases();
        var tiers = ChallengingTestSuite.tiers();

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║   Tool Selection Accuracy — Challenging Test Suite         ║");
        System.out.println("║   Model: deepseek-v4-flash (non-thinking)                   ║");
        System.out.println("║   Date: " + LocalDateTime.now()
            .format(DateTimeFormatter.ISO_LOCAL_DATE) + "                            ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        System.out.println("Test cases (" + testCases.size() + "):");
        for (TestCase tc : testCases) {
            System.out.printf("  [%-30s] → %s%n", tc.description(), tc.expectedTool());
        }
        System.out.println();

        Map<Integer, List<Result>> allResults = new LinkedHashMap<>();

        for (int toolCount : tiers) {
            System.out.println("─── Tools: " + toolCount + " ─────────────────────────────");
            List<ToolDefinition> tools = ChallengingTestSuite.createTools(toolCount);
            List<Result> results = new ArrayList<>();
            int correct = 0;

            for (TestCase tc : testCases) {
                Result r = evaluate(tc, tools);
                results.add(r);
                if (r.correct) correct++;
                String status = r.correct ? "✓" : "✗";
                System.out.printf("  %-35s %s → %s%n",
                    tc.description(),
                    status,
                    r.chosenTool.isEmpty() ? "(no tool call)" : truncate(r.chosenTool, 25));
            }

            int total = testCases.size();
            double accuracy = (double) correct / total * 100;
            System.out.printf("  ─────────────────────────────────────────%n");
            System.out.printf("  Accuracy: %.1f%% (%d/%d)%n%n", accuracy, correct, total);
            allResults.put(toolCount, results);
        }

        printSummary(allResults, testCases);
    }

    private Result evaluate(TestCase tc, List<ToolDefinition> tools) {
        try {
            long start = System.currentTimeMillis();
            String chosen = client.callWithTools(tc.query(), tools);
            long elapsed = System.currentTimeMillis() - start;
            boolean correct = chosen.equals(tc.expectedTool());
            return new Result(tc, chosen, correct, elapsed);
        } catch (Exception e) {
            System.err.println("  API error for '" + tc.description() + "': " + e.getMessage());
            return new Result(tc, "[error]", false, 0);
        }
    }

    private void printSummary(Map<Integer, List<Result>> allResults, List<TestCase> testCases) {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("  Summary");
        System.out.println();

        // Header
        System.out.printf("%-6s", "Tools");
        for (TestCase tc : testCases) {
            System.out.printf("  %-12s", tc.description());
        }
        System.out.printf("  %6s%n", "Acc");

        // Separator
        System.out.printf("%-6s", "─────");
        for (int i = 0; i < testCases.size(); i++) {
            System.out.printf("  %-12s", "────────────");
        }
        System.out.printf("  %6s%n", "──────");

        for (var entry : allResults.entrySet()) {
            int toolCount = entry.getKey();
            List<Result> results = entry.getValue();
            int correct = (int) results.stream().filter(r -> r.correct).count();
            double accuracy = (double) correct / results.size() * 100;

            System.out.printf("%-5d  ", toolCount);
            for (Result r : results) {
                System.out.printf("  %-12s", r.correct ? "✓" : "✗");
            }
            System.out.printf("  %5.1f%%%n", accuracy);
        }

        System.out.println();

        // Detailed breakdown
        System.out.println("── Detailed results ──");
        for (var entry : allResults.entrySet()) {
            System.out.println("\nTools=" + entry.getKey() + ":");
            for (Result r : entry.getValue()) {
                System.out.printf("  %-35s → got='%s' (expected='%s') %s%n",
                    r.testCase().description(),
                    r.chosenTool,
                    r.testCase().expectedTool(),
                    r.correct ? "✓" : "✗");
            }
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    record Result(TestCase testCase, String chosenTool, boolean correct, long elapsedMs) {}

    public static void main(String[] args) throws Exception {
        String apiKey = System.getProperty("deepseek.api.key");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("Usage: java -Ddeepseek.api.key=sk-xxx " + ChallengingBenchmark.class.getName());
            System.exit(1);
        }
        new ChallengingBenchmark(apiKey).run();
    }
}
