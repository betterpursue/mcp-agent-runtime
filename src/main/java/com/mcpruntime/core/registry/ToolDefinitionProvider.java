package com.mcpruntime.core.registry;

import java.util.List;

@FunctionalInterface
public interface ToolDefinitionProvider {
    List<ToolDefinition> provide();
}
