package com.hasandag.exchange.rate.client.impl;

import com.hasandag.exchange.common.client.ExternalExchangeRateClient;
import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import com.hasandag.exchange.common.enums.Currency;
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

@Slf4j
@Component
public class WebClientExternalExchangeRateClient implements ExternalExchangeRateClient {

    private final WebClient webClient;
    private final RetryService retryService;
    private final int rateLimitStatusCode;

    public WebClientExternalExchangeRateClient(
            @Qualifier("externalApiWebClient") WebClient webClient,
            @Qualifier("retryScheduler") ScheduledExecutorService scheduler,
            @Value("${exchange.api.rate-limit-status-code:429}") int rateLimitStatusCode) {
        this.webClient = webClient;
        this.rateLimitStatusCode = rateLimitStatusCode;
        
        RetryConfiguration retryConfig = RetryConfiguration.builder()
                .maxAttempts(3)
                .initialDelay(Duration.ofSeconds(1))
                .maxDelay(Duration.ofSeconds(30))
                .backoffMultiplier(2.0)
                .jitterFactor(0.1)
                .build();
        
        this.retryService = new RetryServiceImpl(retryConfig, scheduler);
    }

    @Override
    @Cacheable(value = "exchangeRates", key = "#sourceCurrency.code + '-' + #targetCurrency.code")
    public ExchangeRateResponse getExchangeRate(Currency sourceCurrency, Currency targetCurrency) {
        log.info("Fetching exchange rate for {} -> {}", sourceCurrency, targetCurrency);
        
        return retryService.executeWithRetry(
            () -> fetchExchangeRateFromApi(sourceCurrency, targetCurrency, "Primary"),
            "ExchangeRateAPI-" + sourceCurrency + "-" + targetCurrency
        ).join();
    }

    private ExchangeRateResponse fetchExchangeRateFromApi(Currency sourceCurrency, Currency targetCurrency, String callType) {
        log.debug("Making {} HTTP call: {}-{}", callType, sourceCurrency, targetCurrency);
        
        try {
            Mono<Map> responseMono = webClient
                    .get()
                    .uri("")
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

    private ExchangeRateResponse parseResponse(Map<String, Object> body, Currency sourceCurrency, Currency targetCurrency, String callType) {
        if (body == null) {
            throw new RateServiceException("Failed to get exchange rate from API: Empty response");
        }
        
        log.debug("{} API response format detection: keys={}", callType, body.keySet());
        
        // Handle multiple API response formats
        Map<String, Object> rates = null;
        
        // Format 1: ExchangeRate-API.com format: {"base": "USD", "rates": {"EUR": 0.8747}}
        if (body.containsKey("base") && body.containsKey("rates")) {
            rates = (Map<String, Object>) body.get("rates");
            log.debug("{} detected ExchangeRate-API.com format", callType);
        }
        // Format 2: open.er-api.com format: {"result": "success", "base_code": "USD", "rates": {"EUR": 0.8747}}
        else if ("success".equals(body.get("result")) && body.containsKey("rates") && body.containsKey("base_code")) {
            rates = (Map<String, Object>) body.get("rates");
            log.debug("{} detected open.er-api.com format", callType);
        }
        // Format 3: Mock/Custom format: {"result": "success", "rates": {"EUR": 0.8747}}
        else if ("success".equals(body.get("result")) && body.containsKey("rates")) {
            rates = (Map<String, Object>) body.get("rates");
            log.debug("{} detected Mock/Custom format", callType);
        }
        // Format 4: Fixer.io format: {"success": true, "rates": {"EUR": 0.8747}}
        else if (Boolean.TRUE.equals(body.get("success")) && body.containsKey("rates")) {
            rates = (Map<String, Object>) body.get("rates");
            log.debug("{} detected Fixer.io format", callType);
        }
        // Format 5: Direct rates object (some APIs return rates directly)
        else if (body.containsKey(targetCurrency.getCode())) {
            rates = body;
            log.debug("{} detected direct rates format", callType);
        }
        
        if (rates == null || !rates.containsKey(targetCurrency.getCode())) {
            log.error("{} API response parsing failed: rates={}, targetCurrency={}, body={}", 
                     callType, rates, targetCurrency, body);
            throw new RateServiceException("Exchange rate not found for " + targetCurrency + " in API response");
        }
        
        double rate = ((Number) rates.get(targetCurrency.getCode())).doubleValue();
        ExchangeRateResponse exchangeRateResponse = ExchangeRateResponse.builder()
                .sourceCurrency(sourceCurrency)
                .targetCurrency(targetCurrency)
                .rate(BigDecimal.valueOf(rate))
                .lastUpdated(LocalDateTime.now())
                .build();
        
        log.debug("{} call fetched rate: {}-{} = {}", 
                 callType, sourceCurrency, targetCurrency, exchangeRateResponse.getRate());
        return exchangeRateResponse;
    }
    
    public com.hasandag.exchange.common.retry.RetryMetrics getRetryMetrics() {
        return retryService.getMetrics();
    }
} 