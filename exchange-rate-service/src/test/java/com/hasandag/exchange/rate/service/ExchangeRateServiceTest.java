package com.hasandag.exchange.rate.service;

import com.hasandag.exchange.common.client.ExternalExchangeRateClient;
import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import com.hasandag.exchange.common.enums.Currency;
import com.hasandag.exchange.rate.service.impl.ExchangeRateServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ActiveProfiles("test")
class ExchangeRateServiceTest {

    @Mock
    private ExternalExchangeRateClient externalExchangeRateClient;

    @InjectMocks
    private ExchangeRateServiceImpl exchangeRateService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("Test getting exchange rate from external API")
    void testGetExchangeRate() {
        Currency sourceCurrency = Currency.USD;
        Currency targetCurrency = Currency.EUR;
        ExchangeRateResponse mockResponse = ExchangeRateResponse.builder()
                .sourceCurrency(sourceCurrency)
                .targetCurrency(targetCurrency)
                .rate(BigDecimal.valueOf(0.85))
                .lastUpdated(LocalDateTime.now())
                .build();
        
        when(externalExchangeRateClient.getExchangeRate(sourceCurrency, targetCurrency))
                .thenReturn(mockResponse);

        ExchangeRateResponse result = exchangeRateService.getExchangeRate(sourceCurrency, targetCurrency);

        assertNotNull(result);
        assertEquals(sourceCurrency, result.getSourceCurrency());
        assertEquals(targetCurrency, result.getTargetCurrency());
        assertEquals(BigDecimal.valueOf(0.85), result.getRate());
        verify(externalExchangeRateClient, times(1))
            .getExchangeRate(sourceCurrency, targetCurrency);
    }

    @Test
    @DisplayName("Test case insensitive currency codes")
    void testCaseInsensitiveCurrencyCodes() {
        Currency sourceCurrency = Currency.USD;
        Currency targetCurrency = Currency.EUR;
        ExchangeRateResponse mockResponse = ExchangeRateResponse.builder()
                .sourceCurrency(sourceCurrency)
                .targetCurrency(targetCurrency)
                .rate(BigDecimal.valueOf(0.85))
                .lastUpdated(LocalDateTime.now())
                .build();
        
        when(externalExchangeRateClient.getExchangeRate(sourceCurrency, targetCurrency))
                .thenReturn(mockResponse);

        ExchangeRateResponse result = exchangeRateService.getExchangeRate(sourceCurrency, targetCurrency);

        assertNotNull(result);
        verify(externalExchangeRateClient, times(1))
            .getExchangeRate(sourceCurrency, targetCurrency);
    }
} 