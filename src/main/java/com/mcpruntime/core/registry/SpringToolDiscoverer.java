package com.mcpruntime.core.registry;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SpringToolDiscoverer {

    private final ApplicationContext applicationContext;

    public SpringToolDiscoverer(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public List<ToolDefinition> discover() {
        return applicationContext.getBeansOfType(ToolDefinition.class)
            .values()
            .stream()
            .toList();
    }

    public void autoRegister(ToolRegistry registry) {
        discover().forEach(registry::register);
    }
}
