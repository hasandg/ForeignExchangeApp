package com.hasandag.exchange.rate.client.impl;

import com.hasandag.exchange.common.client.ExternalExchangeRateClient;
import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import com.hasandag.exchange.common.exception.RateServiceException;
import com.hasandag.exchange.common.retry.RetryConfiguration;
import com.hasandag.exchange.common.retry.RetryService;
import com.hasandag.exchange.common.retry.RetryServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

@Component
@Slf4j
public class WebClientExternalExchangeRateClient implements ExternalExchangeRateClient {

    private final WebClient webClient;
    private final RetryService retryService;
    private final int rateLimitStatusCode;

    public WebClientExternalExchangeRateClient(
            @Qualifier("exchangeRateApiWebClient") WebClient webClient,
            @Qualifier("retryScheduler") ScheduledExecutorService retryScheduler,
            @Value("${exchange.client.max-attempts:3}") int maxAttempts,
            @Value("${exchange.client.backoff-delay-ms:1000}") long backoffDelayMs,
            @Value("${exchange.client.backoff-multiplier:2.0}") double backoffMultiplier,
            @Value("${exchange.client.max-delay-ms:30000}") long maxDelayMs,
            @Value("${exchange.client.jitter-factor:0.1}") double jitterFactor,
            @Value("${exchange.client.circuit-breaker-enabled:true}") boolean circuitBreakerEnabled,
            @Value("${exchange.client.circuit-breaker-failure-threshold:5}") int failureThreshold,
            @Value("${exchange.client.circuit-breaker-timeout-ms:60000}") long circuitBreakerTimeoutMs,
            @Value("${exchange.client.rate-limit-status-code:429}") int rateLimitStatusCode) {
        
        this.webClient = webClient;
        this.rateLimitStatusCode = rateLimitStatusCode;
        
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
        
        log.info("Initialized WebClient with retry service: maxAttempts={}, backoff={}ms->{}ms, multiplier={}, circuitBreaker={}", 
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

    private ExchangeRateResponse fetchExchangeRateFromApi(String sourceCurrency, String targetCurrency, String callType) {
        log.debug("Making {} HTTP call: {}-{}", callType, sourceCurrency, targetCurrency);
        
        try {
            Mono<Map> responseMono = webClient
                    .get()
                    .uri("/{sourceCurrency}", sourceCurrency)
                    .header("X-Request-ID", java.util.UUID.randomUUID().toString())
                    .header("X-Call-Type", callType)
                    .retrieve()
                    .onStatus(status -> status.value() == rateLimitStatusCode,
                            clientResponse -> Mono.error(new RateServiceException("External API rate limit exceeded")))
                    .onStatus(HttpStatusCode::is4xxClientError,
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(new RateServiceException("Client error: " + clientResponse.statusCode() + " - " + body))))
                    .onStatus(HttpStatusCode::is5xxServerError,
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(new RateServiceException("Server error: " + clientResponse.statusCode() + " - " + body))))
                    .bodyToMono(Map.class);

            Map<String, Object> responseBody = responseMono.block();
            
            log.debug("{} HTTP response: body={}", callType, responseBody);
            
            return parseResponse(responseBody, sourceCurrency, targetCurrency, callType);
            
        } catch (WebClientResponseException e) {
            String errorMessage = callType + " WebClient error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString();
            log.error("WebClient error during {} API call: {}", callType, errorMessage);
            throw new RateServiceException(errorMessage, e);
        } catch (RateServiceException e) {
            throw e;
        } catch (Exception e) {
            String errorMessage = callType + " HTTP request failed: " + e.getMessage();
            log.error("HTTP error during {} API call: {}", callType, errorMessage);
            throw new RateServiceException(errorMessage, e);
        }
    }

    private ExchangeRateResponse parseResponse(Map<String, Object> body, String sourceCurrency, String targetCurrency, String callType) {
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
            
            log.debug("{} call fetched rate: {}-{} = {}", 
                     callType, sourceCurrency, targetCurrency, exchangeRateResponse.getRate());
            return exchangeRateResponse;
        } else {
            log.error("{} API error response: {}", callType, body);
            throw new RateServiceException("Failed to get exchange rate from API: " + 
                                         body.getOrDefault("error-type", "Unknown error"));
        }
    }
    
    public com.hasandag.exchange.common.retry.RetryMetrics getRetryMetrics() {
        return retryService.getMetrics();
    }
} 