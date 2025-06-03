package com.hasandag.exchange.rate.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@Slf4j
public class ExternalServiceConfig {

    private final boolean virtualThreadsEnabled;
    private final int poolSize;
    private final Duration timeout;
    
    
    private final String externalServiceThreadPrefix;
    private final int externalServiceThreadCounter;
    private final double externalServiceCorePoolSizeFactor;
    private final double externalServiceQueueCapacityFactor;
    
    
    private final String httpClientThreadPrefix;
    private final int httpClientThreadCounter;
    private final int httpClientCorePoolSize;
    private final int httpClientMaxPoolSize;
    private final int httpClientQueueCapacity;

    public ExternalServiceConfig(
            @Value("${external-services.virtual-threads.enabled:true}") boolean virtualThreadsEnabled,
            @Value("${external-services.virtual-threads.pool-size:50}") int poolSize,
            @Value("${external-services.virtual-threads.timeout:30s}") Duration timeout,
            @Value("${external-services.executors.external-service.thread-name-prefix:external-service-}") String externalServiceThreadPrefix,
            @Value("${external-services.executors.external-service.thread-name-counter:0}") int externalServiceThreadCounter,
            @Value("${external-services.executors.external-service.core-pool-size-factor:0.5}") double externalServiceCorePoolSizeFactor,
            @Value("${external-services.executors.external-service.queue-capacity-factor:2.0}") double externalServiceQueueCapacityFactor,
            @Value("${external-services.executors.http-client.thread-name-prefix:http-client-}") String httpClientThreadPrefix,
            @Value("${external-services.executors.http-client.thread-name-counter:0}") int httpClientThreadCounter,
            @Value("${external-services.executors.http-client.core-pool-size:15}") int httpClientCorePoolSize,
            @Value("${external-services.executors.http-client.max-pool-size:30}") int httpClientMaxPoolSize,
            @Value("${external-services.executors.http-client.queue-capacity:100}") int httpClientQueueCapacity) {
        this.virtualThreadsEnabled = virtualThreadsEnabled;
        this.poolSize = poolSize;
        this.timeout = timeout;
        this.externalServiceThreadPrefix = externalServiceThreadPrefix;
        this.externalServiceThreadCounter = externalServiceThreadCounter;
        this.externalServiceCorePoolSizeFactor = externalServiceCorePoolSizeFactor;
        this.externalServiceQueueCapacityFactor = externalServiceQueueCapacityFactor;
        this.httpClientThreadPrefix = httpClientThreadPrefix;
        this.httpClientThreadCounter = httpClientThreadCounter;
        this.httpClientCorePoolSize = httpClientCorePoolSize;
        this.httpClientMaxPoolSize = httpClientMaxPoolSize;
        this.httpClientQueueCapacity = httpClientQueueCapacity;
        
        log.info("External service config - Virtual threads enabled: {}, Pool size: {}, Timeout: {}", 
                virtualThreadsEnabled, poolSize, timeout);
    }

    @Bean("externalServiceExecutor")
    @Primary
    public Executor externalServiceExecutor() {
        if (virtualThreadsEnabled) {
            log.info("Creating virtual thread executor for external services");
            return task -> Thread.ofVirtual()
                    .name(externalServiceThreadPrefix, externalServiceThreadCounter)
                    .start(task);
        } else {
            log.info("Creating traditional thread pool executor for external services");
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize((int) (poolSize * externalServiceCorePoolSizeFactor));
            executor.setMaxPoolSize(poolSize);
            executor.setQueueCapacity((int) (poolSize * externalServiceQueueCapacityFactor));
            executor.setThreadNamePrefix(externalServiceThreadPrefix);
            executor.initialize();
            return executor;
        }
    }

    @Bean("httpClientExecutor")
    public Executor httpClientExecutor() {
        if (virtualThreadsEnabled) {
            return task -> Thread.ofVirtual()
                    .name(httpClientThreadPrefix, httpClientThreadCounter)
                    .start(task);
        } else {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(httpClientCorePoolSize);
            executor.setMaxPoolSize(httpClientMaxPoolSize);
            executor.setQueueCapacity(httpClientQueueCapacity);
            executor.setThreadNamePrefix(httpClientThreadPrefix);
            executor.initialize();
            return executor;
        }
    }
} 