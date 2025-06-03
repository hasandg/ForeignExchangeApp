package com.hasandag.exchange.rate.client;

import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import com.hasandag.exchange.common.exception.RateServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Component
@Slf4j
public class ExchangeRateClient {

    private final RestTemplate restTemplate;
    private final String apiUrl;
    private final Executor virtualThreadExecutor;
    
    private final int maxAttempts = 3;
    private final long backoffDelayMs = 1000;

    public ExchangeRateClient(RestTemplate restTemplate, 
                             @Value("${exchange.api.url}") String apiUrl) {
        this.restTemplate = restTemplate;
        this.apiUrl = apiUrl;
        this.virtualThreadExecutor = task -> Thread.ofVirtual().start(task);
    }

    private ExchangeRateResponse getExchangeRateInternal(String sourceCurrency, String targetCurrency) {
        log.debug("Fetching exchange rate: {}-{}", sourceCurrency, targetCurrency);
        
        String url = apiUrl + "/" + sourceCurrency;
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
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
                    
                    log.debug("Fetched rate: {}-{} = {} (from {} total rates)", 
                             sourceCurrency, targetCurrency, exchangeRateResponse.getRate(), rates.size());
                    return exchangeRateResponse;
                } else {
                    log.error("Error from API: {}", body);
                    throw new RateServiceException("Failed to get exchange rate from API: " + 
                                                 body.getOrDefault("error-type", "Unknown error"));
                }
                
            } catch (HttpServerErrorException e) {
                String errorMessage = "External API server error: " + e.getResponseBodyAsString();
                log.error("HTTP 5xx error on attempt {} of {}: {}", attempt, maxAttempts, errorMessage);
                
                if (attempt == maxAttempts) {
                    throw new RateServiceException("Retries exhausted for external API: " + errorMessage);
                }
                
                // Wait before retry
                try {
                    Thread.sleep(backoffDelayMs * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RateServiceException("Interrupted while retrying API call: " + errorMessage);
                }
                
            } catch (HttpClientErrorException.TooManyRequests e) {
                throw new RateServiceException("External API rate limit exceeded.");
            } catch (HttpClientErrorException e) {
                log.error("HTTP client error: Status {}, Body {}", e.getStatusCode(), e.getResponseBodyAsString());
                throw new RateServiceException("Client error: " + e.getMessage());
            } catch (ResourceAccessException e) {
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
}