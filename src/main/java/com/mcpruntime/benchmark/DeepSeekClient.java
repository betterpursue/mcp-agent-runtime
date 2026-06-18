package com.mcpruntime.benchmark;

import com.mcpruntime.core.registry.ToolDefinition;
import com.mcpruntime.core.schema.JsonSchema;
import com.mcpruntime.core.schema.SchemaProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Calls DeepSeek's OpenAI-compatible chat completions endpoint
 * with tool definitions and returns the model's tool choice.
 * <p>
 * Uses the non-thinking mode of deepseek-v4-flash.
 */
public class DeepSeekClient {

    private static final String API_URL = "https://api.deepseek.com/v1/chat/completions";
    private static final String MODEL = "deepseek-v4-flash";

    private final String apiKey;
    private final HttpClient httpClient;

    public DeepSeekClient(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    /**
     * Send a query with available tools and return the tool name the model chose.
     *
     * @return chosen tool name, or empty string if model did not call a tool
     * @throws Exception on network/API errors
     */
    public String callWithTools(String query, List<ToolDefinition> tools) throws Exception {
        String requestBody = buildRequestBody(query, tools);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .timeout(Duration.ofSeconds(30))
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("API returned " + response.statusCode()
                + ": " + response.body());
        }

        return parseToolChoice(response.body());
    }

    // ---- request builder ----

    private String buildRequestBody(String query, List<ToolDefinition> tools) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"model\": \"").append(MODEL).append("\",\n");
        json.append("  \"messages\": [\n");
        json.append("    {\"role\": \"user\", \"content\": \"")
            .append(escapeJson(query)).append("\"}\n");
        json.append("  ],\n");
        json.append("  \"tools\": [\n");

        for (int i = 0; i < tools.size(); i++) {
            ToolDefinition t = tools.get(i);
            json.append("    {\n");
            json.append("      \"type\": \"function\",\n");
            json.append("      \"function\": {\n");
            json.append("        \"name\": \"").append(escapeJson(t.getName())).append("\",\n");
            json.append("        \"description\": \"").append(escapeJson(t.getDescription())).append("\",\n");
            json.append("        \"parameters\": ").append(toOpenAiSchema(t.getInputSchema())).append("\n");
            json.append("      }\n");
            json.append("    }");
            if (i < tools.size() - 1) json.append(",");
            json.append("\n");
        }

        json.append("  ],\n");
        json.append("  \"tool_choice\": \"auto\"\n");
        json.append("}\n");

        return json.toString();
    }

    /**
     * Convert the project's JsonSchema to OpenAI-compatible JSON schema string.
     */
    private String toOpenAiSchema(JsonSchema schema) {
        Map<String, Object> m = schema.toMap();
        return mapToJson(m, 0);
    }

    @SuppressWarnings("unchecked")
    private String mapToJson(Map<String, Object> map, int indent) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        int i = 0;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            sb.append("    ".repeat(indent + 1));
            sb.append("\"").append(escapeJson(e.getKey())).append("\": ");
            Object val = e.getValue();
            if (val instanceof Map) {
                sb.append(mapToJson((Map<String, Object>) val, indent + 1));
            } else if (val instanceof List) {
                sb.append(listToJson((List<Object>) val, indent + 1));
            } else if (val instanceof String s) {
                sb.append("\"").append(escapeJson(s)).append("\"");
            } else {
                sb.append(val);
            }
            if (i < map.size() - 1) sb.append(",");
            sb.append("\n");
            i++;
        }
        sb.append("    ".repeat(indent)).append("}");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String listToJson(List<Object> list, int indent) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            Object val = list.get(i);
            if (val instanceof Map) {
                sb.append(mapToJson((Map<String, Object>) val, indent));
            } else if (val instanceof String s) {
                sb.append("\"").append(escapeJson(s)).append("\"");
            } else {
                sb.append(val);
            }
            if (i < list.size() - 1) sb.append(", ");
        }
        sb.append("]");
        return sb.toString();
    }

    // ---- response parser ----

    /**
     * Extract the tool name from the API response JSON.
     * Returns empty string if no tool was called.
     */
    static String parseToolChoice(String responseJson) {
        // Simple JSON parsing without external dependency
        // Look for "tool_calls" array in the response
        int toolCallsIdx = responseJson.indexOf("\"tool_calls\"");
        if (toolCallsIdx < 0) return "";

        // Find the function name within the first tool call
        int functionIdx = responseJson.indexOf("\"function\"", toolCallsIdx);
        if (functionIdx < 0) return "";

        int nameIdx = responseJson.indexOf("\"name\"", functionIdx);
        if (nameIdx < 0) return "";

        int colonIdx = responseJson.indexOf(':', nameIdx);
        int quoteStart = responseJson.indexOf('"', colonIdx + 1);
        int quoteEnd = responseJson.indexOf('"', quoteStart + 1);

        if (quoteStart < 0 || quoteEnd < 0) return "";

        return responseJson.substring(quoteStart + 1, quoteEnd);
    }

    // ---- JSON helpers ----

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
