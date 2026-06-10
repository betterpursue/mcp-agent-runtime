package com.mcpruntime.mcp;

import com.mcpruntime.core.registry.*;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.ServletRegistration;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.ServiceLoader;

@Configuration
public class McpServerConfig {

    private final ToolRegistry toolRegistry;

    public McpServerConfig(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @Bean
    public McpJsonMapper mcpJsonMapper() {
        return ServiceLoader.load(McpJsonMapper.class)
            .findFirst()
            .orElseThrow(() -> new RuntimeException(
                "No McpJsonMapper implementation found on classpath"));
    }

    @Bean
    public HttpServletStreamableServerTransportProvider transportProvider(McpJsonMapper jsonMapper) {
        return HttpServletStreamableServerTransportProvider.builder()
            .jsonMapper(jsonMapper)
            .mcpEndpoint("/mcp")
            .build();
    }

    @Bean
    public ServletRegistrationBean<?> mcpServlet(
            HttpServletStreamableServerTransportProvider transportProvider) {
        return new ServletRegistrationBean<>(transportProvider);
    }

    @Bean
    public McpSyncServer mcpServer(
            HttpServletStreamableServerTransportProvider transportProvider) {
        return McpServer.sync(transportProvider)
            .serverInfo("mcp-agent-runtime", "0.0.1")
            .capabilities(McpSchema.ServerCapabilities.builder()
                .tools(true)
                .logging()
                .build())
            .build();
    }
}
