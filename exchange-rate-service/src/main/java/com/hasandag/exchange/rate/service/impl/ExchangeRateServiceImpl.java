package com.hasandag.exchange.rate.service.impl;

import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import com.hasandag.exchange.rate.client.ExchangeRateClient;
import com.hasandag.exchange.rate.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateServiceImpl implements ExchangeRateService {
    
    private final ExchangeRateClient exchangeRateClient;

    @Override
    @Cacheable(value = "exchangeRates", key = "#sourceCurrency + '-' + #targetCurrency")
    public ExchangeRateResponse getExchangeRate(String sourceCurrency, String targetCurrency) {
        log.info("Fetching exchange rate for {} -> {}", sourceCurrency, targetCurrency);
        
        return exchangeRateClient.getExchangeRate(sourceCurrency.toUpperCase(), targetCurrency.toUpperCase());
    }

    @Async("externalServiceExecutor")
    public CompletableFuture<ExchangeRateResponse> getExchangeRateAsync(String sourceCurrency, String targetCurrency) {
        log.info("Fetching exchange rate asynchronously for {} -> {}", sourceCurrency, targetCurrency);
        
        return exchangeRateClient.getExchangeRateAsync(sourceCurrency.toUpperCase(), targetCurrency.toUpperCase());
    }

    @Async("thirdPartyApiExecutor")
    public CompletableFuture<ExchangeRateResponse> getExchangeRateFromThirdParty(String sourceCurrency, String targetCurrency) {
        log.info("Fetching exchange rate from third party for {} -> {}", sourceCurrency, targetCurrency);
        
        return CompletableFuture.completedFuture(
            exchangeRateClient.getExchangeRate(sourceCurrency.toUpperCase(), targetCurrency.toUpperCase())
        );
    }
} 