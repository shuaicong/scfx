package com.scfx.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
public class AsyncConfig {

    @Value("${async.pool.core-size:2}")
    private int corePoolSize;

    @Value("${async.pool.max-size:5}")
    private int maxPoolSize;

    @Value("${async.pool.queue-capacity:50}")
    private int queueCapacity;

    /** Visualization (DashScope) 异步执行器，与检索隔离 */
    @Bean(name = "vizExecutor")
    public Executor vizExecutor() {
        return createExecutor("viz-exec-");
    }

    private ThreadPoolTaskExecutor createExecutor(String threadPrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadPrefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("Async pool: prefix={}, core={}, max={}, queue={}",
            threadPrefix, corePoolSize, maxPoolSize, queueCapacity);
        return executor;
    }
}
