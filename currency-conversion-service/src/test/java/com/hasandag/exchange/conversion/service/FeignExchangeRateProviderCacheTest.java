package com.hasandag.exchange.conversion.service;

import com.hasandag.exchange.common.client.InternalExchangeRateClient;
import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import com.hasandag.exchange.conversion.service.impl.FeignExchangeRateProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = {FeignExchangeRateProvider.class, FeignExchangeRateProviderCacheTest.CacheTestConfig.class})
class FeignExchangeRateProviderCacheTest {

    @MockBean
    private InternalExchangeRateClient internalExchangeRateClient;

    @Autowired
    private ExchangeRateProvider exchangeRateProvider;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        // Clear all caches before each test
        cacheManager.getCacheNames().forEach(cacheName -> 
            cacheManager.getCache(cacheName).clear());
        
        // Reset mock invocations
        clearInvocations(internalExchangeRateClient);
    }

    @Test
    void testCacheHit() {
        // Given
        String sourceCurrency = "USD";
        String targetCurrency = "EUR";
        ExchangeRateResponse mockResponse = ExchangeRateResponse.builder()
                .sourceCurrency(sourceCurrency)
                .targetCurrency(targetCurrency)
                .rate(BigDecimal.valueOf(0.85))
                .lastUpdated(LocalDateTime.now())
                .build();

        when(internalExchangeRateClient.getExchangeRate(sourceCurrency, targetCurrency))
                .thenReturn(mockResponse);

        // When - First call (cache miss)
        ExchangeRateResponse result1 = exchangeRateProvider.getExchangeRate(sourceCurrency, targetCurrency);

        // When - Second call (should be cache hit)
        ExchangeRateResponse result2 = exchangeRateProvider.getExchangeRate(sourceCurrency, targetCurrency);

        // Then
        assertNotNull(result1);
        assertNotNull(result2);
        assertEquals(mockResponse.getRate(), result1.getRate());
        assertEquals(mockResponse.getRate(), result2.getRate());
        
        // Verify the external client was called only once (first call)
        verify(internalExchangeRateClient, times(1)).getExchangeRate(sourceCurrency, targetCurrency);
    }

    @Test
    void testDifferentCurrencyPairsNotCached() {
        // Given
        ExchangeRateResponse usdEurResponse = ExchangeRateResponse.builder()
                .sourceCurrency("USD")
                .targetCurrency("EUR")
                .rate(BigDecimal.valueOf(0.85))
                .lastUpdated(LocalDateTime.now())
                .build();

        ExchangeRateResponse usdGbpResponse = ExchangeRateResponse.builder()
                .sourceCurrency("USD")
                .targetCurrency("GBP")
                .rate(BigDecimal.valueOf(0.75))
                .lastUpdated(LocalDateTime.now())
                .build();

        when(internalExchangeRateClient.getExchangeRate("USD", "EUR"))
                .thenReturn(usdEurResponse);
        when(internalExchangeRateClient.getExchangeRate("USD", "GBP"))
                .thenReturn(usdGbpResponse);

        // When
        ExchangeRateResponse result1 = exchangeRateProvider.getExchangeRate("USD", "EUR");
        ExchangeRateResponse result2 = exchangeRateProvider.getExchangeRate("USD", "GBP");

        // Then
        assertNotNull(result1);
        assertNotNull(result2);
        assertEquals(BigDecimal.valueOf(0.85), result1.getRate());
        assertEquals(BigDecimal.valueOf(0.75), result2.getRate());
        
        // Verify both calls were made (different cache keys)
        verify(internalExchangeRateClient, times(1)).getExchangeRate("USD", "EUR");
        verify(internalExchangeRateClient, times(1)).getExchangeRate("USD", "GBP");
    }

    @Configuration
    @EnableCaching
    static class CacheTestConfig {
        
        @Bean
        @Primary
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("exchange-rates");
        }
    }
} 