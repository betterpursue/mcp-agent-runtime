package com.mcpruntime.core.structured;

import com.mcpruntime.core.schema.JsonSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Structured output compatibility layer for reasoning models.
 * <p>
 * Bridges the gap between raw LLM output and the Runtime's structured
 * representation (Tool call requests, JSON-formatted responses, etc.).
 * Different model providers emit structured output in different formats:
 * <ul>
 *   <li>OpenAI-compatible: {@code tool_calls} array in the delta</li>
 *   <li>Anthropic: XML tags / function_call blocks</li>
 *   <li>Open-source models: configurable JSON prefixes</li>
 * </ul>
 * <p>
 * The adapter normalizes these into a uniform {@link StructuredOutput}
 * that downstream consumers (e.g. Tool execution engine) can process
 * without knowing the origin model's conventions.
 * <p>
 * In addition to parsing, the adapter supports <b>constrained decoding</b>
 * by applying {@link OutputConstraint}s — schema-driven guards that
 * shape what the model sees as its response prefix.
 */
public class StructuredOutputAdapter {

    private static final Logger log = LoggerFactory.getLogger(StructuredOutputAdapter.class);

    private final Map<String, OutputConstraint> constraints = new ConcurrentHashMap<>();
    private final OutputFormat defaultFormat;

    public StructuredOutputAdapter() {
        this(OutputFormat.OPENAI);
    }

    public StructuredOutputAdapter(OutputFormat defaultFormat) {
        this.defaultFormat = defaultFormat;
    }

    // ========================================================================
    // Parsing — raw string → StructuredOutput
    // ========================================================================

    /**
     * Parse a raw LLM completion into a structured output.
     * <p>
     * Attempts each registered parser in priority order and returns the
     * first successful parse. If no parser succeeds, returns a text-only
     * output with the raw content.
     */
    public StructuredOutput parse(String sessionId, String rawOutput, OutputFormat format) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return StructuredOutput.empty();
        }

        // 1. Try to extract tool calls from the output
        List<ToolCall> toolCalls = extractToolCalls(rawOutput, format);

        // 2. Separate tool calls from text content
        String textContent = stripToolCalls(rawOutput, format);

        // 3. Validate against registered constraint (if any)
        OutputConstraint constraint = constraints.get(sessionId);
        if (constraint != null && !toolCalls.isEmpty()) {
            validateToolCalls(toolCalls, constraint);
        }

        return new StructuredOutput(textContent, toolCalls, format);
    }

    /**
     * Convenience overload — uses the default format.
     */
    public StructuredOutput parse(String sessionId, String rawOutput) {
        return parse(sessionId, rawOutput, defaultFormat);
    }

    // ========================================================================
    // Constraint management
    // ========================================================================

    /**
     * Register an output constraint for a session.
     * <p>
     * The constraint will be applied to subsequent parse() calls for
     * the same session. Setting a constraint implicitly sets the format
     * to the constraint's preferred format.
     */
    public void setConstraint(String sessionId, OutputConstraint constraint) {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(constraint);
        constraints.put(sessionId, constraint);
        log.debug("Output constraint registered for session {}: expected tool {}",
                sessionId, constraint.getToolName());
    }

    /**
     * Remove the constraint for a session.
     */
    public void removeConstraint(String sessionId) {
        constraints.remove(sessionId);
    }

    /**
     * Build a constrained decoding prefix — an <b>incomplete</b> JSON string
     * that is prepended to the model's response to guide generation toward a
     * specific structured format.
     * <p>
     * The prefix is deliberately unterminated: the model is expected to
     * <b>complete</b> the JSON with the actual arguments. This is a common
     * technique for guiding open-source models toward structured output.
     * <p>
     * Example: if the constraint expects a tool call to "get_weather",
     * the returned prefix would be:
     * <pre>
     * {"tool": "get_weather", "args": {
     * </pre>
     * The model should complete this with:
     * <pre>
     * {"tool": "get_weather", "args": {"city": "Beijing"}}
     * </pre>
     */
    public String buildConstrainedPrefix(OutputConstraint constraint) {
        Objects.requireNonNull(constraint);

        JsonSchema schema = constraint.getArgsSchema().orElse(null);
        if (schema == null || schema.toMap().isEmpty()) {
            return String.format("{\"tool\": \"%s\", \"args\": {", constraint.getToolName());
        }

        return String.format("{\"tool\": \"%s\", \"args\": ", constraint.getToolName());
    }

    /**
     * Check whether a session has an active constraint.
     */
    public boolean hasConstraint(String sessionId) {
        return constraints.containsKey(sessionId);
    }

    // ========================================================================
    // Internal: Tool Call Extraction
    // ========================================================================

    private List<ToolCall> extractToolCalls(String rawOutput, OutputFormat format) {
        return switch (format) {
            case OPENAI -> extractOpenAiToolCalls(rawOutput);
            case ANTHROPIC -> extractAnthropicToolCalls(rawOutput);
            case GENERIC_JSON -> extractGenericJsonToolCalls(rawOutput);
        };
    }

    private List<ToolCall> extractOpenAiToolCalls(String rawOutput) {
        // OpenAI format: {"tool_calls": [{"function": {"name": "...", "arguments": "..."}}]}
        List<ToolCall> calls = new ArrayList<>();

        // Simple heuristic: look for "tool_calls" in the raw output
        int tcIdx = rawOutput.indexOf("\"tool_calls\"");
        if (tcIdx < 0) {
            tcIdx = rawOutput.indexOf("'tool_calls'");
        }
        if (tcIdx < 0) {
            return calls; // no tool calls detected
        }

        // Look for function name patterns
        int nameIdx = rawOutput.indexOf("\"name\"", tcIdx);
        if (nameIdx < 0) {
            nameIdx = rawOutput.indexOf("'name'", tcIdx);
        }
        while (nameIdx >= 0) {
            int colonIdx = rawOutput.indexOf(':', nameIdx + 6);
            if (colonIdx < 0) break;

            int startQuote = rawOutput.indexOf('"', colonIdx + 1);
            if (startQuote < 0) {
                startQuote = rawOutput.indexOf('\'', colonIdx + 1);
            }
            if (startQuote < 0) break;

            int endQuote = rawOutput.indexOf('"', startQuote + 1);
            if (endQuote < 0) {
                endQuote = rawOutput.indexOf('\'', startQuote + 1);
            }
            if (endQuote < 0) break;

            String name = rawOutput.substring(startQuote + 1, endQuote);

            // Extract arguments
            int argIdx = rawOutput.indexOf("\"arguments\"", endQuote);
            if (argIdx < 0) {
                argIdx = rawOutput.indexOf("'arguments'", endQuote);
            }
            String args = "";
            if (argIdx >= 0) {
                int argColon = rawOutput.indexOf(':', argIdx + 10);
                if (argColon >= 0) {
                    int argStart = rawOutput.indexOf('{', argColon);
                    if (argStart >= 0) {
                        int argEnd = findMatchingBrace(rawOutput, argStart);
                        if (argEnd > argStart) {
                            args = rawOutput.substring(argStart, argEnd + 1);
                        }
                    }
                }
            }

            calls.add(new ToolCall(name, args));
            nameIdx = rawOutput.indexOf("\"name\"", endQuote);
            if (nameIdx < 0) {
                nameIdx = rawOutput.indexOf("'name'", endQuote);
            }
        }

        return calls;
    }

    private List<ToolCall> extractAnthropicToolCalls(String rawOutput) {
        // Anthropic format: <function_calls><invoke name="xxx">...</invoke></function_calls>
        List<ToolCall> calls = new ArrayList<>();

        int idx = 0;
        while (true) {
            int invokeStart = rawOutput.indexOf("<invoke", idx);
            if (invokeStart < 0) break;

            int nameStart = rawOutput.indexOf("name=\"", invokeStart);
            if (nameStart < 0) {
                nameStart = rawOutput.indexOf("name='", invokeStart);
            }
            if (nameStart < 0) {
                idx = invokeStart + 7;
                continue;
            }

            int nameEndQuote = rawOutput.indexOf('"', nameStart + 6);
            if (nameEndQuote < 0) {
                nameEndQuote = rawOutput.indexOf('\'', nameStart + 6);
            }
            if (nameEndQuote < 0) {
                idx = invokeStart + 7;
                continue;
            }

            String name = rawOutput.substring(nameStart + 6, nameEndQuote);

            int invokeClose = rawOutput.indexOf('>', nameEndQuote);
            if (invokeClose < 0) break;

            int invokeEnd = rawOutput.indexOf("</invoke>", invokeClose);
            if (invokeEnd < 0) break;

            String innerContent = rawOutput.substring(invokeClose + 1, invokeEnd);

            calls.add(new ToolCall(name, innerContent));
            idx = invokeEnd + 9;
        }

        return calls;
    }

    private List<ToolCall> extractGenericJsonToolCalls(String rawOutput) {
        // Generic JSON format: a JSON object with "tool"/"action" + "args" fields
        // at the top level or nested in a "tool_calls" array
        List<ToolCall> calls = new ArrayList<>();

        // If the output starts with an array bracket, parse as array first
        int firstBracket = rawOutput.indexOf('[');
        int firstBrace = rawOutput.indexOf('{');

        if (firstBracket >= 0 && (firstBrace < 0 || firstBracket < firstBrace)) {
            // Array comes first — parse its elements
            int bracketEnd = findMatchingBracket(rawOutput, firstBracket);
            if (bracketEnd > firstBracket) {
                calls.addAll(parseToolCallArray(rawOutput, firstBracket, bracketEnd));
                if (!calls.isEmpty()) return calls;
            }
        }

        // Try top-level JSON object
        if (firstBrace >= 0) {
            int braceEnd = findMatchingBrace(rawOutput, firstBrace);
            if (braceEnd > firstBrace) {
                String jsonBlock = rawOutput.substring(firstBrace, braceEnd + 1);
                ToolCall single = parseGenericToolCall(jsonBlock);
                if (single != null) {
                    calls.add(single);
                    return calls;
                }
            }
        }

        // Fallback: try array even if bracket wasn't first
        if (firstBracket >= 0) {
            int bracketEnd = findMatchingBracket(rawOutput, firstBracket);
            if (bracketEnd > firstBracket) {
                calls.addAll(parseToolCallArray(rawOutput, firstBracket, bracketEnd));
            }
        }

        return calls;
    }

    private List<ToolCall> parseToolCallArray(String rawOutput, int bracketStart, int bracketEnd) {
        List<ToolCall> calls = new ArrayList<>();
        String arrayContent = rawOutput.substring(bracketStart + 1, bracketEnd);
        int depth = 0;
        int segStart = -1;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < arrayContent.length(); i++) {
            char c = arrayContent.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (!inString) {
                if (c == '{') {
                    if (depth == 0) segStart = i;
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0 && segStart >= 0) {
                        String segment = arrayContent.substring(segStart, i + 1);
                        ToolCall tc = parseGenericToolCall(segment);
                        if (tc != null) calls.add(tc);
                        segStart = -1;
                    }
                }
            }
        }
        return calls;
    }

    private ToolCall parseGenericToolCall(String json) {
        // Minimal JSON key-value extraction for tool name
        String toolName = extractJsonStringValue(json, "tool");
        if (toolName == null) {
            toolName = extractJsonStringValue(json, "action");
        }
        if (toolName == null) {
            toolName = extractJsonStringValue(json, "function");
        }
        if (toolName == null) return null;

        String args = extractJsonObjectValue(json, "args");
        if (args == null) {
            args = extractJsonObjectValue(json, "arguments");
        }
        if (args == null) {
            args = extractJsonObjectValue(json, "parameters");
        }
        if (args == null) {
            args = "{}";
        }

        return new ToolCall(toolName, args);
    }

    // ========================================================================
    // Internal: Text Stripping
    // ========================================================================

    private String stripToolCalls(String rawOutput, OutputFormat format) {
        // Remove known tool call structures from the output, leaving only
        // the natural language response text
        return switch (format) {
            case OPENAI -> stripOpenAiToolCalls(rawOutput);
            case ANTHROPIC -> stripAnthropicToolCalls(rawOutput);
            case GENERIC_JSON -> stripGenericJsonToolCalls(rawOutput);
        };
    }

    private String stripOpenAiToolCalls(String rawOutput) {
        int idx = rawOutput.indexOf("\"tool_calls\"");
        if (idx < 0) return rawOutput;
        // Find the end of the tool_calls array
        int bracketStart = rawOutput.indexOf('[', idx);
        if (bracketStart < 0) return rawOutput;
        int bracketEnd = findMatchingBracket(rawOutput, bracketStart);
        if (bracketEnd < 0) return rawOutput.substring(0, idx).trim();
        // Concatenate text before and after the tool_calls block
        String before = rawOutput.substring(0, idx).trim();
        if (before.endsWith(",")) {
            before = before.substring(0, before.length() - 1).trim();
        }
        String after = rawOutput.substring(bracketEnd + 1).trim();
        // Remove leading comma in "after" if present (from enclosing object)
        if (after.startsWith(",")) {
            after = after.substring(1).trim();
        }
        // Remove trailing object/brace artifacts
        if (after.endsWith("}")) {
            int objEnd = after.lastIndexOf('}');
            after = after.substring(0, objEnd).trim();
            if (after.endsWith(",")) {
                after = after.substring(0, after.length() - 1).trim();
            }
        }
        return (before + " " + after).trim();
    }

    private String stripAnthropicToolCalls(String rawOutput) {
        return rawOutput.replaceAll("<invoke[^>]*>.*?</invoke>", "").trim();
    }

    private String stripGenericJsonToolCalls(String rawOutput) {
        // If the entire output is a JSON tool call object, return empty
        int braceStart = rawOutput.indexOf('{');
        if (braceStart < 0) return rawOutput;
        int braceEnd = findMatchingBrace(rawOutput, braceStart);
        if (braceEnd < 0) return rawOutput;

        String before = rawOutput.substring(0, braceStart).trim();
        String after = rawOutput.substring(braceEnd + 1).trim();
        return (before + " " + after).trim();
    }

    // ========================================================================
    // Internal: Validation
    // ========================================================================

    private void validateToolCalls(List<ToolCall> toolCalls, OutputConstraint constraint) {
        Iterator<ToolCall> it = toolCalls.iterator();
        while (it.hasNext()) {
            ToolCall tc = it.next();
            if (!tc.getToolName().equals(constraint.getToolName())) {
                log.warn("Tool call '{}' does not match expected tool '{}'; removing",
                        tc.getToolName(), constraint.getToolName());
                it.remove();
            }
            // TODO: validate tool call arguments against constraint.getArgsSchema()
            // once the schema is non-empty. Currently only name-matching is enforced.
        }
    }

    // ========================================================================
    // Internal: JSON Helpers
    // ========================================================================

    private static int findMatchingBrace(String s, int openIdx) {
        if (openIdx < 0 || s.charAt(openIdx) != '{') return -1;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (!inString) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    private static int findMatchingBracket(String s, int openIdx) {
        if (openIdx < 0 || s.charAt(openIdx) != '[') return -1;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (!inString) {
                if (c == '[') depth++;
                else if (c == ']') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    private static String extractJsonStringValue(String json, String key) {
        // Look for "key": "value"
        String pattern = "\"" + key + "\"";
        int keyIdx = json.indexOf(pattern);
        if (keyIdx < 0) {
            pattern = "'" + key + "'";
            keyIdx = json.indexOf(pattern);
        }
        if (keyIdx < 0) return null;

        int colonIdx = json.indexOf(':', keyIdx + pattern.length());
        if (colonIdx < 0) return null;

        int contentIdx = colonIdx + 1;
        while (contentIdx < json.length() && Character.isWhitespace(json.charAt(contentIdx))) {
            contentIdx++;
        }
        if (contentIdx >= json.length()) return null;

        char quote = json.charAt(contentIdx);
        if (quote != '"' && quote != '\'') return null;

        int endIdx = json.indexOf(quote, contentIdx + 1);
        if (endIdx < 0) return null;

        return json.substring(contentIdx + 1, endIdx);
    }

    private static String extractJsonObjectValue(String json, String key) {
        String pattern = "\"" + key + "\"";
        int keyIdx = json.indexOf(pattern);
        if (keyIdx < 0) {
            pattern = "'" + key + "'";
            keyIdx = json.indexOf(pattern);
        }
        if (keyIdx < 0) return null;

        int colonIdx = json.indexOf(':', keyIdx + pattern.length());
        if (colonIdx < 0) return null;

        int contentIdx = colonIdx + 1;
        while (contentIdx < json.length() && Character.isWhitespace(json.charAt(contentIdx))) {
            contentIdx++;
        }
        if (contentIdx >= json.length() || json.charAt(contentIdx) != '{') return null;

        int endIdx = findMatchingBrace(json, contentIdx);
        if (endIdx < 0) return null;

        return json.substring(contentIdx, endIdx + 1);
    }

    // ========================================================================
    // Inner Types
    // ========================================================================

    /**
     * Supported structured output formats.
     */
    public enum OutputFormat {
        /**
         * OpenAI-compatible format: tool_calls array in response delta.
         */
        OPENAI,

        /**
         * Anthropic format: function_call XML blocks.
         */
        ANTHROPIC,

        /**
         * Generic JSON format: top-level JSON object with tool/action field.
         */
        GENERIC_JSON
    }

    /**
     * A parsed structured output from an LLM.
     * <p>
     * Contains the natural language text (if any) and a list of tool calls
     * extracted from the raw completion.
     */
    public static class StructuredOutput {

        private final String text;
        private final List<ToolCall> toolCalls;
        private final OutputFormat sourceFormat;

        StructuredOutput(String text, List<ToolCall> toolCalls, OutputFormat sourceFormat) {
            this.text = text;
            this.toolCalls = Collections.unmodifiableList(toolCalls);
            this.sourceFormat = sourceFormat;
        }

        public static StructuredOutput empty() {
            return new StructuredOutput("", List.of(), OutputFormat.OPENAI);
        }

        public boolean hasToolCalls() {
            return !toolCalls.isEmpty();
        }

        public String getText() {
            return text;
        }

        public List<ToolCall> getToolCalls() {
            return toolCalls;
        }

        public OutputFormat getSourceFormat() {
            return sourceFormat;
        }

        /**
         * Convenience: get the first tool call, if any.
         */
        public Optional<ToolCall> firstToolCall() {
            return toolCalls.isEmpty() ? Optional.empty() : Optional.of(toolCalls.get(0));
        }
    }

    /**
     * A single tool call extracted from LLM output.
     */
    public static class ToolCall {

        private final String toolName;
        private final String argumentsJson;

        public ToolCall(String toolName, String argumentsJson) {
            this.toolName = Objects.requireNonNull(toolName);
            this.argumentsJson = argumentsJson != null ? argumentsJson : "{}";
        }

        public String getToolName() {
            return toolName;
        }

        public String getArgumentsJson() {
            return argumentsJson;
        }
    }

    /**
     * An output constraint that guides structured output parsing.
     * <p>
     * Associates a session with an expected tool and optional schema
     * for its arguments. Used for constrained decoding and validation.
     */
    public static class OutputConstraint {

        private final String toolName;
        private final JsonSchema argsSchema;
        private final boolean requireExactMatch;

        public OutputConstraint(String toolName, JsonSchema argsSchema, boolean requireExactMatch) {
            this.toolName = Objects.requireNonNull(toolName);
            this.argsSchema = argsSchema;
            this.requireExactMatch = requireExactMatch;
        }

        public OutputConstraint(String toolName) {
            this(toolName, null, false);
        }

        public String getToolName() {
            return toolName;
        }

        public Optional<JsonSchema> getArgsSchema() {
            return Optional.ofNullable(argsSchema);
        }

        public boolean isRequireExactMatch() {
            return requireExactMatch;
        }
    }
}
