package com.hasandag.exchange.rate.controller;

import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import com.hasandag.exchange.rate.service.ExchangeRateService;
import com.hasandag.exchange.rate.service.impl.ExchangeRateServiceImpl;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/v1/exchange-rates")
@RequiredArgsConstructor
@Validated
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;
    private final ExchangeRateServiceImpl exchangeRateServiceImpl;

    @GetMapping
    public ResponseEntity<ExchangeRateResponse> getExchangeRate(
            @RequestParam 
            @NotBlank(message = "Source currency cannot be blank")
            @Pattern(regexp = "^[A-Z]{3}$", message = "Source currency must be a 3-letter uppercase code")
            String sourceCurrency,
            @RequestParam 
            @NotBlank(message = "Target currency cannot be blank")
            @Pattern(regexp = "^[A-Z]{3}$", message = "Target currency must be a 3-letter uppercase code")
            String targetCurrency) {
        ExchangeRateResponse response = exchangeRateService.getExchangeRate(sourceCurrency, targetCurrency);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/async")
    public CompletableFuture<ResponseEntity<ExchangeRateResponse>> getExchangeRateAsync(
            @RequestParam 
            @NotBlank(message = "Source currency cannot be blank")
            @Pattern(regexp = "^[A-Z]{3}$", message = "Source currency must be a 3-letter uppercase code")
            String sourceCurrency,
            @RequestParam 
            @NotBlank(message = "Target currency cannot be blank")
            @Pattern(regexp = "^[A-Z]{3}$", message = "Target currency must be a 3-letter uppercase code")
            String targetCurrency) {
        
        log.info("Async request received for {} -> {} using virtual threads", sourceCurrency, targetCurrency);
        
        return exchangeRateServiceImpl.getExchangeRateAsync(sourceCurrency, targetCurrency)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping("/third-party")
    public CompletableFuture<ResponseEntity<ExchangeRateResponse>> getExchangeRateFromThirdParty(
            @RequestParam 
            @NotBlank(message = "Source currency cannot be blank")
            @Pattern(regexp = "^[A-Z]{3}$", message = "Source currency must be a 3-letter uppercase code")
            String sourceCurrency,
            @RequestParam 
            @NotBlank(message = "Target currency cannot be blank")
            @Pattern(regexp = "^[A-Z]{3}$", message = "Target currency must be a 3-letter uppercase code")
            String targetCurrency) {
        
        log.info("Third-party async request received for {} -> {} using virtual threads", sourceCurrency, targetCurrency);
        
        return exchangeRateServiceImpl.getExchangeRateFromThirdParty(sourceCurrency, targetCurrency)
                .thenApply(ResponseEntity::ok);
    }
}