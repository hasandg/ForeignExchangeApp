package com.hasandag.exchange.common.retry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryConfiguration {
    
    
    @Builder.Default
    private int maxAttempts = 3;
    
    @Builder.Default
    private Duration initialDelay = Duration.ofMillis(1000);
    
    @Builder.Default
    private Duration maxDelay = Duration.ofSeconds(30);
    
    
    @Builder.Default
    private double backoffMultiplier = 2.0;
    
    @Builder.Default
    private double jitterFactor = 0.1;
    
    @Builder.Default
    private boolean enableExponentialBackoff = true;
    
    
    @Builder.Default
    private boolean enableCircuitBreaker = false;
    
    @Builder.Default
    private int circuitBreakerFailureThreshold = 5;
    
    @Builder.Default
    private Duration circuitBreakerTimeout = Duration.ofMinutes(1);
    
    @Builder.Default
    private int circuitBreakerMinCalls = 3;
    
    
    @Builder.Default
    private Set<Class<? extends Throwable>> retryableExceptions = Set.of(
        java.net.ConnectException.class,
        java.net.SocketTimeoutException.class,
        org.springframework.web.client.ResourceAccessException.class
    );
    
    @Builder.Default
    private Set<Class<? extends Throwable>> nonRetryableExceptions = Set.of(
        IllegalArgumentException.class,
        SecurityException.class
    );
    
    @Builder.Default
    private Set<Integer> retryableHttpStatuses = Set.of(408, 429, 500, 502, 503, 504);
    
    @Builder.Default
    private Set<Integer> nonRetryableHttpStatuses = Set.of(400, 401, 403, 404, 422);
    
    
    @Builder.Default
    private boolean enableRateLimiting = false;
    
    @Builder.Default
    private int rateLimitPermitsPerSecond = 10;
    
    
    @Builder.Default
    private boolean enableMetrics = true;
    
    @Builder.Default
    private String metricsPrefix = "retry";
    
    public Duration calculateDelay(int attemptNumber) {
        if (!enableExponentialBackoff) {
            return initialDelay;
        }
        
        double delay = initialDelay.toMillis() * Math.pow(backoffMultiplier, attemptNumber - 1);
        
        
        if (jitterFactor > 0) {
            double jitter = delay * jitterFactor * Math.random();
            delay += jitter;
        }
        
        
        long finalDelay = Math.min((long) delay, maxDelay.toMillis());
        
        return Duration.ofMillis(finalDelay);
    }
    
    public boolean isRetryableException(Throwable throwable) {
        Class<? extends Throwable> exceptionClass = throwable.getClass();
        
        
        if (nonRetryableExceptions.stream().anyMatch(clazz -> clazz.isAssignableFrom(exceptionClass))) {
            return false;
        }
        
        
        return retryableExceptions.stream().anyMatch(clazz -> clazz.isAssignableFrom(exceptionClass));
    }
    
    public boolean isRetryableHttpStatus(int statusCode) {
        
        if (nonRetryableHttpStatuses.contains(statusCode)) {
            return false;
        }
        
        return retryableHttpStatuses.contains(statusCode);
    }
    
    public void validate() {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        if (initialDelay.isNegative() || initialDelay.isZero()) {
            throw new IllegalArgumentException("initialDelay must be positive");
        }
        if (maxDelay.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException("maxDelay must be >= initialDelay");
        }
        if (backoffMultiplier <= 1.0) {
            throw new IllegalArgumentException("backoffMultiplier must be > 1.0");
        }
        if (jitterFactor < 0.0 || jitterFactor > 1.0) {
            throw new IllegalArgumentException("jitterFactor must be between 0.0 and 1.0");
        }
        if (enableCircuitBreaker) {
            if (circuitBreakerFailureThreshold <= 0) {
                throw new IllegalArgumentException("circuitBreakerFailureThreshold must be positive");
            }
            if (circuitBreakerTimeout.isNegative() || circuitBreakerTimeout.isZero()) {
                throw new IllegalArgumentException("circuitBreakerTimeout must be positive");
            }
            if (circuitBreakerMinCalls <= 0) {
                throw new IllegalArgumentException("circuitBreakerMinCalls must be positive");
            }
        }
    }
    
    public static RetryConfiguration defaultConfig() {
        return RetryConfiguration.builder().build();
    }
    
    public static RetryConfiguration withCircuitBreaker() {
        return RetryConfiguration.builder()
                .enableCircuitBreaker(true)
                .build();
    }
    
    public static RetryConfiguration aggressive() {
        return RetryConfiguration.builder()
                .maxAttempts(5)
                .initialDelay(Duration.ofMillis(500))
                .maxDelay(Duration.ofSeconds(10))
                .backoffMultiplier(1.5)
                .jitterFactor(0.2)
                .enableCircuitBreaker(true)
                .circuitBreakerFailureThreshold(3)
                .circuitBreakerTimeout(Duration.ofSeconds(30))
                .build();
    }
} 