package com.hasandag.exchange.conversion.client.impl;

import com.hasandag.exchange.common.client.InternalExchangeRateClient;
import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import com.hasandag.exchange.common.exception.RateServiceException;
import com.hasandag.exchange.conversion.client.ExchangeRateFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class FeignInternalExchangeRateClient implements InternalExchangeRateClient {

    private final ExchangeRateFeignClient feignClient;

    @Override
    public ExchangeRateResponse getExchangeRate(String sourceCurrency, String targetCurrency) {
        log.debug("Getting exchange rate from {} to {} using Feign client", sourceCurrency, targetCurrency);
        
        try {
            ExchangeRateResponse response = feignClient.getExchangeRate(sourceCurrency, targetCurrency);
            log.debug("Successfully retrieved exchange rate: {} -> {} = {}", 
                     sourceCurrency, targetCurrency, response.getRate());
            return response;
        } catch (Exception ex) {
            log.error("Failed to get exchange rate from {} to {}: {}", 
                     sourceCurrency, targetCurrency, ex.getMessage());
            throw new RateServiceException(
                String.format("Unable to get exchange rate from %s to %s: %s", 
                             sourceCurrency, targetCurrency, ex.getMessage()), ex);
        }
    }
} 