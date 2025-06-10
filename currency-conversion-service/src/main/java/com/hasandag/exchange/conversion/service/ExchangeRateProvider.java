package com.hasandag.exchange.conversion.service;

import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import com.hasandag.exchange.common.enums.Currency;

public interface ExchangeRateProvider {

    ExchangeRateResponse getExchangeRate(Currency sourceCurrency, Currency targetCurrency);

} 