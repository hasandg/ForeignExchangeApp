package com.hasandag.exchange.common.client;

import com.hasandag.exchange.common.dto.ExchangeRateResponse;

import java.util.concurrent.CompletableFuture;

public interface InternalExchangeRateClient {

    ExchangeRateResponse getExchangeRate(String sourceCurrency, String targetCurrency);

    CompletableFuture<ExchangeRateResponse> getExchangeRateAsync(String sourceCurrency, String targetCurrency);

} 