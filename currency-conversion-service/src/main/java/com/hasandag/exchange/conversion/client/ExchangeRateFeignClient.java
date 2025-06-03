package com.hasandag.exchange.conversion.client;

import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "exchange-rate-service", url = "${conversion.exchange-rate-service.url:http://localhost:8083}", path = "/api/v1/exchange-rates")
public interface ExchangeRateFeignClient {

    @GetMapping
    ExchangeRateResponse getExchangeRate(@RequestParam("sourceCurrency") String sourceCurrency,
                                       @RequestParam("targetCurrency") String targetCurrency);
} 