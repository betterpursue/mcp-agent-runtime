package com.mcpruntime.mcp;

import com.mcpruntime.core.registry.*;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class McpToolBridge implements ToolRegistry.ToolRegistryListener {

    private static final Logger log = LoggerFactory.getLogger(McpToolBridge.class);

    private final ToolRegistry toolRegistry;
    private final ToolExecutorRouter executorRouter;
    private final McpSyncServer mcpServer;

    public McpToolBridge(ToolRegistry toolRegistry,
                         ToolExecutorRouter executorRouter,
                         McpSyncServer mcpServer) {
        this.toolRegistry = toolRegistry;
        this.executorRouter = executorRouter;
        this.mcpServer = mcpServer;

        this.toolRegistry.addListener(this);
        registerExistingTools();
    }

    private void registerExistingTools() {
        for (ToolDefinition def : toolRegistry.list()) {
            McpServerFeatures.SyncToolSpecification spec = buildSpecification(def);
            mcpServer.addTool(spec);
            log.info("Registered MCP tool: {}", def.getName());
        }
    }

    @Override
    public void onToolRegistered(ToolDefinition tool) {
        McpServerFeatures.SyncToolSpecification spec = buildSpecification(tool);
        mcpServer.addTool(spec);
        log.info("Registered MCP tool (live): {}", tool.getName());
    }

    private McpServerFeatures.SyncToolSpecification buildSpecification(ToolDefinition def) {
        McpSchema.Tool mcpTool = McpSchema.Tool.builder()
            .name(def.getName())
            .description(def.getDescription())
            .inputSchema(def.getInputSchema().toMap())
            .build();

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(mcpTool)
            .callHandler((exchange, request) -> {
                Map<String, Object> args = request.arguments() != null
                    ? request.arguments()
                    : Map.of();

                ToolExecutionContext ctx = ToolExecutionContext.builder()
                    .sessionId(exchange.sessionId())
                    .traceId(java.util.UUID.randomUUID().toString())
                    .callId("mcp-call")
                    .args(args)
                    .build();

                ToolExecutionResult result = executorRouter.execute(def, ctx);

                if (result.isSuccess()) {
                    String content = result.getResult() != null
                        ? result.getResult().toString()
                        : "";
                    return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(content)))
                        .build();
                } else {
                    return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(
                            "Error: " + result.getErrorMessage())))
                        .isError(true)
                        .build();
                }
            })
            .build();
    }
}
