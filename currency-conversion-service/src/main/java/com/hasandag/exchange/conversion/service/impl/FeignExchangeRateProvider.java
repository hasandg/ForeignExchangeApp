package com.hasandag.exchange.conversion.service.impl;

import com.hasandag.exchange.common.client.InternalExchangeRateClient;
import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import com.hasandag.exchange.conversion.service.ExchangeRateProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class FeignExchangeRateProvider implements ExchangeRateProvider {

    private final InternalExchangeRateClient internalExchangeRateClient;

    @Override
    @Cacheable(value = "conversion-exchange-rates", key = "#sourceCurrency + '-' + #targetCurrency")
    public ExchangeRateResponse getExchangeRate(String sourceCurrency, String targetCurrency) {
        log.info("Fetching exchange rate from {} to {} using internal client (cache miss)", sourceCurrency, targetCurrency);
        
        ExchangeRateResponse response = internalExchangeRateClient.getExchangeRate(sourceCurrency, targetCurrency);
        log.info("Successfully retrieved and cached exchange rate: {} -> {} = {}", 
                 sourceCurrency, targetCurrency, response.getRate());
        return response;
    }
} 