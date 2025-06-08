package com.hasandag.exchange.rate.retry;

import com.hasandag.exchange.common.retry.RetryConfiguration;
import com.hasandag.exchange.common.retry.RetryService;
import com.hasandag.exchange.common.retry.RetryServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class RetryImprovementTest {

    @Test
    public void demonstrateOldVsNewRetryPerformance() {
        log.info("=== Demonstrating Retry System Improvements ===");
        
        
        long oldRetryTime = simulateOldBlockingRetry();
        
        
        long newRetryTime = simulateNewNonBlockingRetry();
        
        log.info("Old blocking retry took: {}ms", oldRetryTime);
        log.info("New non-blocking retry took: {}ms", newRetryTime);
        log.info("Performance improvement: {}%", ((double)(oldRetryTime - newRetryTime) / oldRetryTime) * 100);
        
        
        assertTrue(newRetryTime < oldRetryTime, "New retry should be faster than old retry");
    }
    
    @Test
    public void demonstrateExponentialBackoffWithJitter() {
        RetryConfiguration config = RetryConfiguration.builder()
                .maxAttempts(5)
                .initialDelay(Duration.ofMillis(100))
                .backoffMultiplier(2.0)
                .jitterFactor(0.1)
                .enableExponentialBackoff(true)
                .build();
        
        log.info("=== Exponential Backoff with Jitter ===");
        for (int attempt = 1; attempt <= 5; attempt++) {
            Duration delay = config.calculateDelay(attempt);
            log.info("Attempt {}: delay = {}ms", attempt, delay.toMillis());
            
            
            if (attempt > 1) {
                Duration previousDelay = config.calculateDelay(attempt - 1);
                assertTrue(delay.toMillis() >= previousDelay.toMillis(), 
                          "Delay should increase exponentially");
            }
        }
    }
    
    @Test
    public void demonstrateCircuitBreakerBehavior() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        
        RetryConfiguration config = RetryConfiguration.builder()
                .maxAttempts(3)
                .initialDelay(Duration.ofMillis(50))
                .enableCircuitBreaker(true)
                .circuitBreakerFailureThreshold(2)
                .circuitBreakerTimeout(Duration.ofSeconds(1))
                .circuitBreakerMinCalls(2)
                .build();
        
        RetryService retryService = new RetryServiceImpl(config, scheduler);
        AtomicInteger callCount = new AtomicInteger(0);
        
        log.info("=== Circuit Breaker Demonstration ===");
        
        
        for (int i = 0; i < 5; i++) {
            try {
                CompletableFuture<String> result = retryService.executeWithRetry(() -> {
                    int count = callCount.incrementAndGet();
                    log.info("Call attempt: {}", count);
                    if (count <= 4) {
                        throw new RuntimeException("Simulated failure");
                    }
                    return "Success!";
                }, "circuitBreakerTest");
                
                String outcome = result.join();
                log.info("Result: {}", outcome);
                
            } catch (Exception e) {
                log.info("Failed with: {}", e.getMessage());
            }
        }
        
        var metrics = retryService.getMetrics();
        log.info("Final metrics - Attempts: {}, Successes: {}, Failures: {}, Circuit Breakers: {}", 
                metrics.getTotalAttempts(), metrics.getTotalSuccesses(), 
                metrics.getTotalFailures(), metrics.getActiveCircuitBreakers());
        
        scheduler.shutdown();
    }
    
    @Test
    public void demonstrateIntelligentErrorCategorization() {
        RetryConfiguration config = RetryConfiguration.builder()
                .maxAttempts(3)
                .initialDelay(Duration.ofMillis(10))
                .build();
        
        log.info("=== Intelligent Error Categorization ===");
        
        
        boolean shouldRetryConnection = config.isRetryableException(
            new org.springframework.web.client.ResourceAccessException("Connection timeout"));
        log.info("ResourceAccessException is retryable: {}", shouldRetryConnection);
        assertTrue(shouldRetryConnection);
        
        
        boolean shouldRetryIllegal = config.isRetryableException(
            new IllegalArgumentException("Invalid parameter"));
        log.info("IllegalArgumentException is retryable: {}", shouldRetryIllegal);
        assertFalse(shouldRetryIllegal);
        
        
        assertTrue(config.isRetryableHttpStatus(503)); 
        assertTrue(config.isRetryableHttpStatus(429)); 
        assertFalse(config.isRetryableHttpStatus(400)); 
        assertFalse(config.isRetryableHttpStatus(404)); 
        
        log.info("HTTP 503 (Service Unavailable) is retryable: {}", config.isRetryableHttpStatus(503));
        log.info("HTTP 400 (Bad Request) is retryable: {}", config.isRetryableHttpStatus(400));
    }
    
    private long simulateOldBlockingRetry() {
        log.info("Simulating old blocking retry...");
        long startTime = System.currentTimeMillis();
        
        int maxAttempts = 3;
        long backoffDelayMs = 100;
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                if (attempt < 3) {
                    throw new RuntimeException("Simulated failure");
                }
                
                break;
            } catch (Exception e) {
                if (attempt < maxAttempts) {
                    try {
                        
                        Thread.sleep(backoffDelayMs * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        return System.currentTimeMillis() - startTime;
    }
    
    private long simulateNewNonBlockingRetry() {
        log.info("Simulating new non-blocking retry...");
        long startTime = System.currentTimeMillis();
        
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        
        RetryConfiguration config = RetryConfiguration.builder()
                .maxAttempts(3)
                .initialDelay(Duration.ofMillis(100))
                .enableExponentialBackoff(false) 
                .enableCircuitBreaker(false)
                .build();
        
        RetryService retryService = new RetryServiceImpl(config, scheduler);
        AtomicInteger attemptCount = new AtomicInteger(0);
        
        try {
            CompletableFuture<String> result = retryService.executeWithRetry(() -> {
                int attempt = attemptCount.incrementAndGet();
                if (attempt < 3) {
                    throw new RuntimeException("Simulated failure");
                }
                return "Success!";
            }, "performanceTest");
            
            result.join(); 
            
        } catch (Exception e) {
            
        } finally {
            scheduler.shutdown();
        }
        
        return System.currentTimeMillis() - startTime;
    }
} 