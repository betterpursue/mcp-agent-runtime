package com.mcpruntime.core.registry;

import com.mcpruntime.core.registry.exception.ToolExecutionException;

import java.util.List;

public class InterceptorChain {

    private final List<ToolInterceptor> interceptors;

    public InterceptorChain(List<ToolInterceptor> interceptors) {
        this.interceptors = interceptors;
    }

    public void beforeExecute(ToolExecutionContext ctx) {
        for (ToolInterceptor interceptor : interceptors) {
            interceptor.beforeExecute(ctx);
        }
    }

    public void afterExecute(ToolExecutionContext ctx, ToolExecutionResult result) {
        for (int i = interceptors.size() - 1; i >= 0; i--) {
            interceptors.get(i).afterExecute(ctx, result);
        }
    }

    public void onError(ToolExecutionContext ctx, Exception e) {
        for (int i = interceptors.size() - 1; i >= 0; i--) {
            interceptors.get(i).onError(ctx, e);
        }
    }
}
