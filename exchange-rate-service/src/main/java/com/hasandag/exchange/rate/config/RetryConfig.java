package com.hasandag.exchange.rate.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
@Slf4j
public class RetryConfig {

    @Value("${exchange.retry.scheduler.threads:2}")
    private int retrySchedulerThreads;

    @Bean("retryScheduler")
    public ScheduledExecutorService retryScheduler() {
        log.info("Creating retry scheduler with {} threads", retrySchedulerThreads);
        return Executors.newScheduledThreadPool(retrySchedulerThreads, 
            r -> {
                Thread thread = new Thread(r, "retry-scheduler-");
                thread.setDaemon(true);
                return thread;
            });
    }
} 