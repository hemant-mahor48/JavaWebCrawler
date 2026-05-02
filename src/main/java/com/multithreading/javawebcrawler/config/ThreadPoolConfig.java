package com.multithreading.javawebcrawler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class ThreadPoolConfig {

    @Bean(name = "crawlerExecutor")
    public Executor crawlerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(5);        // 5 threads always ready
        executor.setMaxPoolSize(20);        // burst up to 20 threads
        executor.setQueueCapacity(100);     // 100 URLs can wait in line
        executor.setThreadNamePrefix("crawler-thread-"); // visible in logs
        executor.setKeepAliveSeconds(60);   // idle threads die after 60s
        executor.initialize();

        return executor;
    }
}
