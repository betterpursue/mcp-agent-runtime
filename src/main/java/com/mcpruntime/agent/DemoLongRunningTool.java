package com.mcpruntime.agent;

import com.mcpruntime.core.registry.*;
import com.mcpruntime.core.schema.JsonSchema;
import com.mcpruntime.core.schema.SchemaProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * A demo tool that simulates a long-running operation (e.g. web scraping,
 * report generation). After registration, this tool is automatically routed
 * to the async execution path because its metadata marks it as long-running.
 * <p>
 * Run with: java -jar mcp-agent-runtime.jar
 * Then call the tool via MCP and observe that {@code TaskSubmitted}
 * is returned immediately, while the actual result arrives later.
 */
@Component
public class DemoLongRunningTool implements ToolDefinitionProvider {

    private static final Logger log = LoggerFactory.getLogger(DemoLongRunningTool.class);

    @Override
    public List<ToolDefinition> provide() {
        ToolDefinition scrape = ToolDefinition.builder()
            .name("web_scrape")
            .description("Scrape a web page (simulated long-running). Returns page text. Takes ~5 seconds.")
            .parameterSchema(JsonSchema.builder()
                .addProperty("url", SchemaProperty.STRING
                    .withDescription("The URL to scrape"))
                .addRequired("url")
                .build())
            .executor(ctx -> {
                String url = ctx.getArg("url");
                log.info("[LongRunning] Simulating web scrape for URL: {}", url);
                // Simulate a 5-second operation
                Thread.sleep(5000);
                return "Content of " + url + ": [simulated page text with " + url.length() + " chars]";
            })
            .metadata(ToolMetadata.builder()
                .timeout(Duration.ofSeconds(30))
                .longRunning(true)
                .owner("demo")
                .version("1.0.0")
                .build())
            .build();

        return List.of(scrape);
    }
}
