package com.hasandag.exchange.rate.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Demonstrates different HTTP client configurations for RestTemplate
 * This class shows various options for virtual threads compatibility
 */
@Configuration
@Slf4j
public class HttpClientComparisonExample {

    // 1. DEFAULT: Simple HTTP Client (NOT recommended for production)
    @Bean("simpleRestTemplate")
    public RestTemplate simpleRestTemplate() {
        log.info("Creating RestTemplate with Simple HTTP Client (default)");
        
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(10));
        
        // Pros: Simple, no dependencies
        // Cons: No connection pooling, poor performance, not ideal for virtual threads
        return new RestTemplate(factory);
    }

    // 2. APACHE HTTP COMPONENTS CLIENT 5 (Our current choice - RECOMMENDED)
    @Bean("apacheHttpClient5RestTemplate")
    public RestTemplate apacheHttpClient5RestTemplate() {
        log.info("Creating RestTemplate with Apache HTTP Components Client 5");
        
        // Connection pooling configuration
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(200);              // Total pool size
        connectionManager.setDefaultMaxPerRoute(50);     // Per-route pool size
        
        // Request configuration
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(10))
                .setResponseTimeout(Timeout.ofSeconds(15))
                .setConnectionRequestTimeout(Timeout.ofSeconds(5))  // Time to get connection from pool
                .build();
        
        // Build HTTP client
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .evictExpiredConnections()                    // Cleanup expired connections
                .evictIdleConnections(Timeout.ofSeconds(30)) // Cleanup idle connections
                .build();
        
        // Create request factory
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setHttpClient(httpClient);
        
        // Pros: Excellent connection pooling, production-ready, perfect for virtual threads
        // Cons: Additional dependency
        return new RestTemplate(factory);
    }

    // 3. VIRTUAL THREAD OPTIMIZED CONFIGURATION
    @Bean("virtualThreadOptimizedRestTemplate")
    public RestTemplate virtualThreadOptimizedRestTemplate() {
        log.info("Creating RestTemplate optimized for Virtual Threads");
        
        // Large connection pool for virtual threads
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(1000);           // High total for many virtual threads
        connectionManager.setDefaultMaxPerRoute(200);  // High per-route for concurrent requests
        
        // Optimized timeouts for virtual threads
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(5))           // Faster connection timeout
                .setResponseTimeout(Timeout.ofSeconds(30))         // Longer response timeout
                .setConnectionRequestTimeout(Timeout.ofSeconds(2)) // Fast pool access
                .build();
        
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .evictExpiredConnections()
                .evictIdleConnections(Timeout.ofSeconds(60))
                .build();
        
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setHttpClient(httpClient);
        
        return new RestTemplate(factory);
    }

    // Performance comparison method
    public void demonstratePerformanceDifference() {
        log.info("=== HTTP Client Performance Comparison ===");
        
        log.info("1. Simple Client (Default):");
        log.info("   - Connection per request: NEW TCP CONNECTION");
        log.info("   - Virtual thread impact: HIGH (connection overhead)");
        log.info("   - Concurrent requests: LIMITED (no pooling)");
        log.info("   - Memory usage: HIGH (connection objects)");
        
        log.info("2. Apache HTTP Components Client 5 (Recommended):");
        log.info("   - Connection per request: POOLED CONNECTION REUSE");
        log.info("   - Virtual thread impact: LOW (efficient connection reuse)");
        log.info("   - Concurrent requests: HIGH (1000+ with proper pooling)");
        log.info("   - Memory usage: LOW (shared connection pool)");
        
        log.info("3. Performance Impact:");
        log.info("   - 1000 concurrent requests with Simple Client: ~5000ms + high memory");
        log.info("   - 1000 concurrent requests with Apache HC: ~500ms + low memory");
        log.info("   - Connection establishment overhead: 100-300ms per new connection");
        log.info("   - Pooled connection reuse: ~1-5ms per request");
    }

    // Virtual threads specific benefits
    public void explainVirtualThreadBenefits() {
        log.info("=== Virtual Threads + HTTP Client Benefits ===");
        
        log.info("Traditional Thread Pool Limitations:");
        log.info("   - Platform threads: Limited to ~200-500 concurrent threads");
        log.info("   - Each thread: ~2MB stack memory");
        log.info("   - Context switching: Expensive CPU overhead");
        
        log.info("Virtual Threads Advantages:");
        log.info("   - Virtual threads: Millions of concurrent threads possible");
        log.info("   - Each virtual thread: ~Few KB memory");
        log.info("   - Context switching: Minimal overhead");
        log.info("   - Blocking operations: Don't block carrier threads");
        
        log.info("Why HTTP Client matters with Virtual Threads:");
        log.info("   - Connection pooling reduces TCP overhead");
        log.info("   - Blocking I/O works perfectly with virtual threads");
        log.info("   - No need for reactive programming complexity");
        log.info("   - Simple, readable synchronous code");
    }
} 