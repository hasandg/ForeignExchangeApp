package com.hasandag.exchange.common.retry;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

@Slf4j
public class RetryService {
    
    private final RetryConfiguration config;
    private final ScheduledExecutorService scheduler;
    private final CircuitBreakerState circuitBreaker;
    
    private final AtomicLong totalAttempts = new AtomicLong(0);
    private final AtomicLong totalSuccesses = new AtomicLong(0);
    private final AtomicLong totalFailures = new AtomicLong(0);
    private final AtomicLong totalRetries = new AtomicLong(0);
    
    public RetryService(RetryConfiguration config, ScheduledExecutorService scheduler) {
        this.config = config;
        this.scheduler = scheduler;
        this.circuitBreaker = new CircuitBreakerState(config);
        
        config.validate();
        log.info("RetryService initialized with config: {}", config);
    }
    
    public <T> CompletableFuture<T> executeWithRetry(Supplier<T> operation, String operationName) {
        if (config.isEnableCircuitBreaker() && circuitBreaker.isOpen()) {
            log.warn("Circuit breaker is OPEN for operation: {}", operationName);
            return CompletableFuture.failedFuture(
                new RuntimeException("Circuit breaker is open for operation: " + operationName)
            );
        }
        
        return executeAttempt(operation, operationName, 1, config.getInitialDelay());
    }
    
    private <T> CompletableFuture<T> executeAttempt(Supplier<T> operation, String operationName, 
                                                   int attemptNumber, Duration currentDelay) {
        
        CompletableFuture<T> future = new CompletableFuture<>();
        totalAttempts.incrementAndGet();
        
        try {
            log.debug("Executing attempt {} for operation: {}", attemptNumber, operationName);
            T result = operation.get();
            
            totalSuccesses.incrementAndGet();
            if (config.isEnableCircuitBreaker()) {
                circuitBreaker.recordSuccess();
            }
            
            future.complete(result);
            
        } catch (Exception e) {
            log.warn("Attempt {} failed for operation: {} - {}", attemptNumber, operationName, e.getMessage());
            
            if (attemptNumber >= config.getMaxAttempts()) {
                totalFailures.incrementAndGet();
                if (config.isEnableCircuitBreaker()) {
                    circuitBreaker.recordFailure();
                }
                log.error("All {} attempts failed for operation: {}", config.getMaxAttempts(), operationName);
                future.completeExceptionally(e);
            } else {
                totalRetries.incrementAndGet();
                Duration nextDelay = calculateNextDelay(currentDelay, attemptNumber);
                log.debug("Scheduling retry attempt {} for operation: {} in {}ms", 
                         attemptNumber + 1, operationName, nextDelay.toMillis());
                
                scheduler.schedule(() -> {
                    executeAttempt(operation, operationName, attemptNumber + 1, nextDelay)
                        .whenComplete((result, throwable) -> {
                            if (throwable != null) {
                                future.completeExceptionally(throwable);
                            } else {
                                future.complete(result);
                            }
                        });
                }, nextDelay.toMillis(), TimeUnit.MILLISECONDS);
            }
        }
        
        return future;
    }
    
    private Duration calculateNextDelay(Duration currentDelay, int attemptNumber) {
        if (!config.isEnableExponentialBackoff()) {
            return currentDelay;
        }
        
        long baseDelayMs = (long) (currentDelay.toMillis() * config.getBackoffMultiplier());
        long maxDelayMs = config.getMaxDelay().toMillis();
        long delayMs = Math.min(baseDelayMs, maxDelayMs);
        
        if (config.getJitterFactor() > 0) {
            double jitterRange = delayMs * config.getJitterFactor();
            double jitter = (ThreadLocalRandom.current().nextDouble() - 0.5) * 2 * jitterRange;
            delayMs = Math.max(0, (long) (delayMs + jitter));
        }
        
        return Duration.ofMillis(delayMs);
    }
    
    public RetryMetrics getMetrics() {
        long attempts = totalAttempts.get();
        long successes = totalSuccesses.get();
        double successRate = attempts > 0 ? (double) successes / attempts : 1.0;
        
        return RetryMetrics.builder()
                .totalAttempts(attempts)
                .totalSuccesses(successes)
                .totalFailures(totalFailures.get())
                .totalRetries(totalRetries.get())
                .successRate(successRate)
                .activeCircuitBreakers(config.isEnableCircuitBreaker() ? 1 : 0)
                .build();
    }
    
    public boolean isCircuitBreakerOpen() {
        return config.isEnableCircuitBreaker() && circuitBreaker.isOpen();
    }
    
    public void resetCircuitBreaker() {
        if (config.isEnableCircuitBreaker()) {
            circuitBreaker.reset();
            log.info("Circuit breaker has been manually reset");
        }
    }
    
    private static class CircuitBreakerState {
        private final RetryConfiguration config;
        private volatile CircuitBreakerStatus status = CircuitBreakerStatus.CLOSED;
        private volatile int failureCount = 0;
        private volatile int successCount = 0;
        private volatile Instant lastFailureTime = Instant.now();
        
        public CircuitBreakerState(RetryConfiguration config) {
            this.config = config;
        }
        
        public synchronized boolean isOpen() {
            if (status == CircuitBreakerStatus.OPEN) {
                if (Instant.now().isAfter(lastFailureTime.plus(config.getCircuitBreakerTimeout()))) {
                    status = CircuitBreakerStatus.HALF_OPEN;
                    failureCount = 0;
                    successCount = 0;
                    log.info("Circuit breaker transitioned to HALF_OPEN");
                    return false;
                }
                return true;
            }
            return false;
        }
        
        public synchronized void recordSuccess() {
            successCount++;
            
            if (status == CircuitBreakerStatus.HALF_OPEN) {
                if (successCount >= config.getCircuitBreakerMinCalls()) {
                    status = CircuitBreakerStatus.CLOSED;
                    failureCount = 0;
                    successCount = 0;
                    log.info("Circuit breaker transitioned to CLOSED after successful recovery");
                }
            } else if (status == CircuitBreakerStatus.CLOSED) {
                failureCount = Math.max(0, failureCount - 1);
            }
        }
        
        public synchronized void recordFailure() {
            failureCount++;
            lastFailureTime = Instant.now();
            
            if (status == CircuitBreakerStatus.CLOSED || status == CircuitBreakerStatus.HALF_OPEN) {
                if (failureCount >= config.getCircuitBreakerFailureThreshold()) {
                    status = CircuitBreakerStatus.OPEN;
                    log.warn("Circuit breaker transitioned to OPEN after {} failures", failureCount);
                }
            }
        }
        
        public synchronized void reset() {
            status = CircuitBreakerStatus.CLOSED;
            failureCount = 0;
            successCount = 0;
        }
    }
    
    private enum CircuitBreakerStatus {
        CLOSED, OPEN, HALF_OPEN
    }
} 