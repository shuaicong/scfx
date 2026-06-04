package com.scfx.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 默认线程池与定时任务配置。
 * <p>
 * 命名 executor（chunkExecutor / vectorEmbedExecutor / vizExecutor）
 * 定义在 {@link AsyncConfig} 中。
 */
@Configuration
@EnableScheduling
public class ThreadPoolConfig {

    /** 默认异步执行器（供 @Async 无 qualifier 使用） */
    @Bean
    @Primary
    public Executor defaultAsyncExecutor() {
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(4);
        e.setMaxPoolSize(8);
        e.setQueueCapacity(200);
        e.setThreadNamePrefix("async-");
        e.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        e.initialize();
        return e;
    }

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
}
