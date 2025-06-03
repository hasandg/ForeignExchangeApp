package com.hasandag.exchange.rate.controller;

import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import com.hasandag.exchange.rate.service.ExchangeRateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
class ExchangeRateControllerTest {

    @Mock
    private ExchangeRateService exchangeRateService;

    @InjectMocks
    private ExchangeRateController exchangeRateController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetExchangeRate() {
        String sourceCurrency = "USD";
        String targetCurrency = "EUR";
        ExchangeRateResponse mockResponse = ExchangeRateResponse.builder()
                .sourceCurrency(sourceCurrency)
                .targetCurrency(targetCurrency)
                .rate(BigDecimal.valueOf(0.85))
                .lastUpdated(LocalDateTime.now())
                .build();
        
        when(exchangeRateService.getExchangeRate(sourceCurrency, targetCurrency))
                .thenReturn(mockResponse);

        ResponseEntity<ExchangeRateResponse> response = exchangeRateController.getExchangeRate(sourceCurrency, targetCurrency);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(sourceCurrency, response.getBody().getSourceCurrency());
        assertEquals(targetCurrency, response.getBody().getTargetCurrency());
        assertEquals(BigDecimal.valueOf(0.85), response.getBody().getRate());
    }
} 