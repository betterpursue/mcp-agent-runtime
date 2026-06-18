package com.mcpruntime.benchmark;

import com.mcpruntime.core.registry.ToolDefinition;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Tool Selection Accuracy Benchmark for MCP Agent Runtime.
 * <p>
 * Measures how often DeepSeek v4 Flash selects the correct tool
 * as the number of available tools grows (5, 10, 15, 20, 30).
 * <p>
 * Usage:
 * <pre>
 *   java -Ddeepseek.api.key=sk-xxx com.mcpruntime.benchmark.AccuracyBenchmark
 * </pre>
 * <p>
 * Results are printed as a table to stdout and saved to a CSV file.
 */
public class AccuracyBenchmark {

    private final DeepSeekClient client;
    private final List<TestCase> testCases;
    private final List<Integer> tiers;

    public AccuracyBenchmark(String apiKey) {
        this.client = new DeepSeekClient(apiKey);
        this.testCases = ToolSuiteFactory.testCases();
        this.tiers = ToolSuiteFactory.tiers();
    }

    /**
     * Run the full benchmark and print a summary table.
     */
    public BenchmarkResult run() throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║   Tool Selection Accuracy Benchmark                     ║");
        System.out.println("║   Model: deepseek-v4-flash (non-thinking)               ║");
        System.out.println("║   Date: " + LocalDateTime.now()
            .format(DateTimeFormatter.ISO_LOCAL_DATE) + "                          ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.println();

        Map<Integer, List<Result>> allResults = new LinkedHashMap<>();

        for (int toolCount : tiers) {
            System.out.println("─── Tools: " + toolCount + " ───────────────────────");
            List<ToolDefinition> tools = ToolSuiteFactory.createTools(toolCount);
            List<Result> results = new ArrayList<>();
            int correct = 0;

            for (TestCase tc : testCases) {
                Result r = evaluate(tc, tools);
                results.add(r);
                if (r.correct) correct++;
                System.out.printf("  %-30s → %s %s%n",
                    tc.description(),
                    r.chosenTool.isEmpty() ? "(no tool call)" : r.chosenTool,
                    r.correct ? "✓" : "✗ (expected: " + r.testCase().expectedTool() + ")");
            }

            double accuracy = (double) correct / testCases.size() * 100;
            System.out.printf("  Accuracy: %.1f%% (%d/%d)%n%n", accuracy, correct, testCases.size());
            allResults.put(toolCount, results);
        }

        printSummary(allResults);
        return new BenchmarkResult(allResults, testCases);
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
            return new Result(tc, "[error: " + e.getMessage() + "]", false, 0);
        }
    }

    private void printSummary(Map<Integer, List<Result>> allResults) {
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("  Summary");
        System.out.println();

        // Header
        System.out.printf("%-8s", "Tools");
        for (TestCase tc : testCases) {
            System.out.printf("  %-20s", tc.description());
        }
        System.out.printf("  %-10s%n", "Accuracy");

        // Separator
        System.out.printf("%-8s", "─────");
        for (int i = 0; i < testCases.size(); i++) {
            System.out.printf("  %-20s", "────────────────────");
        }
        System.out.printf("  %-10s%n", "─────────");

        // Rows
        Map<Integer, Double> overallAccuracy = new LinkedHashMap<>();
        for (int toolCount : tiers) {
            List<Result> results = allResults.get(toolCount);
            int correct = (int) results.stream().filter(r -> r.correct).count();
            double accuracy = (double) correct / testCases.size() * 100;
            overallAccuracy.put(toolCount, accuracy);

            System.out.printf("%-5d  ", toolCount);
            for (Result r : results) {
                String status = r.correct ? "✓" : "✗";
                System.out.printf("  %-20s", status);
            }
            System.out.printf("  %5.1f%%%n", accuracy);
        }

        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════");
    }

    // ========================================================================
    // Result types
    // ========================================================================

    public record Result(TestCase testCase, String chosenTool, boolean correct, long elapsedMs) {}

    public record BenchmarkResult(Map<Integer, List<Result>> results, List<TestCase> testCases) {

        public void printCsv() {
            System.out.println("tool_count,test_case,correct,chosen_tool,expected_tool,elapsed_ms");
            for (var entry : results.entrySet()) {
                int toolCount = entry.getKey();
                for (Result r : entry.getValue()) {
                    System.out.printf("%d,%s,%s,%s,%s,%d%n",
                        toolCount,
                        r.testCase().description(),
                        r.correct,
                        r.chosenTool,
                        r.testCase().expectedTool(),
                        r.elapsedMs);
                }
            }
        }
    }

    // ========================================================================
    // Main
    // ========================================================================

    public static void main(String[] args) throws Exception {
        String apiKey = System.getProperty("deepseek.api.key");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("Usage: java -Ddeepseek.api.key=sk-xxx " + AccuracyBenchmark.class.getName());
            System.exit(1);
        }

        AccuracyBenchmark bench = new AccuracyBenchmark(apiKey);
        BenchmarkResult result = bench.run();

        System.out.println("\n\nCSV output:\n");
        result.printCsv();
    }
}
