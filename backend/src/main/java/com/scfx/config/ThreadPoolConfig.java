package com.scfx.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置
 * <p>
 * 文档解析 → 切片 → 向量化，各阶段物理隔离互不阻塞
 */
@Configuration
@EnableAsync
@EnableScheduling
public class ThreadPoolConfig {

    /** 文档解析线程池（IO + CPU 混合） */
    @Bean("uploadParseExecutor")
    public Executor uploadParseExecutor() {
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(2);
        e.setMaxPoolSize(4);
        e.setQueueCapacity(100);
        e.setThreadNamePrefix("parse-");
        e.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        e.initialize();
        return e;
    }

    /** 切片入库线程池（DB IO） */
    @Bean("chunkExecutor")
    public Executor chunkExecutor() {
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(2);
        e.setMaxPoolSize(4);
        e.setQueueCapacity(200);
        e.setThreadNamePrefix("chunk-");
        e.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        e.initialize();
        return e;
    }

    /** BGE-M3 向量化线程池（IO 密集型，控制并发数适配 API QPS） */
    @Bean("vectorEmbedExecutor")
    public Executor vectorEmbedExecutor() {
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(3);
        e.setMaxPoolSize(5);
        e.setQueueCapacity(200);
        e.setThreadNamePrefix("embed-");
        e.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        e.initialize();
        return e;
    }
}
