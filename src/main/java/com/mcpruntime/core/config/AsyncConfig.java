package com.mcpruntime.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Enables Spring async processing and provides a dedicated thread pool
 * for long-running Tool executions dispatched by {@code WorkerAgent}.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("toolTaskExecutor")
    public Executor toolTaskExecutor(RuntimeConfig runtimeConfig) {
        int coreSize = runtimeConfig.get(
            RuntimeConfig.ConfigKeys.THREAD_POOL_CORE_SIZE, Integer.class, 4);
        int maxSize = runtimeConfig.get(
            RuntimeConfig.ConfigKeys.THREAD_POOL_MAX_SIZE, Integer.class, 8);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("tool-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
