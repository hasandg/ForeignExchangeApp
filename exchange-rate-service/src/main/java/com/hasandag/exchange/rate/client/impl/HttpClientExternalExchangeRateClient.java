package com.hasandag.exchange.rate.client.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hasandag.exchange.common.client.ExternalExchangeRateClient;
import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import com.hasandag.exchange.common.exception.RateServiceException;
import com.hasandag.exchange.common.retry.RetryConfiguration;
import com.hasandag.exchange.common.retry.RetryService;
import com.hasandag.exchange.common.retry.RetryServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

@Component
@Slf4j
public class HttpClientExternalExchangeRateClient implements ExternalExchangeRateClient {

    private final CloseableHttpClient httpClient;
    private final Executor virtualThreadExecutor;
    private final RetryService retryService;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final int rateLimitStatusCode;

    public HttpClientExternalExchangeRateClient(
            @Qualifier("exchangeRateApiHttpClient") CloseableHttpClient httpClient,
            @Qualifier("externalServiceExecutor") Executor virtualThreadExecutor,
            @Qualifier("retryScheduler") ScheduledExecutorService retryScheduler,
            @Value("${exchange.api.url}") String baseUrl,
            @Value("${exchange.client.max-attempts:3}") int maxAttempts,
            @Value("${exchange.client.backoff-delay-ms:1000}") long backoffDelayMs,
            @Value("${exchange.client.backoff-multiplier:2.0}") double backoffMultiplier,
            @Value("${exchange.client.max-delay-ms:30000}") long maxDelayMs,
            @Value("${exchange.client.jitter-factor:0.1}") double jitterFactor,
            @Value("${exchange.client.circuit-breaker-enabled:true}") boolean circuitBreakerEnabled,
            @Value("${exchange.client.circuit-breaker-failure-threshold:5}") int failureThreshold,
            @Value("${exchange.client.circuit-breaker-timeout-ms:60000}") long circuitBreakerTimeoutMs,
            @Value("${exchange.client.rate-limit-status-code:429}") int rateLimitStatusCode) {
        
        this.httpClient = httpClient;
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.baseUrl = baseUrl;
        this.rateLimitStatusCode = rateLimitStatusCode;
        this.objectMapper = new ObjectMapper();
        
        RetryConfiguration retryConfig = RetryConfiguration.builder()
                .maxAttempts(maxAttempts)
                .initialDelay(Duration.ofMillis(backoffDelayMs))
                .maxDelay(Duration.ofMillis(maxDelayMs))
                .backoffMultiplier(backoffMultiplier)
                .jitterFactor(jitterFactor)
                .enableExponentialBackoff(true)
                .enableCircuitBreaker(circuitBreakerEnabled)
                .circuitBreakerFailureThreshold(failureThreshold)
                .circuitBreakerTimeout(Duration.ofMillis(circuitBreakerTimeoutMs))
                .circuitBreakerMinCalls(3)
                .build();
        
        this.retryService = new RetryServiceImpl(retryConfig, retryScheduler);
        
        log.info("Initialized Apache HttpClient 5 with retry service: maxAttempts={}, backoff={}ms->{}ms, multiplier={}, circuitBreaker={}", 
                maxAttempts, backoffDelayMs, maxDelayMs, backoffMultiplier, circuitBreakerEnabled);
    }

    @Override
    @Cacheable(value = "exchangeRates", key = "#sourceCurrency + '-' + #targetCurrency")
    public ExchangeRateResponse getExchangeRate(String sourceCurrency, String targetCurrency) {
        log.debug("Fetching exchange rate synchronously: {}-{}", sourceCurrency, targetCurrency);
        
        String operationName = "getExchangeRate_" + sourceCurrency + "_" + targetCurrency;
        return retryService.executeWithRetry(
                () -> fetchExchangeRateFromApi(sourceCurrency, targetCurrency, "SYNC"),
                operationName
        ).join();
    }

    @Override
    public CompletableFuture<ExchangeRateResponse> getExchangeRateAsync(String sourceCurrency, String targetCurrency) {
        log.debug("Fetching exchange rate asynchronously: {}-{}", sourceCurrency, targetCurrency);
        
        String operationName = "getExchangeRateAsync_" + sourceCurrency + "_" + targetCurrency;
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return fetchExchangeRateFromApi(sourceCurrency, targetCurrency, "ASYNC");
            } catch (Exception e) {
                log.error("Async HTTP call failed: {}", e.getMessage());
                throw new RuntimeException(e);
            }
        }, virtualThreadExecutor).thenCompose(result -> {
            return retryService.executeWithRetry(() -> result, operationName);
        });
    }

    private ExchangeRateResponse fetchExchangeRateFromApi(String sourceCurrency, String targetCurrency, String callType) {
        log.debug("Making {} HTTP call: {}-{}", callType, sourceCurrency, targetCurrency);
        
        String url = baseUrl + "/" + sourceCurrency;
        HttpGet request = new HttpGet(url);
        
        // Add headers
        request.addHeader("X-Request-ID", java.util.UUID.randomUUID().toString());
        request.addHeader("X-Call-Type", callType);
        request.addHeader("X-Thread-Type", Thread.currentThread().isVirtual() ? "VIRTUAL" : "PLATFORM");
        request.addHeader("Accept", "application/json");
        
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getCode();
            HttpEntity entity = response.getEntity();
            String responseBody = entity != null ? EntityUtils.toString(entity) : "";
            
            log.debug("{} HTTP response: status={}, body={}", callType, statusCode, responseBody);
            
            if (statusCode == HttpStatus.SC_OK) {
                return parseResponse(responseBody, sourceCurrency, targetCurrency, callType);
            } else if (statusCode == rateLimitStatusCode) {
                throw new RateServiceException("External API rate limit exceeded");
            } else if (statusCode >= 400 && statusCode < 500) {
                throw new RateServiceException("Client error: " + statusCode + " - " + responseBody);
            } else if (statusCode >= 500) {
                throw new RateServiceException("Server error: " + statusCode + " - " + responseBody);
            } else {
                throw new RateServiceException("Unexpected response: " + statusCode + " - " + responseBody);
            }
            
        } catch (RateServiceException e) {
            throw e;
        } catch (Exception e) {
            String errorMessage = callType + " HTTP request failed: " + e.getMessage();
            log.error("HTTP error during {} API call: {}", callType, errorMessage);
            throw new RateServiceException(errorMessage, e);
        }
    }

    private ExchangeRateResponse parseResponse(String responseBody, String sourceCurrency, String targetCurrency, String callType) {
        try {
            Map<String, Object> body = objectMapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {});
            
            if (body == null) {
                throw new RateServiceException("Failed to get exchange rate from API: Empty response");
            }
            
            if ("success".equals(body.get("result"))) {
                Map<String, Object> rates = (Map<String, Object>) body.get("rates");
                if (rates == null || !rates.containsKey(targetCurrency)) {
                    throw new RateServiceException("Exchange rate not found for " + targetCurrency);
                }
                
                double rate = ((Number) rates.get(targetCurrency)).doubleValue();
                ExchangeRateResponse exchangeRateResponse = ExchangeRateResponse.builder()
                        .sourceCurrency(sourceCurrency)
                        .targetCurrency(targetCurrency)
                        .rate(BigDecimal.valueOf(rate))
                        .lastUpdated(LocalDateTime.now())
                        .build();
                
                log.debug("{} call fetched rate: {}-{} = {} [Thread: {}]", 
                         callType, sourceCurrency, targetCurrency, exchangeRateResponse.getRate(),
                         Thread.currentThread().isVirtual() ? "VIRTUAL" : "PLATFORM");
                return exchangeRateResponse;
            } else {
                log.error("{} API error response: {}", callType, body);
                throw new RateServiceException("Failed to get exchange rate from API: " + 
                                             body.getOrDefault("error-type", "Unknown error"));
            }
        } catch (RateServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error parsing {} response: {}", callType, e.getMessage());
            throw new RateServiceException("Failed to parse API response", e);
        }
    }
    
    public com.hasandag.exchange.common.retry.RetryMetrics getRetryMetrics() {
        return retryService.getMetrics();
    }
} 