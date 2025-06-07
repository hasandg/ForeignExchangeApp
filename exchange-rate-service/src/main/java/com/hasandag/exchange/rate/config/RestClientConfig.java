package com.hasandag.exchange.rate.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@Slf4j
public class RestClientConfig {

    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final Duration virtualThreadsConnectTimeout;
    private final Duration virtualThreadsReadTimeout;
    private final String defaultUserAgent;
    private final String virtualThreadsUserAgent;

    public RestClientConfig(
            @Value("${exchange.webclient.connect-timeout:10s}") Duration connectTimeout,
            @Value("${exchange.webclient.read-timeout:10s}") Duration readTimeout,
            @Value("${exchange.virtual-threads.connect-timeout:5s}") Duration virtualThreadsConnectTimeout,
            @Value("${exchange.virtual-threads.read-timeout:30s}") Duration virtualThreadsReadTimeout,
            @Value("${exchange.user-agent.default:Exchange-Rate-Service/1.0}") String defaultUserAgent,
            @Value("${exchange.user-agent.virtual-threads:Exchange-Rate-Service-VirtualThreads/1.0}") String virtualThreadsUserAgent) {
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.virtualThreadsConnectTimeout = virtualThreadsConnectTimeout;
        this.virtualThreadsReadTimeout = virtualThreadsReadTimeout;
        this.defaultUserAgent = defaultUserAgent;
        this.virtualThreadsUserAgent = virtualThreadsUserAgent;
    }

    @Bean("exchangeRateApiRestClient")
    public RestClient exchangeRateRestClient(@Value("${exchange.api.url}") String baseUrl) {
        log.info("Creating RestClient for exchange rate API with base URL: {}", baseUrl);
        
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeaders(headers -> {
                    headers.add("User-Agent", defaultUserAgent);
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
        
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        return RestClient.builder()
                .requestFactory(factory)
                .defaultHeaders(headers -> {
                    headers.add("User-Agent", defaultUserAgent);
                })
                .build();
    }

    @Bean("virtualThreadOptimizedRestClient")
    public RestClient virtualThreadOptimizedRestClient() {
        log.info("Creating virtual thread optimized RestClient");
        
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(virtualThreadsConnectTimeout);
        factory.setReadTimeout(virtualThreadsReadTimeout);

        return RestClient.builder()
                .requestFactory(factory)
                .defaultHeaders(headers -> {
                    headers.add("User-Agent", virtualThreadsUserAgent);
                })
                .build();
    }
} 