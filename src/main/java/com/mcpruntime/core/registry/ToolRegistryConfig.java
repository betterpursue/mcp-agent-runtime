package com.mcpruntime.core.registry;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolRegistryConfig {

    @Bean
    public ToolRegistry toolRegistry() {
        return new ToolRegistry();
    }
}
