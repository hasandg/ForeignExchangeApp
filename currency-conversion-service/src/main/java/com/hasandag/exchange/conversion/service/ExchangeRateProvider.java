package com.hasandag.exchange.conversion.service;

import com.hasandag.exchange.common.dto.ExchangeRateResponse;

public interface ExchangeRateProvider {

    ExchangeRateResponse getExchangeRate(String sourceCurrency, String targetCurrency);

} 