package com.hasandag.exchange.rate.client;

import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import com.hasandag.exchange.common.exception.RateServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;


@Component
@Slf4j
public class ExchangeRateRestClient {

    private final RestClient restClient;
    private final Executor virtualThreadExecutor;
    
    private final int maxAttempts = 3;
    private final long backoffDelayMs = 1000;

    public ExchangeRateRestClient(@Qualifier("exchangeRateApiRestClient") RestClient restClient,
                                 @Qualifier("externalServiceExecutor") Executor virtualThreadExecutor) {
        this.restClient = restClient;
        this.virtualThreadExecutor = virtualThreadExecutor;
    }

    private ExchangeRateResponse getExchangeRateInternal(String sourceCurrency, String targetCurrency) {
        log.debug("Fetching exchange rate using RestClient: {}-{}", sourceCurrency, targetCurrency);
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ResponseEntity<Map> response = restClient
                        .get()
                        .uri("/{sourceCurrency}", sourceCurrency)
                        .header("X-Request-ID", java.util.UUID.randomUUID().toString())
                        .retrieve()
                        .onStatus(status -> status.is4xxClientError(), (request, resp) -> {
                            if (resp.getStatusCode().value() == 429) {
                                throw new RateServiceException("External API rate limit exceeded");
                            }
                            throw new RateServiceException("Client error: " + resp.getStatusCode());
                        })
                        .toEntity(Map.class);

                Map<String, Object> body = response.getBody();
                
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
                    
                    log.debug("RestClient fetched rate: {}-{} = {} (from {} total rates)", 
                             sourceCurrency, targetCurrency, exchangeRateResponse.getRate(), rates.size());
                    return exchangeRateResponse;
                } else {
                    log.error("API error response: {}", body);
                    throw new RateServiceException("Failed to get exchange rate from API: " + 
                                                 body.getOrDefault("error-type", "Unknown error"));
                }
                
            } catch (org.springframework.web.client.HttpServerErrorException e) {
                String errorMessage = "External API server error: " + e.getResponseBodyAsString();
                log.error("HTTP 5xx error on attempt {} of {}: {}", attempt, maxAttempts, errorMessage);
                
                if (attempt == maxAttempts) {
                    throw new RateServiceException("Retries exhausted for external API: " + errorMessage);
                }
                
                try {
                    Thread.sleep(backoffDelayMs * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RateServiceException("Interrupted while retrying API call: " + errorMessage);
                }
                
            } catch (org.springframework.web.client.ResourceAccessException e) {
                log.error("Resource access error on attempt {} of {}: {}", attempt, maxAttempts, e.getMessage());
                
                if (attempt == maxAttempts) {
                    throw new RateServiceException("Connection failed after " + maxAttempts + " attempts: " + e.getMessage());
                }
                
                try {
                    Thread.sleep(backoffDelayMs * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RateServiceException("Interrupted while retrying connection: " + e.getMessage());
                }
            }
        }
        
        throw new RateServiceException("Unexpected error: exceeded retry attempts");
    }

    @Cacheable(value = "exchangeRates", key = "#sourceCurrency + '-' + #targetCurrency")
    public ExchangeRateResponse getExchangeRate(String sourceCurrency, String targetCurrency) {
        return getExchangeRateInternal(sourceCurrency, targetCurrency);
    }

    public CompletableFuture<ExchangeRateResponse> getExchangeRateAsync(String sourceCurrency, String targetCurrency) {
        return CompletableFuture.supplyAsync(() -> getExchangeRateInternal(sourceCurrency, targetCurrency), virtualThreadExecutor);
    }

    public ExchangeRateResponse getExchangeRateWithAdvancedFeatures(String sourceCurrency, String targetCurrency) {
        log.info("Demonstrating RestClient advanced features for {}-{}", sourceCurrency, targetCurrency);
        
        Map<String, Object> responseBody = restClient
                .get()
                .uri("/{sourceCurrency}", sourceCurrency)
                .header("X-Custom-Header", "RestClient-Demo")
                .header("X-Request-Timestamp", String.valueOf(System.currentTimeMillis()))
                .retrieve()
                .onStatus(
                    status -> status.is5xxServerError(),
                    (request, response) -> {
                        log.error("Server error detected, will retry...");
                        throw new RuntimeException("Server error: " + response.getStatusCode());
                    }
                )
                .body(Map.class);
        
        if (responseBody != null && "success".equals(responseBody.get("result"))) {
            Map<String, Object> rates = (Map<String, Object>) responseBody.get("rates");
            if (rates != null && rates.containsKey(targetCurrency)) {
                double rate = ((Number) rates.get(targetCurrency)).doubleValue();
                return ExchangeRateResponse.builder()
                        .sourceCurrency(sourceCurrency)
                        .targetCurrency(targetCurrency)
                        .rate(BigDecimal.valueOf(rate))
                        .lastUpdated(LocalDateTime.now())
                        .build();
            }
            throw new RateServiceException("Rate not found for " + targetCurrency);
        }
        
        throw new RateServiceException("No valid response received");
    }
} 