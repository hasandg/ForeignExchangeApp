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

import static org.assertj.core.api.Assertions.assertThat;
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
        if (cacheManager != null) {
            cacheManager.getCacheNames().forEach(cacheName -> 
                cacheManager.getCache(cacheName).clear()
            );
        }
        reset(internalExchangeRateClient);
    }

    @Test
    void testCacheHitOnSecondCall() {
        ExchangeRateResponse mockResponse = ExchangeRateResponse.builder()
                .sourceCurrency("USD")
                .targetCurrency("EUR")
                .rate(BigDecimal.valueOf(0.85))
                .lastUpdated(LocalDateTime.now())
                .build();

        when(internalExchangeRateClient.getExchangeRate("USD", "EUR"))
                .thenReturn(mockResponse);

        ExchangeRateResponse firstCall = exchangeRateProvider.getExchangeRate("USD", "EUR");

        ExchangeRateResponse secondCall = exchangeRateProvider.getExchangeRate("USD", "EUR");

        assertThat(firstCall).isNotNull();
        assertThat(secondCall).isNotNull();
        assertThat(firstCall.getRate()).isEqualTo(BigDecimal.valueOf(0.85));
        assertThat(secondCall.getRate()).isEqualTo(BigDecimal.valueOf(0.85));

        verify(internalExchangeRateClient, times(1)).getExchangeRate("USD", "EUR");
    }

    @Test
    void testCacheMissOnDifferentCurrencyPairs() {
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

        ExchangeRateResponse usdEur = exchangeRateProvider.getExchangeRate("USD", "EUR");
        ExchangeRateResponse usdGbp = exchangeRateProvider.getExchangeRate("USD", "GBP");

        assertThat(usdEur.getRate()).isEqualTo(BigDecimal.valueOf(0.85));
        assertThat(usdGbp.getRate()).isEqualTo(BigDecimal.valueOf(0.75));

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