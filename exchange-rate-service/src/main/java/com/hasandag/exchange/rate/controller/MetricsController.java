package com.hasandag.exchange.rate.controller;

import com.hasandag.exchange.common.retry.RetryMetrics;
import com.hasandag.exchange.rate.client.impl.WebClientExternalExchangeRateClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final WebClientExternalExchangeRateClient exchangeRateClient;

    @GetMapping("/retry")
    public ResponseEntity<Map<String, Object>> getRetryMetrics() {
        RetryMetrics metrics = exchangeRateClient.getRetryMetrics();
        
        Map<String, Object> response = new HashMap<>();
        response.put("totalAttempts", metrics.getTotalAttempts());
        response.put("totalSuccesses", metrics.getTotalSuccesses());
        response.put("totalFailures", metrics.getTotalFailures());
        response.put("totalRetries", metrics.getTotalRetries());
        response.put("successRate", String.format("%.2f%%", metrics.getSuccessRate() * 100));
        response.put("failureRate", String.format("%.2f%%", metrics.getFailureRate() * 100));
        response.put("averageRetriesPerOperation", String.format("%.2f", metrics.getAverageRetriesPerOperation()));
        response.put("activeCircuitBreakers", metrics.getActiveCircuitBreakers());
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealthMetrics() {
        RetryMetrics retryMetrics = exchangeRateClient.getRetryMetrics();
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("retryHealth", Map.of(
            "successRate", retryMetrics.getSuccessRate(),
            "circuitBreakersOpen", retryMetrics.getActiveCircuitBreakers() > 0,
            "totalOperations", retryMetrics.getTotalOperations()
        ));
        
        return ResponseEntity.ok(response);
    }
} 