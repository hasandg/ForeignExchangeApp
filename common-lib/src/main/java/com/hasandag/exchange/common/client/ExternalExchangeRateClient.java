package com.hasandag.exchange.common.client;

import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import com.hasandag.exchange.common.enums.Currency;

public interface ExternalExchangeRateClient {
    
    ExchangeRateResponse getExchangeRate(Currency sourceCurrency, Currency targetCurrency);
} 