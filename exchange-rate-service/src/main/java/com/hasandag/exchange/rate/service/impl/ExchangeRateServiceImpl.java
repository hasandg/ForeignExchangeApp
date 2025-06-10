package com.hasandag.exchange.rate.service.impl;

import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import com.hasandag.exchange.common.client.ExternalExchangeRateClient;
import com.hasandag.exchange.common.enums.Currency;
import com.hasandag.exchange.rate.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateServiceImpl implements ExchangeRateService {
    
    private final ExternalExchangeRateClient externalExchangeRateClient;

    @Override
    @Cacheable(value = "exchangeRates", key = "#sourceCurrency.code + '-' + #targetCurrency.code")
    public ExchangeRateResponse getExchangeRate(Currency sourceCurrency, Currency targetCurrency) {
        log.info("Fetching exchange rate for {} -> {}", sourceCurrency, targetCurrency);
        
        return externalExchangeRateClient.getExchangeRate(sourceCurrency, targetCurrency);
    }
} 