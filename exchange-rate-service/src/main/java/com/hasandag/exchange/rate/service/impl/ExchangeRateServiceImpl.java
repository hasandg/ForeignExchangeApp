package com.hasandag.exchange.rate.service.impl;

import com.hasandag.exchange.common.client.ExternalExchangeRateClient;
import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import com.hasandag.exchange.rate.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateServiceImpl implements ExchangeRateService {
    
    private final ExternalExchangeRateClient externalClient;

    @Override
    public ExchangeRateResponse getExchangeRate(String sourceCurrency, String targetCurrency) {
        log.debug("Getting exchange rate: {} -> {}", sourceCurrency, targetCurrency);
        return externalClient.getExchangeRate(sourceCurrency, targetCurrency);
    }

    @Override
    public CompletableFuture<ExchangeRateResponse> getExchangeRateAsync(String sourceCurrency, String targetCurrency) {
        log.debug("Getting exchange rate async: {} -> {}", sourceCurrency, targetCurrency);
        return externalClient.getExchangeRateAsync(sourceCurrency, targetCurrency);
    }
} 