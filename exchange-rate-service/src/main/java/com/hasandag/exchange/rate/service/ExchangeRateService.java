package com.hasandag.exchange.rate.service;

import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import com.hasandag.exchange.common.enums.Currency;

public interface ExchangeRateService {
    ExchangeRateResponse getExchangeRate(Currency sourceCurrency, Currency targetCurrency);
}