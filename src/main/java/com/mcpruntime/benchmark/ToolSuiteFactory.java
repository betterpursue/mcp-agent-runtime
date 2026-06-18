package com.mcpruntime.benchmark;

import com.mcpruntime.core.registry.ToolDefinition;
import com.mcpruntime.core.registry.ToolExecutor;
import com.mcpruntime.core.registry.ToolMetadata;
import com.mcpruntime.core.schema.JsonSchema;
import com.mcpruntime.core.schema.SchemaProperty;

import java.time.Duration;
import java.util.*;

/**
 * Generates tool sets of different sizes for the accuracy benchmark.
 * The first 5 tools are "core" tools that correspond to the test cases.
 * Additional tools are distractors — plausible but wrong for the test queries.
 * <p>
 * Tool count tiers: 5, 10, 15, 20, 30
 */
public class ToolSuiteFactory {

    private ToolSuiteFactory() {}

    /**
     * Generate a tool set with exactly {@code count} tools.
     * Tools are deterministic: same count always yields the same set.
     */
    public static List<ToolDefinition> createTools(int count) {
        List<ToolDefinition> all = allTools();
        if (count > all.size()) {
            throw new IllegalArgumentException("Requested " + count
                + " tools but only " + all.size() + " defined");
        }
        return List.copyOf(all.subList(0, count));
    }

    /** Return all tool count tiers for iteration. */
    public static List<Integer> tiers() {
        return List.of(5, 10, 15, 20, 30);
    }

    // ========================================================================
    // Test cases — each maps to the first 5 core tools
    // ========================================================================

    public static List<TestCase> testCases() {
        return List.of(
            TestCase.of(
                "What's the weather like in Tokyo today?",
                "get_weather",
                "weather query"),
            TestCase.of(
                "Send an email to john@example.com saying the meeting is at 3pm",
                "send_email",
                "email composition"),
            TestCase.of(
                "What is 15 percent of 200?",
                "calculate",
                "arithmetic calculation"),
            TestCase.of(
                "Search for the latest research on retrieval augmented generation",
                "web_search",
                "information retrieval"),
            TestCase.of(
                "Translate 'good morning' into Japanese",
                "translate_text",
                "language translation")
        );
    }

    // ========================================================================
    // Tool definitions (deterministic, ordered by name)
    // ========================================================================

    private static List<ToolDefinition> allTools() {
        List<ToolDefinition> tools = new ArrayList<>();

        // ---- Core tools (first 5, matched to test cases) ----
        tools.add(tool("get_weather",
            "Get the current weather forecast for a specified location",
            props("location", "string", "City name or coordinates")));

        tools.add(tool("send_email",
            "Send an email message to one or more recipients",
            props("to", "string", "Recipient email address"),
            props("subject", "string", "Email subject line"),
            props("body", "string", "Email body content")));

        tools.add(tool("calculate",
            "Evaluate a mathematical expression and return the result",
            props("expression", "string", "Mathematical expression to evaluate")));

        tools.add(tool("web_search",
            "Search the internet for information matching the given query",
            props("query", "string", "Search query text")));

        tools.add(tool("translate_text",
            "Translate text from one language to another",
            props("text", "string", "Text to translate"),
            props("target_language", "string", "Target language code")));

        // ---- Distractors (added incrementally) ----
        tools.add(tool("book_flight",
            "Search for and book a flight between two destinations",
            props("origin", "string", "Departure airport code"),
            props("destination", "string", "Arrival airport code"),
            props("date", "string", "Travel date")));

        tools.add(tool("get_stock_price",
            "Get the current or historical stock price for a ticker symbol",
            props("ticker", "string", "Stock ticker symbol")));

        tools.add(tool("create_calendar_event",
            "Create a new event in the user's calendar",
            props("title", "string", "Event title"),
            props("start_time", "string", "Start time"),
            props("end_time", "string", "End time")));

        tools.add(tool("control_smart_device",
            "Control a smart home device such as lights or thermostat",
            props("device", "string", "Device name"),
            props("action", "string", "Action to perform (on/off/set)")));

        tools.add(tool("get_news_headlines",
            "Retrieve top news headlines for a given category or region",
            props("category", "string", "News category")));

        // 10-15
        tools.add(tool("summarize_text",
            "Generate a concise summary of a long text passage",
            props("text", "string", "Text to summarize")));

        tools.add(tool("generate_image",
            "Generate an image based on a textual description using AI",
            props("prompt", "string", "Image description")));

        tools.add(tool("set_reminder",
            "Set a reminder or alarm for a specified time",
            props("text", "string", "Reminder text"),
            props("time", "string", "Reminder time")));

        tools.add(tool("get_definition",
            "Look up the definition of a word or phrase",
            props("word", "string", "Word to define")));

        tools.add(tool("convert_currency",
            "Convert an amount from one currency to another",
            props("amount", "number", "Amount to convert"),
            props("from", "string", "Source currency code"),
            props("to", "string", "Target currency code")));

        // 15-20
        tools.add(tool("check_grammar",
            "Check text for grammar and spelling errors",
            props("text", "string", "Text to check")));

        tools.add(tool("get_recipe",
            "Find a recipe matching specified ingredients or cuisine type",
            props("ingredients", "string", "Available ingredients"),
            props("cuisine", "string", "Preferred cuisine")));

        tools.add(tool("calculate_distance",
            "Calculate the distance between two geographic locations",
            props("origin", "string", "Starting location"),
            props("destination", "string", "Ending location")));

        tools.add(tool("get_movie_info",
            "Get information about a movie including rating and cast",
            props("title", "string", "Movie title")));

        tools.add(tool("schedule_meeting",
            "Schedule a meeting and send calendar invitations to attendees",
            props("title", "string", "Meeting title"),
            props("attendees", "string", "Attendee email addresses"),
            props("time", "string", "Meeting time")));

        // 20-30
        tools.add(tool("generate_qr_code",
            "Generate a QR code image from a URL or text",
            props("data", "string", "Data to encode")));

        tools.add(tool("lookup_zip_code",
            "Look up the details for a US ZIP code or postal code",
            props("code", "string", "ZIP or postal code")));

        tools.add(tool("get_timezone",
            "Get the timezone information for a city or location",
            props("location", "string", "City or location name")));

        tools.add(tool("create_poll",
            "Create a poll or survey with multiple options",
            props("question", "string", "Poll question"),
            props("options", "string", "Comma-separated options")));

        tools.add(tool("check_website_status",
            "Check whether a website is online and responding",
            props("url", "string", "Website URL")));

        tools.add(tool("generate_password",
            "Generate a random secure password with specified length",
            props("length", "integer", "Password length")));

        tools.add(tool("get_exchange_rate",
            "Get the current exchange rate between two currencies",
            props("from", "string", "Source currency"),
            props("to", "string", "Target currency")));

        tools.add(tool("find_nearby_places",
            "Find nearby points of interest like restaurants or gas stations",
            props("location", "string", "Current location"),
            props("category", "string", "Category of places")));

        tools.add(tool("get_lyrics",
            "Search for the lyrics of a song",
            props("title", "string", "Song title"),
            props("artist", "string", "Artist name")));

        tools.add(tool("scan_barcode",
            "Decode a barcode or QR code from an image",
            props("image_url", "string", "URL of the barcode image")));

        assert tools.size() == 30 : "Expected 30 tools but got " + tools.size();
        return tools;
    }

    // ---- builder helpers ----

    private static ToolDefinition tool(String name, String description,
                                       Object... propDefs) {
        // Flatten: when called via props(), each props() call passes an Object[]{a,b,c}
        // as a single vararg element, so we may have a nested structure.
        Object[] flat = flatten(propDefs);
        JsonSchema.Builder schema = JsonSchema.builder();
        for (int i = 0; i < flat.length; i += 3) {
            String propName = (String) flat[i];
            String propType = (String) flat[i + 1];
            String propDesc = (String) flat[i + 2];
            SchemaProperty prop = switch (propType) {
                case "string" -> SchemaProperty.STRING.withDescription(propDesc);
                case "number" -> SchemaProperty.NUMBER.withDescription(propDesc);
                case "integer" -> SchemaProperty.INTEGER.withDescription(propDesc);
                default -> SchemaProperty.ofType(propType).withDescription(propDesc);
            };
            schema.addProperty(propName, prop);
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

    private static Object[] props(String name, String type, String desc) {
        return new Object[]{name, type, desc};
    }
}
