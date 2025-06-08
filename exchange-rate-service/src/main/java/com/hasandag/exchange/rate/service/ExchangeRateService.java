package com.hasandag.exchange.rate.service;

import com.hasandag.exchange.common.dto.ExchangeRateResponse;

public interface ExchangeRateService {
    ExchangeRateResponse getExchangeRate(String sourceCurrency, String targetCurrency);
}