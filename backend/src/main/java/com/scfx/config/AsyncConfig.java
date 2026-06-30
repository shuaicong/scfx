package com.scfx.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步执行器配置。
 * <p>
 * 所有命名 executor 定义在此类中，供 @Async("name") 引用。
 * @EnableAsync 放在此处确保 executor bean 在 AOP 基础设施之前注册。
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    private static final int CORE = 2;
    private static final int MAX = 4;
    private static final int QUEUE = 200;

    /** 切片入库线程池（DB IO） */
    @Bean("chunkExecutor")
    public Executor chunkExecutor() {
        return createExecutor("chunk-", CORE, MAX, QUEUE, new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /** BGE-M3 向量化线程池（IO 密集型，控制并发数适配 API QPS） */
    @Bean("vectorEmbedExecutor")
    public Executor vectorEmbedExecutor() {
        return createExecutor("embed-", 3, 5, 200, new ThreadPoolExecutor.AbortPolicy());
    }

    /** Visualization (DashScope) 异步执行器 */
    @Bean("vizExecutor")
    public Executor vizExecutor() {
        return createExecutor("viz-exec-", 2, 5, 50, new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /** 报告生成异步执行器（AI 生成、Gotenberg 导出等耗时任务） */
    @Bean("reportExecutor")
    public Executor reportExecutor() {
        return createExecutor("report-", 2, 4, 100, new ThreadPoolExecutor.CallerRunsPolicy());
    }

    private static ThreadPoolTaskExecutor createExecutor(
            String prefix, int core, int max, int queue,
            java.util.concurrent.RejectedExecutionHandler handler) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queue);
        executor.setThreadNamePrefix(prefix);
        executor.setRejectedExecutionHandler(handler);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("Async pool: prefix={}, core={}, max={}, queue={}",
            prefix, core, max, queue);
        return executor;
    }
}
