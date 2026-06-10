package com.mcpruntime.mcp;

import com.mcpruntime.core.registry.ToolDefinition;
import com.mcpruntime.core.registry.ToolDefinitionProvider;
import com.mcpruntime.core.registry.ToolInterceptor;
import com.mcpruntime.core.registry.ToolMetadata;
import com.mcpruntime.core.registry.ToolExecutionContext;
import com.mcpruntime.core.registry.ToolExecutionResult;
import com.mcpruntime.core.schema.JsonSchema;
import com.mcpruntime.core.schema.SchemaProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class WeatherToolProvider implements ToolDefinitionProvider {

    @Override
    public List<ToolDefinition> provide() {
        return List.of(
            ToolDefinition.builder()
                .name("get_weather")
                .description("获取指定城市的实时天气信息")
                .parameterSchema(JsonSchema.builder()
                    .addProperty("city", SchemaProperty.STRING
                        .withDescription("城市名，中文，如 北京、上海")
                        .withEnum("北京", "上海", "广州", "深圳"))
                    .addProperty("unit", SchemaProperty.STRING
                        .withDefault("celsius")
                        .withEnum("celsius", "fahrenheit"))
                    .addRequired("city")
                    .build())
                .executor(ctx -> {
                    String city = ctx.getArg("city");
                    String unit = ctx.getArgOrDefault("unit", "celsius");
                    return Map.of(
                        "city", city,
                        "temperature", unit.equals("celsius") ? 22 : 72,
                        "unit", unit,
                        "condition", "晴",
                        "humidity", 45
                    );
                })
                .withInterceptor(new LoggingInterceptor())
                .metadata(ToolMetadata.builder()
                    .timeout(Duration.ofSeconds(10))
                    .owner("weather-team")
                    .version("1.0.0")
                    .build())
                .build(),

            ToolDefinition.builder()
                .name("calculate")
                .description("执行数学计算")
                .parameterSchema(JsonSchema.builder()
                    .addProperty("expression", SchemaProperty.STRING
                        .withDescription("数学表达式，如 1 + 2 * 3"))
                    .addRequired("expression")
                    .build())
                .executor(ctx -> {
                    String expr = ctx.getArg("expression");
                    return Map.of(
                        "expression", expr,
                        "result", evaluateSimple(expr)
                    );
                })
                .build()
        );
    }

    private static Number evaluateSimple(String expr) {
        String trimmed = expr.replaceAll("\\s+", "");
        for (String op : List.of("+", "-", "*", "/")) {
            int idx = trimmed.lastIndexOf(op);
            if (idx > 0) {
                double left = Double.parseDouble(trimmed.substring(0, idx));
                double right = Double.parseDouble(trimmed.substring(idx + 1));
                return switch (op) {
                    case "+" -> left + right;
                    case "-" -> left - right;
                    case "*" -> left * right;
                    case "/" -> right != 0 ? left / right : Double.NaN;
                    default -> 0;
                };
            }
        }
        return Double.parseDouble(trimmed);
    }

    public static class LoggingInterceptor implements ToolInterceptor {

        private static final Logger log = LoggerFactory.getLogger(LoggingInterceptor.class);

        @Override
        public void beforeExecute(ToolExecutionContext ctx) {
            log.info("Executing tool: args={}", ctx.getArgs());
        }

        @Override
        public void afterExecute(ToolExecutionContext ctx, ToolExecutionResult result) {
            log.info("Tool executed in {}ms: success={}",
                result.getElapsedNanos() / 1_000_000, result.isSuccess());
        }
    }
}
