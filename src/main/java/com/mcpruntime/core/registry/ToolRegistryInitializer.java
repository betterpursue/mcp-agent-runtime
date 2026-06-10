package com.mcpruntime.core.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ToolRegistryInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistryInitializer.class);

    private final ToolRegistry registry;
    private final List<ToolDefinitionProvider> providers;

    public ToolRegistryInitializer(ToolRegistry registry,
                                   List<ToolDefinitionProvider> providers) {
        this.registry = registry;
        this.providers = providers;
    }

    @Override
    public void run(ApplicationArguments args) {
        providers.stream()
            .flatMap(p -> p.provide().stream())
            .forEach(registry::register);

        if (log.isInfoEnabled()) {
            log.info("Registered {} tools from {} providers",
                registry.size(), providers.size());
        }
    }
}
