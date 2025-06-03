package com.hasandag.exchange.gateway.controller;

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
    public ResponseEntity<String> exchangeRatesFallback() {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Exchange Rate Service is currently unavailable. Please try again later.");
    }

    @GetMapping("/conversions")
    public ResponseEntity<String> conversionsFallback() {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Currency Conversion Service is currently unavailable. Please try again later.");
    }

    @PostMapping("/conversions")
    public ResponseEntity<String> conversionsPostFallback() {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Currency Conversion Service is currently unavailable. Please try again later.");
    }
} 