package com.hasandag.exchange.rate.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@Slf4j
public class ExternalServiceConfig {

    private final boolean virtualThreadsEnabled;
    private final int poolSize;
    private final Duration timeout;

    public ExternalServiceConfig(
            @Value("${external-services.virtual-threads.enabled:true}") boolean virtualThreadsEnabled,
            @Value("${external-services.virtual-threads.pool-size:50}") int poolSize,
            @Value("${external-services.virtual-threads.timeout:30s}") Duration timeout) {
        this.virtualThreadsEnabled = virtualThreadsEnabled;
        this.poolSize = poolSize;
        this.timeout = timeout;
        log.info("External service config - Virtual threads enabled: {}, Pool size: {}, Timeout: {}", 
                virtualThreadsEnabled, poolSize, timeout);
    }

    @Bean("externalServiceExecutor")
    @Primary
    public Executor externalServiceExecutor() {
        if (virtualThreadsEnabled) {
            log.info("Creating virtual thread executor for external services");
            return task -> Thread.ofVirtual()
                    .name("external-service-", 0)
                    .start(task);
        } else {
            log.info("Creating traditional thread pool executor for external services");
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(poolSize / 2);
            executor.setMaxPoolSize(poolSize);
            executor.setQueueCapacity(poolSize * 2);
            executor.setThreadNamePrefix("external-service-");
            executor.initialize();
            return executor;
        }
    }

    @Bean("thirdPartyApiExecutor")
    public Executor thirdPartyApiExecutor() {
        if (virtualThreadsEnabled) {
            return task -> Thread.ofVirtual()
                    .name("third-party-api-", 0)
                    .start(task);
        } else {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(10);
            executor.setMaxPoolSize(25);
            executor.setQueueCapacity(50);
            executor.setThreadNamePrefix("third-party-api-");
            executor.initialize();
            return executor;
        }
    }

    @Bean("httpClientExecutor")
    public Executor httpClientExecutor() {
        if (virtualThreadsEnabled) {
            return task -> Thread.ofVirtual()
                    .name("http-client-", 0)
                    .start(task);
        } else {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(15);
            executor.setMaxPoolSize(30);
            executor.setQueueCapacity(100);
            executor.setThreadNamePrefix("http-client-");
            executor.initialize();
            return executor;
        }
    }
} 