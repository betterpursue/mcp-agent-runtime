package com.mcpruntime.core.structured;

import com.mcpruntime.core.schema.JsonSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for {@link StructuredOutputAdapter}.
 * <p>
 * Covers: format-specific parsing (OpenAI, Anthropic, Generic JSON),
 * constraint enforcement, constrained decoding prefix generation,
 * and edge cases (null, empty, malformed input).
 */
class StructuredOutputAdapterTest {

    private StructuredOutputAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new StructuredOutputAdapter();
    }

    // ========================================================================
    // OpenAI-formatted output
    // ========================================================================

    @Test
    void shouldParseOpenAiToolCall() {
        String raw = "Let me check the weather for you.\n" +
                "{\"tool_calls\": [{\"function\": {\"name\": \"get_weather\", " +
                "\"arguments\": \"{\\\"city\\\": \\\"Beijing\\\"}\"}}]}";

        StructuredOutputAdapter.StructuredOutput output =
                adapter.parse("s1", raw, StructuredOutputAdapter.OutputFormat.OPENAI);

        assertTrue(output.hasToolCalls());
        assertEquals(1, output.getToolCalls().size());
        assertEquals("get_weather", output.getToolCalls().get(0).getToolName());
        assertTrue(output.getText().contains("Let me check"));
    }

    @Test
    void shouldParseOpenAiWithoutToolCalls() {
        String raw = "Hello, how can I help you today?";

        StructuredOutputAdapter.StructuredOutput output =
                adapter.parse("s1", raw, StructuredOutputAdapter.OutputFormat.OPENAI);

        assertFalse(output.hasToolCalls());
        assertEquals("Hello, how can I help you today?", output.getText().trim());
    }

    @Test
    void shouldHandleNullOpenAiOutput() {
        StructuredOutputAdapter.StructuredOutput output =
                adapter.parse("s1", null, StructuredOutputAdapter.OutputFormat.OPENAI);

        assertFalse(output.hasToolCalls());
        assertTrue(output.getText().isEmpty());
    }

    @Test
    void shouldHandleBlankOpenAiOutput() {
        StructuredOutputAdapter.StructuredOutput output =
                adapter.parse("s1", "   ", StructuredOutputAdapter.OutputFormat.OPENAI);

        assertFalse(output.hasToolCalls());
        assertTrue(output.getText().isEmpty());
    }

    @Test
    void shouldExtractMultipleOpenAiToolCalls() {
        String raw = "I'll do both.\n" +
                "{\"tool_calls\": [" +
                "{\"function\": {\"name\": \"get_weather\", \"arguments\": \"{\\\"city\\\": \\\"BJ\\\"}\"}}," +
                "{\"function\": {\"name\": \"search_news\", \"arguments\": \"{\\\"topic\\\": \\\"AI\\\"}\"}}" +
                "]}";

        StructuredOutputAdapter.StructuredOutput output =
                adapter.parse("s1", raw, StructuredOutputAdapter.OutputFormat.OPENAI);

        assertEquals(2, output.getToolCalls().size());
        assertEquals("get_weather", output.getToolCalls().get(0).getToolName());
        assertEquals("search_news", output.getToolCalls().get(1).getToolName());
    }

    // ========================================================================
    // Anthropic-formatted output
    // ========================================================================

    @Test
    void shouldParseAnthropicToolCall() {
        String raw = "Let me check the weather.\n" +
                "<function_calls>\n" +
                "<invoke name=\"get_weather\">\n" +
                "<parameter name=\"city\">Beijing</parameter>\n" +
                "</invoke>\n" +
                "</function_calls>";

        StructuredOutputAdapter.StructuredOutput output =
                adapter.parse("s1", raw, StructuredOutputAdapter.OutputFormat.ANTHROPIC);

        assertTrue(output.hasToolCalls());
        assertEquals(1, output.getToolCalls().size());
        assertEquals("get_weather", output.getToolCalls().get(0).getToolName());
    }

    @Test
    void shouldParseAnthropicWithMultipleInvokes() {
        String raw = "<function_calls>\n" +
                "<invoke name=\"search\">content</invoke>\n" +
                "<invoke name=\"translate\">content</invoke>\n" +
                "</function_calls>";

        StructuredOutputAdapter.StructuredOutput output =
                adapter.parse("s1", raw, StructuredOutputAdapter.OutputFormat.ANTHROPIC);

        assertEquals(2, output.getToolCalls().size());
        assertEquals("search", output.getToolCalls().get(0).getToolName());
        assertEquals("translate", output.getToolCalls().get(1).getToolName());
    }

    // ========================================================================
    // Generic JSON format
    // ========================================================================

    @Test
    void shouldParseGenericJsonToolCall() {
        String raw = "{\"tool\": \"get_weather\", \"args\": {\"city\": \"Beijing\"}}";

        StructuredOutputAdapter.StructuredOutput output =
                adapter.parse("s1", raw, StructuredOutputAdapter.OutputFormat.GENERIC_JSON);

        assertTrue(output.hasToolCalls());
        assertEquals(1, output.getToolCalls().size());
        assertEquals("get_weather", output.getToolCalls().get(0).getToolName());
    }

    @Test
    void shouldParseGenericJsonWithActionField() {
        String raw = "{\"action\": \"search\", \"args\": {\"query\": \"MCP\"}}";

        StructuredOutputAdapter.StructuredOutput output =
                adapter.parse("s1", raw, StructuredOutputAdapter.OutputFormat.GENERIC_JSON);

        assertTrue(output.hasToolCalls());
        assertEquals("search", output.getToolCalls().get(0).getToolName());
    }

    @Test
    void shouldParseGenericJsonWithFunctionField() {
        String raw = "{\"function\": \"translate\", \"args\": {\"text\": \"hello\"}}";

        StructuredOutputAdapter.StructuredOutput output =
                adapter.parse("s1", raw, StructuredOutputAdapter.OutputFormat.GENERIC_JSON);

        assertTrue(output.hasToolCalls());
        assertEquals("translate", output.getToolCalls().get(0).getToolName());
    }

    @Test
    void shouldParseGenericJsonArray() {
        String raw = "[{\"tool\": \"a\", \"args\": {}}, {\"tool\": \"b\", \"args\": {}}]";

        StructuredOutputAdapter.StructuredOutput output =
                adapter.parse("s1", raw, StructuredOutputAdapter.OutputFormat.GENERIC_JSON);

        assertEquals(2, output.getToolCalls().size());
    }

    // ========================================================================
    // Default format
    // ========================================================================

    @Test
    void shouldUseDefaultFormat() {
        StructuredOutputAdapter defaultAdapter = new StructuredOutputAdapter(
                StructuredOutputAdapter.OutputFormat.GENERIC_JSON);

        String raw = "{\"tool\": \"search\", \"args\": {}}";
        StructuredOutputAdapter.StructuredOutput output = defaultAdapter.parse("s1", raw);

        assertTrue(output.hasToolCalls());
        assertEquals("search", output.getToolCalls().get(0).getToolName());
    }

    // ========================================================================
    // Constraint management
    // ========================================================================

    @Test
    void shouldRegisterConstraint() {
        JsonSchema schema = JsonSchema.builder()
                .addProperty("city", com.mcpruntime.core.schema.SchemaProperty.STRING)
                .build();
        StructuredOutputAdapter.OutputConstraint constraint =
                new StructuredOutputAdapter.OutputConstraint(
                        "get_weather", schema, false);

        adapter.setConstraint("session-1", constraint);

        assertTrue(adapter.hasConstraint("session-1"));
    }

    @Test
    void shouldRemoveConstraint() {
        adapter.setConstraint("session-1",
                new StructuredOutputAdapter.OutputConstraint("get_weather"));
        adapter.removeConstraint("session-1");

        assertFalse(adapter.hasConstraint("session-1"));
    }

    @Test
    void shouldFilterToolCallsByConstraint() {
        adapter.setConstraint("session-1",
                new StructuredOutputAdapter.OutputConstraint("allowed_tool", null, true));

        String raw = "{\"tool_calls\": [{\"function\": {\"name\": \"disallowed_tool\"," +
                "\"arguments\": \"{}\"}}]}";

        StructuredOutputAdapter.StructuredOutput output =
                adapter.parse("session-1", raw, StructuredOutputAdapter.OutputFormat.OPENAI);

        // The disallowed tool call should be filtered out
        assertFalse(output.hasToolCalls());
    }

    @Test
    void constraintShouldBeSessionScoped() {
        adapter.setConstraint("s1",
                new StructuredOutputAdapter.OutputConstraint("tool_a"));
        adapter.setConstraint("s2",
                new StructuredOutputAdapter.OutputConstraint("tool_b"));

        assertTrue(adapter.hasConstraint("s1"));
        assertTrue(adapter.hasConstraint("s2"));
        adapter.removeConstraint("s1");
        assertFalse(adapter.hasConstraint("s1"));
        assertTrue(adapter.hasConstraint("s2"));
    }

    @Test
    void shouldRejectNullConstraintArgs() {
        assertThrows(NullPointerException.class,
                () -> adapter.setConstraint("s1", null));
    }

    // ========================================================================
    // Constrained decoding prefix
    // ========================================================================

    @Test
    void shouldBuildConstrainedPrefix() {
        StructuredOutputAdapter.OutputConstraint constraint =
                new StructuredOutputAdapter.OutputConstraint("get_weather");

        String prefix = adapter.buildConstrainedPrefix(constraint);

        assertTrue(prefix.contains("get_weather"));
        assertTrue(prefix.contains("tool"));
    }

    @Test
    void shouldRejectNullConstraintInPrefixBuilder() {
        assertThrows(NullPointerException.class,
                () -> adapter.buildConstrainedPrefix(null));
    }

    // ========================================================================
    // Inner types
    // ========================================================================

    @Test
    void emptyOutputShouldNotHaveToolCalls() {
        StructuredOutputAdapter.StructuredOutput empty =
                StructuredOutputAdapter.StructuredOutput.empty();

        assertFalse(empty.hasToolCalls());
        assertTrue(empty.getText().isEmpty());
        assertTrue(empty.firstToolCall().isEmpty());
    }

    @Test
    void toolCallShouldStoreNameAndArgs() {
        StructuredOutputAdapter.ToolCall tc =
                new StructuredOutputAdapter.ToolCall("test", "{\"x\": 1}");

        assertEquals("test", tc.getToolName());
        assertEquals("{\"x\": 1}", tc.getArgumentsJson());
    }

    @Test
    void toolCallShouldDefaultArgsToEmptyJson() {
        StructuredOutputAdapter.ToolCall tc =
                new StructuredOutputAdapter.ToolCall("test", null);

        assertEquals("{}", tc.getArgumentsJson());
    }

    @Test
    void firstToolCallShouldReturnPresent() {
        StructuredOutputAdapter.StructuredOutput output =
                adapter.parse("s1",
                        "{\"tool\": \"search\", \"args\": {}}",
                        StructuredOutputAdapter.OutputFormat.GENERIC_JSON);

        Optional<StructuredOutputAdapter.ToolCall> first = output.firstToolCall();
        assertTrue(first.isPresent());
        assertEquals("search", first.get().getToolName());
    }

    @Test
    void outputConstraintShouldSupportSchemalessConstruction() {
        StructuredOutputAdapter.OutputConstraint c =
                new StructuredOutputAdapter.OutputConstraint("my_tool");

        assertEquals("my_tool", c.getToolName());
        assertTrue(c.getArgsSchema().isEmpty());
        assertFalse(c.isRequireExactMatch());
    }

    @Test
    void outputShouldRecordSourceFormat() {
        String raw = "{\"tool\": \"x\", \"args\": {}}";

        StructuredOutputAdapter.StructuredOutput output =
                adapter.parse("s1", raw, StructuredOutputAdapter.OutputFormat.GENERIC_JSON);

        assertEquals(StructuredOutputAdapter.OutputFormat.GENERIC_JSON,
                output.getSourceFormat());
    }
}
