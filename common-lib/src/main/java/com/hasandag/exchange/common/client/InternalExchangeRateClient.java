package com.hasandag.exchange.common.client;

import com.hasandag.exchange.common.dto.ExchangeRateResponse;

public interface InternalExchangeRateClient {

    ExchangeRateResponse getExchangeRate(String sourceCurrency, String targetCurrency);

} 