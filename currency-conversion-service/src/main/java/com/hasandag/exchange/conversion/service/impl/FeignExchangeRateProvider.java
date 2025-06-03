package com.hasandag.exchange.conversion.service.impl;

import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import com.hasandag.exchange.common.exception.RateServiceException;
import com.hasandag.exchange.conversion.client.ExchangeRateFeignClient;
import com.hasandag.exchange.conversion.service.ExchangeRateProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FeignExchangeRateProvider implements ExchangeRateProvider {

    private final ExchangeRateFeignClient feignClient;

    public FeignExchangeRateProvider(ExchangeRateFeignClient feignClient) {
        this.feignClient = feignClient;
    }

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