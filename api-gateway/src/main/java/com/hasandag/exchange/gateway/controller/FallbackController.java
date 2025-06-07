package com.hasandag.exchange.gateway.controller;

import com.hasandag.exchange.common.exception.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/exchange-rates")
    public ResponseEntity<ErrorResponse> exchangeRatesFallback() {
        ErrorResponse response = ErrorResponse.of(
                "SERVICE_UNAVAILABLE",
                "Exchange Rate Service is currently unavailable. Please try again later.",
                "Circuit breaker triggered",
                "/fallback/exchange-rates"
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @GetMapping("/conversions")
    public ResponseEntity<ErrorResponse> conversionsFallback() {
        ErrorResponse response = ErrorResponse.of(
                "SERVICE_UNAVAILABLE",
                "Currency Conversion Service is currently unavailable. Please try again later.",
                "Circuit breaker triggered",
                "/fallback/conversions"
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @PostMapping("/conversions")
    public ResponseEntity<ErrorResponse> conversionsPostFallback() {
        ErrorResponse response = ErrorResponse.of(
                "SERVICE_UNAVAILABLE",
                "Currency Conversion Service is currently unavailable. Please try again later.",
                "Circuit breaker triggered",
                "/fallback/conversions"
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
} 