package com.mcpruntime.core.registry;

public interface ToolInterceptor {

    default void beforeExecute(ToolExecutionContext ctx) {}

    default void afterExecute(ToolExecutionContext ctx, ToolExecutionResult result) {}

    default void onError(ToolExecutionContext ctx, Exception e) {}
}
