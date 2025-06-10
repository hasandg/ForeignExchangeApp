package com.hasandag.exchange.rate.client.impl;

import com.hasandag.exchange.common.client.ExternalExchangeRateClient;
import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import com.hasandag.exchange.common.enums.Currency;
import com.hasandag.exchange.common.exception.RateServiceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class WebClientExternalExchangeRateClient implements ExternalExchangeRateClient {

    private final WebClient webClient;

    public WebClientExternalExchangeRateClient(@Qualifier("externalApiWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    @Cacheable(value = "exchangeRates", key = "#sourceCurrency.code + '-' + #targetCurrency.code")
    @Retry(name = "exchange-rate-api")
    @CircuitBreaker(name = "exchange-rate-api", fallbackMethod = "fallbackExchangeRate")
    public ExchangeRateResponse getExchangeRate(Currency sourceCurrency, Currency targetCurrency) {
        return fetchExchangeRateFromApi(sourceCurrency, targetCurrency);
    }

    public ExchangeRateResponse fallbackExchangeRate(Currency sourceCurrency, Currency targetCurrency, Exception ex) {
        throw new RateServiceException("Exchange rate service temporarily unavailable", ex);
    }

    private ExchangeRateResponse fetchExchangeRateFromApi(Currency sourceCurrency, Currency targetCurrency) {
        try {
            Mono<Map> responseMono = webClient
                    .get()
                    .uri("")
                    .header("X-Request-ID", UUID.randomUUID().toString())
                    .retrieve()
                    .onStatus(status -> status.value() == 429,
                            clientResponse -> Mono.error(new RateServiceException("External API rate limit exceeded")))
                    .onStatus(HttpStatusCode::is4xxClientError,
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(new RateServiceException("Client error: " + clientResponse.statusCode() + " - " + body))))
                    .onStatus(HttpStatusCode::is5xxServerError,
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(new RateServiceException("Server error: " + clientResponse.statusCode() + " - " + body))))
                    .bodyToMono(Map.class);

            Map<String, Object> responseBody = responseMono.block();
            return parseResponse(responseBody, sourceCurrency, targetCurrency);
            
        } catch (WebClientResponseException e) {
            throw new RateServiceException("WebClient error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (RateServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new RateServiceException("HTTP request failed: " + e.getMessage(), e);
        }
    }

    private ExchangeRateResponse parseResponse(Map<String, Object> body, Currency sourceCurrency, Currency targetCurrency) {
        if (body == null) {
            throw new RateServiceException("Empty response from API");
        }
        
        Map<String, Object> rates = extractRates(body);
        
        if (rates == null || !rates.containsKey(targetCurrency.getCode())) {
            throw new RateServiceException("Exchange rate not found for " + targetCurrency);
        }
        
        Object rateValue = rates.get(targetCurrency.getCode());
        BigDecimal rate;
        
        try {
            if (rateValue instanceof String) {
                rate = new BigDecimal((String) rateValue);
            } else if (rateValue instanceof Number) {
                rate = new BigDecimal(rateValue.toString());
            } else {
                throw new RateServiceException("Invalid rate format: " + rateValue);
            }
        } catch (NumberFormatException e) {
            throw new RateServiceException("Cannot parse exchange rate: " + rateValue, e);
        }
        
        return ExchangeRateResponse.builder()
                .sourceCurrency(sourceCurrency)
                .targetCurrency(targetCurrency)
                .rate(rate)
                .lastUpdated(LocalDateTime.now())
                .build();
    }
    
    private Map<String, Object> extractRates(Map<String, Object> body) {
        if (body.containsKey("base") && body.containsKey("rates")) {
            return (Map<String, Object>) body.get("rates");
        }
        if ("success".equals(body.get("result")) && body.containsKey("rates")) {
            return (Map<String, Object>) body.get("rates");
        }
        if (Boolean.TRUE.equals(body.get("success")) && body.containsKey("rates")) {
            return (Map<String, Object>) body.get("rates");
        }
        return body;
    }
} 