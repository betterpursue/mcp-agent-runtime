package com.mcpruntime.core.registry;

import java.util.Map;

@FunctionalInterface
public interface ToolExecutor {
    Object execute(ToolExecutionContext ctx) throws Exception;
}
