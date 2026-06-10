package com.mcpruntime.mcp;

import com.mcpruntime.core.registry.ToolDefinition;
import com.mcpruntime.core.registry.ToolExecutionContext;
import com.mcpruntime.core.registry.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WeatherToolProviderTest {

    private final WeatherToolProvider provider = new WeatherToolProvider();

    @Test
    void shouldProvideTwoTools() {
        List<ToolDefinition> tools = provider.provide();
        assertEquals(2, tools.size());
    }

    @Test
    void shouldProvideWeatherTool() {
        List<ToolDefinition> tools = provider.provide();

        ToolDefinition weatherTool = tools.stream()
            .filter(t -> "get_weather".equals(t.getName()))
            .findFirst()
            .orElseThrow();

        assertEquals("get_weather", weatherTool.getName());
        assertEquals(1, weatherTool.getInterceptors().size());
    }

    @Test
    void shouldProvideCalculateTool() {
        List<ToolDefinition> tools = provider.provide();

        ToolDefinition calcTool = tools.stream()
            .filter(t -> "calculate".equals(t.getName()))
            .findFirst()
            .orElseThrow();

        assertEquals("calculate", calcTool.getName());
        assertEquals(0, calcTool.getInterceptors().size());
    }

    @Test
    void weatherToolShouldReturnWeatherData() throws Exception {
        List<ToolDefinition> tools = provider.provide();
        ToolDefinition weatherTool = tools.stream()
            .filter(t -> "get_weather".equals(t.getName()))
            .findFirst()
            .orElseThrow();

        ToolExecutionContext ctx = ToolExecutionContext.builder()
            .sessionId("test")
            .traceId("test")
            .args(Map.of("city", "北京"))
            .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>)
            weatherTool.getExecutor().execute(ctx);

        assertEquals("北京", result.get("city"));
        assertEquals(22, result.get("temperature"));
        assertEquals("celsius", result.get("unit"));
    }

    @Test
    void weatherToolShouldReturnFahrenheit() throws Exception {
        List<ToolDefinition> tools = provider.provide();
        ToolDefinition weatherTool = tools.stream()
            .filter(t -> "get_weather".equals(t.getName()))
            .findFirst()
            .orElseThrow();

        ToolExecutionContext ctx = ToolExecutionContext.builder()
            .sessionId("test")
            .traceId("test")
            .args(Map.of("city", "上海", "unit", "fahrenheit"))
            .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>)
            weatherTool.getExecutor().execute(ctx);

        assertEquals("上海", result.get("city"));
        assertEquals(72, result.get("temperature"));
        assertEquals("fahrenheit", result.get("unit"));
    }

    @Test
    void calculateToolShouldEvaluateExpression() throws Exception {
        List<ToolDefinition> tools = provider.provide();
        ToolDefinition calcTool = tools.stream()
            .filter(t -> "calculate".equals(t.getName()))
            .findFirst()
            .orElseThrow();

        ToolExecutionContext ctx = ToolExecutionContext.builder()
            .sessionId("test")
            .traceId("test")
            .args(Map.of("expression", "3 + 5"))
            .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>)
            calcTool.getExecutor().execute(ctx);

        assertEquals("3 + 5", result.get("expression"));
        assertEquals(8.0, result.get("result"));
    }

    @Test
    void calculateToolShouldHandleSubtraction() throws Exception {
        List<ToolDefinition> tools = provider.provide();
        ToolDefinition calcTool = tools.stream()
            .filter(t -> "calculate".equals(t.getName()))
            .findFirst()
            .orElseThrow();

        ToolExecutionContext ctx = ToolExecutionContext.builder()
            .sessionId("test")
            .traceId("test")
            .args(Map.of("expression", "10 - 3"))
            .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>)
            calcTool.getExecutor().execute(ctx);

        assertEquals(7.0, result.get("result"));
    }

    @Test
    void calculateToolShouldHandleMultiplication() throws Exception {
        List<ToolDefinition> tools = provider.provide();
        ToolDefinition calcTool = tools.stream()
            .filter(t -> "calculate".equals(t.getName()))
            .findFirst()
            .orElseThrow();

        ToolExecutionContext ctx = ToolExecutionContext.builder()
            .sessionId("test")
            .traceId("test")
            .args(Map.of("expression", "4 * 3"))
            .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>)
            calcTool.getExecutor().execute(ctx);

        assertEquals(12.0, result.get("result"));
    }

    @Test
    void calculateToolShouldHandleDivision() throws Exception {
        List<ToolDefinition> tools = provider.provide();
        ToolDefinition calcTool = tools.stream()
            .filter(t -> "calculate".equals(t.getName()))
            .findFirst()
            .orElseThrow();

        ToolExecutionContext ctx = ToolExecutionContext.builder()
            .sessionId("test")
            .traceId("test")
            .args(Map.of("expression", "10 / 2"))
            .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>)
            calcTool.getExecutor().execute(ctx);

        assertEquals(5.0, result.get("result"));
    }

    @Test
    void shouldRegisterToRegistryCorrectly() {
        ToolRegistry registry = new ToolRegistry();
        registry.registerAll(provider.provide());

        assertEquals(2, registry.size());
        assertTrue(registry.contains("get_weather"));
        assertTrue(registry.contains("calculate"));
    }
}
