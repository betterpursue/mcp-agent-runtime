package com.mcpruntime;

import com.mcpruntime.runtime.AgentRuntime;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class McpAgentRuntimeApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpAgentRuntimeApplication.class, args);
    }

    @Bean
    public AgentRuntime agentRuntime() {
        return new AgentRuntime();
    }
}
