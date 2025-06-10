package com.hasandag.exchange.conversion.service.impl;

import com.hasandag.exchange.common.client.InternalExchangeRateClient;
import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import com.hasandag.exchange.common.enums.Currency;
import com.hasandag.exchange.conversion.service.ExchangeRateProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class FeignExchangeRateProvider implements ExchangeRateProvider {

    private final InternalExchangeRateClient internalExchangeRateClient;

    @Override
    public ExchangeRateResponse getExchangeRate(Currency sourceCurrency, Currency targetCurrency) {
        log.info("Fetching exchange rate from {} to {} using internal client", sourceCurrency, targetCurrency);
        
        ExchangeRateResponse response = internalExchangeRateClient.getExchangeRate(sourceCurrency, targetCurrency);
        log.info("Successfully retrieved exchange rate: {} -> {} = {}", 
                 sourceCurrency, targetCurrency, response.getRate());
        return response;
    }
} 