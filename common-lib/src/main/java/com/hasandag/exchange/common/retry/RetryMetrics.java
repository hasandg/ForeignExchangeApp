package com.hasandag.exchange.common.retry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryMetrics {
    
    private long totalAttempts;
    private long totalSuccesses;
    private long totalFailures;
    private long totalRetries;
    private double successRate;
    private int activeCircuitBreakers;
    
    public long getTotalOperations() {
        return totalSuccesses + totalFailures;
    }
    
    public double getFailureRate() {
        return 1.0 - successRate;
    }
    
    public double getAverageRetriesPerOperation() {
        long totalOps = getTotalOperations();
        return totalOps > 0 ? (double) totalRetries / totalOps : 0.0;
    }
} 