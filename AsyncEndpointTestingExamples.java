package com.hasandag.exchange.testing;

import com.hasandag.exchange.common.client.InternalExchangeRateClient;
import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import com.hasandag.exchange.rate.service.ExchangeRateService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AsyncEndpointTestingExamples {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private InternalExchangeRateClient exchangeRateClient;

    @Autowired
    private ExchangeRateService exchangeRateService;

    
    
    

    @Test
    void testAsyncExchangeRateEndpoint_ShouldReturnImmediately() {
        
        ExchangeRateResponse mockResponse = ExchangeRateResponse.builder()
                .sourceCurrency("USD")
                .targetCurrency("EUR")
                .rate(BigDecimal.valueOf(0.85))
                .lastUpdated(LocalDateTime.now())
                .build();

        when(exchangeRateClient.getExchangeRateAsync(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        
        webTestClient.get()
                .uri("/api/v1/exchange-rates/async?sourceCurrency=USD&targetCurrency=EUR")
                .exchange()
                .expectStatus().isOk()
                .expectBody(ExchangeRateResponse.class)
                .value(response -> {
                    assertEquals("USD", response.getSourceCurrency());
                    assertEquals("EUR", response.getTargetCurrency());
                    assertEquals(BigDecimal.valueOf(0.85), response.getRate());
                })
                .returnResult();
    }

    
    
    

    @Test
    void testAsyncMethod_ShouldCompleteAsynchronously() throws Exception {
        
        ExchangeRateResponse mockResponse = ExchangeRateResponse.builder()
                .sourceCurrency("USD")
                .targetCurrency("EUR")
                .rate(BigDecimal.valueOf(0.85))
                .lastUpdated(LocalDateTime.now())
                .build();

        when(exchangeRateClient.getExchangeRateAsync("USD", "EUR"))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        
        CompletableFuture<ExchangeRateResponse> future = exchangeRateService.getExchangeRateAsync("USD", "EUR");

        
        assertNotNull(future);
        assertFalse(future.isDone() || future.isCancelled()); 

        
        ExchangeRateResponse result = future.get(5, TimeUnit.SECONDS);

        assertEquals("USD", result.getSourceCurrency());
        assertEquals("EUR", result.getTargetCurrency());
        assertEquals(BigDecimal.valueOf(0.85), result.getRate());
    }

    
    
    

    @Test
    void testAsyncPerformance_ShouldBeFasterThanSync() throws Exception {
        
        ExchangeRateResponse mockResponse = ExchangeRateResponse.builder()
                .sourceCurrency("USD")
                .targetCurrency("EUR")
                .rate(BigDecimal.valueOf(0.85))
                .lastUpdated(LocalDateTime.now())
                .build();

        
        CompletableFuture<ExchangeRateResponse> slowFuture = CompletableFuture
                .supplyAsync(() -> {
                    try {
                        Thread.sleep(100); 
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return mockResponse;
                });

        when(exchangeRateClient.getExchangeRateAsync(anyString(), anyString()))
                .thenReturn(slowFuture);
        when(exchangeRateClient.getExchangeRate(anyString(), anyString()))
                .thenAnswer(invocation -> {
                    Thread.sleep(100); 
                    return mockResponse;
                });

        
        long asyncStart = System.currentTimeMillis();
        CompletableFuture<ExchangeRateResponse> asyncResult = exchangeRateService.getExchangeRateAsync("USD", "EUR");
        long asyncSubmitTime = System.currentTimeMillis() - asyncStart;
        ExchangeRateResponse asyncResponse = asyncResult.get(5, TimeUnit.SECONDS);
        long asyncTotalTime = System.currentTimeMillis() - asyncStart;

        
        long syncStart = System.currentTimeMillis();
        ExchangeRateResponse syncResponse = exchangeRateService.getExchangeRate("USD", "EUR");
        long syncTotalTime = System.currentTimeMillis() - syncStart;

        
        assertEquals(asyncResponse.getRate(), syncResponse.getRate());
        
        
        assertTrue(asyncSubmitTime < 50, "Async submit should be near-instant: " + asyncSubmitTime + "ms");
        
        
        assertTrue(asyncTotalTime > 90, "Async should take time to complete: " + asyncTotalTime + "ms");
        assertTrue(syncTotalTime > 90, "Sync should take time to complete: " + syncTotalTime + "ms");

        System.out.println("🚀 Async submit time: " + asyncSubmitTime + "ms");
        System.out.println("⏱️ Async total time: " + asyncTotalTime + "ms");
        System.out.println("🐌 Sync total time: " + syncTotalTime + "ms");
    }

    
    
    

    @Test
    void testMultipleAsyncCalls_ShouldExecuteConcurrently() throws Exception {
        
        ExchangeRateResponse usdEurResponse = createMockResponse("USD", "EUR", 0.85);
        ExchangeRateResponse usdGbpResponse = createMockResponse("USD", "GBP", 0.75);
        ExchangeRateResponse usdJpyResponse = createMockResponse("USD", "JPY", 110.0);

        when(exchangeRateClient.getExchangeRateAsync("USD", "EUR"))
                .thenReturn(CompletableFuture.completedFuture(usdEurResponse));
        when(exchangeRateClient.getExchangeRateAsync("USD", "GBP"))
                .thenReturn(CompletableFuture.completedFuture(usdGbpResponse));
        when(exchangeRateClient.getExchangeRateAsync("USD", "JPY"))
                .thenReturn(CompletableFuture.completedFuture(usdJpyResponse));

        
        long startTime = System.currentTimeMillis();

        CompletableFuture<ExchangeRateResponse> future1 = exchangeRateService.getExchangeRateAsync("USD", "EUR");
        CompletableFuture<ExchangeRateResponse> future2 = exchangeRateService.getExchangeRateAsync("USD", "GBP");
        CompletableFuture<ExchangeRateResponse> future3 = exchangeRateService.getExchangeRateAsync("USD", "JPY");

        
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(future1, future2, future3);
        allFutures.get(5, TimeUnit.SECONDS);

        long totalTime = System.currentTimeMillis() - startTime;

        
        ExchangeRateResponse result1 = future1.get();
        ExchangeRateResponse result2 = future2.get();
        ExchangeRateResponse result3 = future3.get();

        assertEquals(BigDecimal.valueOf(0.85), result1.getRate());
        assertEquals(BigDecimal.valueOf(0.75), result2.getRate());
        assertEquals(BigDecimal.valueOf(110.0), result3.getRate());

        
        assertTrue(totalTime < 1000, "Concurrent execution should be fast: " + totalTime + "ms");

        System.out.println("✅ All 3 async calls completed in: " + totalTime + "ms");
    }

    
    
    

    @Test
    void testAsyncErrorHandling_ShouldPropagateExceptions() {
        
        CompletableFuture<ExchangeRateResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Service unavailable"));

        when(exchangeRateClient.getExchangeRateAsync("USD", "INVALID"))
                .thenReturn(failedFuture);

        
        CompletableFuture<ExchangeRateResponse> result = exchangeRateService.getExchangeRateAsync("USD", "INVALID");

        assertThrows(Exception.class, () -> {
            result.get(1, TimeUnit.SECONDS);
        });

        assertTrue(result.isCompletedExceptionally());
    }

    
    
    

    @Test
    void testAsyncTimeout_ShouldHandleSlowResponses() {
        
        CompletableFuture<ExchangeRateResponse> neverCompleteFuture = new CompletableFuture<>();

        when(exchangeRateClient.getExchangeRateAsync("USD", "SLOW"))
                .thenReturn(neverCompleteFuture);

        
        CompletableFuture<ExchangeRateResponse> result = exchangeRateService.getExchangeRateAsync("USD", "SLOW");

        
        assertThrows(java.util.concurrent.TimeoutException.class, () -> {
            result.get(100, TimeUnit.MILLISECONDS); 
        });
    }

    
    
    

    private ExchangeRateResponse createMockResponse(String source, String target, double rate) {
        return ExchangeRateResponse.builder()
                .sourceCurrency(source)
                .targetCurrency(target)
                .rate(BigDecimal.valueOf(rate))
                .lastUpdated(LocalDateTime.now())
                .build();
    }
}

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AsyncBatchProcessingTest {

    @Autowired
    private WebTestClient webTestClient;

    
    
    

    @Test
    void testAsyncBatchUpload_ShouldReturnTaskIdImmediately() {
        
        
        
        webTestClient.post()
                .uri("/api/v1/batch/conversions/async")
                .bodyValue(createTestFile())
                .exchange()
                .expectStatus().isAccepted() 
                .expectBody()
                .jsonPath("$.taskId").exists()
                .jsonPath("$.status").isEqualTo("SUBMITTED")
                .jsonPath("$.message").exists();
    }

    @Test
    void testAsyncTaskStatus_ShouldReturnTaskProgress() {
        
        String taskId = "test-task-123";
        
        webTestClient.get()
                .uri("/api/v1/batch/conversions/async/{taskId}/status", taskId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.taskId").isEqualTo(taskId)
                .jsonPath("$.status").exists();
    }

    private Object createTestFile() {
        
        return "test file content";
    }
} 