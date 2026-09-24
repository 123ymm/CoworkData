package com.huawei.coworkdata.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 导出任务线程池：最多同时跑 2 个，队列为 0（忙时直接拒绝）。
 */
@Configuration
@EnableAsync
@EnableScheduling
public class ExportAsyncConfig {

    public static final String EXPORT_EXECUTOR = "exportExecutor";

    @Bean(name = EXPORT_EXECUTOR)
    public TaskExecutor exportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("db-export-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
