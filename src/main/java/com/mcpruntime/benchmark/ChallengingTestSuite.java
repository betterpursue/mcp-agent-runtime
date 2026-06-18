package com.mcpruntime.benchmark;

import com.mcpruntime.core.registry.ToolDefinition;
import com.mcpruntime.core.registry.ToolMetadata;
import com.mcpruntime.core.schema.JsonSchema;
import com.mcpruntime.core.schema.SchemaProperty;

import java.time.Duration;
import java.util.*;

/**
 * Adds harder test cases with semantic overlap, ambiguous intent,
 * and visually similar tool names — designed to stress-test the
 * model's tool selection beyond simple name-matching.
 * <p>
 * Each tier (5, 10, 15, 20, 30) adds progressively sneakier distractors.
 */
public class ChallengingTestSuite {

    private ChallengingTestSuite() {}

    /** Generate challenging test cases. */
    public static List<TestCase> testCases() {
        return List.of(
            // Core cases (5) — not trivial to match
            TestCase.of(
                "东京今天会下雨吗",
                "get_weather",
                "weather query (Chinese)"),

            TestCase.of(
                "Send a meeting reminder email to the team",
                "send_email",
                "email with meeting context"),

            TestCase.of(
                "What's 15% of 200 plus 30?",
                "calculate",
                "compound calculation"),

            TestCase.of(
                "Find recent papers about multi-agent systems",
                "web_search",
                "academic search"),

            TestCase.of(
                "把这个翻译成英文",
                "translate_text",
                "translation (Chinese)"),

            // --- Harder cases (6-10) added at 10-tool tier ---
            TestCase.of(
                "What's the forecast for Shanghai this weekend?",
                "get_weather",
                "weather with time context"),

            TestCase.of(
                "Can you calculate the monthly payment for a 300k loan at 4.5% over 30 years?",
                "calculate",
                "financial calculation"),

            TestCase.of(
                "Search my emails for messages from John about the Q3 report",
                "send_email",
                "email search (context: should use search, not send)"),

            TestCase.of(
                "Converte esta frase para portugues",
                "translate_text",
                "translation (Portuguese)"),

            TestCase.of(
                "Look up the definition of 'ephemeral'",
                "web_search",
                "definition lookup"),

            // --- Even harder (11-15) added at 15-tool tier ---
            TestCase.of(
                "What's 2 + 2?",
                "calculate",
                "simple math (distractor: there's also a calculator tool)"),

            TestCase.of(
                "帮我查一下北京的天气和上海的温度",
                "get_weather",
                "weather dual query (Chinese)"),

            TestCase.of(
                "Write a poem about spring and send it to mom",
                "send_email",
                "content generation + email"),

            TestCase.of(
                "로 이 문장을 번역해줘",
                "translate_text",
                "translation (Korean)"),

            TestCase.of(
                "Check if there's any news about the new iPhone",
                "web_search",
                "news search")
        );
    }

    /**
     * Generate challenging tool definitions with semantic overlap.
     * Tools at higher indices have increasingly similar names/descriptions
     * to the core tools, making selection harder.
     */
    public static List<ToolDefinition> createTools(int count) {
        List<ToolDefinition> all = allTools();
        if (count > all.size()) {
            throw new IllegalArgumentException("Requested " + count
                + " tools but only " + all.size() + " defined");
        }
        return List.copyOf(all.subList(0, count));
    }

    public static List<Integer> tiers() {
        return List.of(5, 10, 15);
    }

    private static List<ToolDefinition> allTools() {
        List<ToolDefinition> tools = new ArrayList<>();

        // ---- Core tools (0-4) ----
        tools.add(chtool("get_weather",
            "获取当前天气信息 / Get current weather for a location",
            "string", "location", "City name"));

        tools.add(chtool("send_email",
            "发送邮件 / Send an email message",
            "string", "to", "Recipient",
            "string", "subject", "Subject",
            "string", "body", "Body"));

        tools.add(chtool("calculate",
            "执行数学运算 / Perform mathematical calculation",
            "string", "expression", "Math expression"));

        tools.add(chtool("web_search",
            "搜索互联网信息 / Search the web for information",
            "string", "query", "Search query"));

        tools.add(chtool("translate_text",
            "翻译文本到目标语言 / Translate text to a target language",
            "string", "text", "Text to translate",
            "string", "target_lang", "Target language"));

        // ---- Distractors (5-14) ----
        tools.add(chtool("get_forecast",
            "获取天气预报，支持未来7天 / Get weather forecast for the week",
            "string", "location", "City",
            "string", "days", "Number of days"));

        tools.add(chtool("search_news",
            "搜索新闻文章 / Search news articles",
            "string", "query", "News query",
            "string", "category", "Category"));

        tools.add(chtool("convert_units",
            "单位换算 / Convert between units",
            "string", "value", "Value",
            "string", "from_unit", "From unit",
            "string", "to_unit", "To unit"));

        tools.add(chtool("search_emails",
            "搜索邮箱中的邮件 / Search through email inbox",
            "string", "query", "Search query"));

        tools.add(chtool("lookup_dictionary",
            "查词典，获取单词定义 / Look up word definition in dictionary",
            "string", "word", "Word to look up"));

        // ---- More distractors (10-14) ----
        tools.add(chtool("get_climate_data",
            "查询气候数据，包括历史气温和降水 / Get historical climate data",
            "string", "location", "Location",
            "string", "period", "Time period"));

        tools.add(chtool("generate_content",
            "生成文本内容 / Generate text content using AI",
            "string", "prompt", "Content prompt",
            "string", "format", "Output format"));

        tools.add(chtool("get_article",
            "获取指定文章全文 / Retrieve full text of an article",
            "string", "url", "Article URL"));

        tools.add(chtool("check_weather_alerts",
            "查看天气警报 / Check weather alerts and warnings",
            "string", "location", "Location"));

        tools.add(chtool("math_solver",
            "解数学题，支持方程和微积分 / Solve math problems step by step",
            "string", "problem", "Math problem description"));

        return tools;
    }

    @SuppressWarnings("all")
    private static ToolDefinition chtool(String name, String description,
                                          Object... propDefs) {
        Object[] flat = flatten(propDefs);
        JsonSchema.Builder schema = JsonSchema.builder();
        for (int i = 0; i < flat.length; i += 3) {
            String propName = (String) flat[i];
            String propDesc = (String) flat[i + 2];
            schema.addProperty(propName, SchemaProperty.STRING.withDescription(propDesc));
        }

        return ToolDefinition.builder()
            .name(name)
            .description(description)
            .parameterSchema(schema.build())
            .executor(ctx -> "[benchmark stub: " + name + "]")
            .metadata(ToolMetadata.builder()
                .timeout(Duration.ofSeconds(5))
                .owner("benchmark")
                .build())
            .build();
    }

    private static Object[] flatten(Object... args) {
        List<Object> result = new ArrayList<>();
        for (Object arg : args) {
            if (arg instanceof Object[]) {
                Collections.addAll(result, (Object[]) arg);
            } else {
                result.add(arg);
            }
        }
        return result.toArray();
    }
}
