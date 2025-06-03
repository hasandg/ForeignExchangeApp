package com.hasandag.exchange.rate.client;

import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import com.hasandag.exchange.common.exception.RateServiceException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
class ExchangeRateClientTest {

    private MockWebServer mockWebServer;
    private ExchangeRateClient exchangeRateClient;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        RestTemplate restTemplate = new RestTemplate();
        String baseUrl = mockWebServer.url("/").toString();
        
        exchangeRateClient = new ExchangeRateClient(restTemplate, baseUrl);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void testGetExchangeRateSuccess() {
        String sourceCurrency = "USD";
        String targetCurrency = "EUR";
        String mockJsonResponse = String.format(
                "{\"result\":\"success\", \"base_code\":\"%s\", \"rates\":{\"%s\":0.85}}",
                sourceCurrency, targetCurrency
        );

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockJsonResponse)
                .addHeader("Content-Type", "application/json"));

        ExchangeRateResponse result = exchangeRateClient.getExchangeRate(sourceCurrency, targetCurrency);

        assertNotNull(result);
        assertEquals(sourceCurrency, result.getSourceCurrency());
        assertEquals(targetCurrency, result.getTargetCurrency());
        assertEquals(BigDecimal.valueOf(0.85), result.getRate());
    }

    @Test
    void testApiError() {
        String sourceCurrency = "USD";
        String targetCurrency = "EUR";
        String mockJsonResponse = "{\"result\":\"error\", \"error-type\":\"invalid-data\"}";

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockJsonResponse)
                .addHeader("Content-Type", "application/json"));

        RateServiceException exception = assertThrows(RateServiceException.class, 
                () -> exchangeRateClient.getExchangeRate(sourceCurrency, targetCurrency));
        assertTrue(exception.getMessage().contains("invalid-data"));
    }

    @Test
    void testHttp500Error() {
        String sourceCurrency = "USD";
        String targetCurrency = "EUR";
        
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("Attempt 1 Failed"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("Attempt 2 Failed"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("Attempt 3 Failed"));

        RateServiceException exception = assertThrows(RateServiceException.class,
                () -> exchangeRateClient.getExchangeRate(sourceCurrency, targetCurrency));

        assertTrue(exception.getMessage().startsWith("Retries exhausted for external API: "));
        assertTrue(exception.getMessage().contains("External API server error: Attempt 3 Failed"));
        assertEquals(3, mockWebServer.getRequestCount());
    }

    @Test
    void testCurrencyNotFound() {
        String sourceCurrency = "USD";
        String targetCurrency = "INVALID";
        String mockJsonResponse = String.format(
                "{\"result\":\"success\", \"base_code\":\"%s\", \"rates\":{\"EUR\":0.85}}",
                sourceCurrency
        );

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockJsonResponse)
                .addHeader("Content-Type", "application/json"));

        RateServiceException exception = assertThrows(RateServiceException.class, 
                () -> exchangeRateClient.getExchangeRate(sourceCurrency, targetCurrency));
        assertTrue(exception.getMessage().contains("Exchange rate not found for INVALID"));
    }

    @Test
    void testAsyncExchangeRate() throws ExecutionException, InterruptedException {
        String sourceCurrency = "USD";
        String targetCurrency = "EUR";
        String mockJsonResponse = String.format(
                "{\"result\":\"success\", \"base_code\":\"%s\", \"rates\":{\"%s\":0.85}}",
                sourceCurrency, targetCurrency
        );

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockJsonResponse)
                .addHeader("Content-Type", "application/json"));

        CompletableFuture<ExchangeRateResponse> future = exchangeRateClient.getExchangeRateAsync(sourceCurrency, targetCurrency);
        ExchangeRateResponse result = future.get();

        assertNotNull(result);
        assertEquals(sourceCurrency, result.getSourceCurrency());
        assertEquals(targetCurrency, result.getTargetCurrency());
        assertEquals(BigDecimal.valueOf(0.85), result.getRate());
    }

    @Test
    void testRetryOnErrors() {
        String sourceCurrency = "USD";
        String targetCurrency = "EUR";

        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("Attempt 1 Failed"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("Attempt 2 Failed"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("Attempt 3 Failed"));

        RateServiceException exception = assertThrows(RateServiceException.class,
                () -> exchangeRateClient.getExchangeRate(sourceCurrency, targetCurrency));

        assertTrue(exception.getMessage().startsWith("Retries exhausted for external API: "));
        assertTrue(exception.getMessage().contains("External API server error: Attempt 3 Failed"));
        assertEquals(3, mockWebServer.getRequestCount());
    }
} 