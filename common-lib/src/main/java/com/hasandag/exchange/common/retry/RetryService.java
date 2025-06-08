package com.hasandag.exchange.common.retry;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * RetryService Interface
 * 
 * Provides an abstraction for retry operations to follow the Dependency Inversion Principle.
 * High-level modules should depend on this abstraction rather than concrete implementation.
 */
public interface RetryService {
    
    /**
     * Execute an operation with retry logic
     * 
     * @param operation The operation to execute
     * @param operationName A name for the operation (used in logging and metrics)
     * @param <T> The return type of the operation
     * @return A CompletableFuture containing the operation result
     */
    <T> CompletableFuture<T> executeWithRetry(Supplier<T> operation, String operationName);
    
    /**
     * Get metrics for retry operations
     * 
     * @return RetryMetrics containing statistics about retry operations
     */
    RetryMetrics getMetrics();
} 