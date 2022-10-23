package com.deac.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AppContext {

    @Bean(name = "databaseExecutor")
    public Executor taskExecutorDatabase() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(Runtime.getRuntime().availableProcessors());
        executor.setQueueCapacity(1);
        executor.setRejectedExecutionHandler(new RejectedExecutionHandlerImpl());
        executor.initialize();
        executor.setThreadNamePrefix("database-task-executor");
        return executor;
    }

    @Bean(name = "mailExecutor")
    public Executor taskExecutorMail() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(30);
        executor.setQueueCapacity(1);
        executor.setRejectedExecutionHandler(new RejectedExecutionHandlerImpl());
        executor.initialize();
        executor.setThreadNamePrefix("mail-task-executor");
        return executor;
    }

}
