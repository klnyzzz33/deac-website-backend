package com.deac.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AppContext extends WebMvcConfigurationSupport {

    @Bean
    public Executor taskExecutor() {
        return new SimpleAsyncTaskExecutor();
    }

}
