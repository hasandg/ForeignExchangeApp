package com.hasandag.exchange.rate.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * RestClient configuration - Spring's modern alternative to RestTemplate
 * Perfect for virtual threads and provides a more fluent API
 */
@Configuration
@Slf4j
public class RestClientConfig {

    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final Duration responseTimeout;

    public RestClientConfig(@Value("${exchange.webclient.connect-timeout:10s}") Duration connectTimeout,
                           @Value("${exchange.webclient.read-timeout:10s}") Duration readTimeout,
                           @Value("${exchange.webclient.response-timeout:15s}") Duration responseTimeout) {
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.responseTimeout = responseTimeout;
    }

    @Bean("exchangeRateApiRestClient")
    public RestClient exchangeRateRestClient(@Value("${exchange.api.url}") String baseUrl) {
        log.info("Creating RestClient for exchange rate API with base URL: {}", baseUrl);
        
        // Connection pooling (same as RestTemplate)
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(100);
        connectionManager.setDefaultMaxPerRoute(20);
        
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(connectTimeout.toMillis()))
                .setResponseTimeout(Timeout.ofMilliseconds(readTimeout.toMillis()))
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(responseTimeout.toMillis()))
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(config)
                .evictExpiredConnections()
                .evictIdleConnections(Timeout.ofSeconds(30))
                .build();

        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setHttpClient(httpClient);

        // RestClient with fluent API
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeaders(headers -> {
                    headers.add("User-Agent", "Exchange-Rate-Service/1.0");
                    headers.add("Accept", "application/json");
                })
                .defaultStatusHandler(
                    status -> status.is5xxServerError(),
                    (request, response) -> {
                        log.error("Server error: {} - {}", response.getStatusCode(), 
                                new String(response.getBody().readAllBytes()));
                        throw new RuntimeException("Server error: " + response.getStatusCode());
                    }
                )
                .build();
    }

    @Bean("generalPurposeRestClient")
    public RestClient generalPurposeRestClient() {
        log.info("Creating general purpose RestClient for external services");
        
        // Larger connection pool for general use
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(200);
        connectionManager.setDefaultMaxPerRoute(50);
        
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(connectTimeout.toMillis()))
                .setResponseTimeout(Timeout.ofMilliseconds(readTimeout.toMillis()))
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(config)
                .build();

        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setHttpClient(httpClient);

        return RestClient.builder()
                .requestFactory(factory)
                .defaultHeaders(headers -> {
                    headers.add("User-Agent", "Exchange-Rate-Service/1.0");
                })
                .build();
    }

    @Bean("virtualThreadOptimizedRestClient")
    public RestClient virtualThreadOptimizedRestClient() {
        log.info("Creating virtual thread optimized RestClient");
        
        // High-capacity connection pool for virtual threads
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(1000);
        connectionManager.setDefaultMaxPerRoute(200);
        
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(5))
                .setResponseTimeout(Timeout.ofSeconds(30))
                .setConnectionRequestTimeout(Timeout.ofSeconds(2))
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(config)
                .evictExpiredConnections()
                .evictIdleConnections(Timeout.ofSeconds(60))
                .build();

        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setHttpClient(httpClient);

        return RestClient.builder()
                .requestFactory(factory)
                .defaultHeaders(headers -> {
                    headers.add("User-Agent", "Exchange-Rate-Service-VirtualThreads/1.0");
                })
                .build();
    }
} 