package com.hasandag.exchange.rate.controller;

import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import com.hasandag.exchange.common.enums.Currency;
import com.hasandag.exchange.rate.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/exchange-rates")
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    @GetMapping
    public ResponseEntity<ExchangeRateResponse> getExchangeRate(
            @RequestParam Currency sourceCurrency,
            @RequestParam Currency targetCurrency) {
        
        log.debug("Sync exchange rate request: {} -> {}", sourceCurrency, targetCurrency);
        ExchangeRateResponse response = exchangeRateService.getExchangeRate(sourceCurrency, targetCurrency);
        return ResponseEntity.ok(response);
    }
}