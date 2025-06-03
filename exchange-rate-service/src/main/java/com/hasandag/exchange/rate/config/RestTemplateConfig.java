package com.hasandag.exchange.rate.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;

import java.time.Duration;
import java.util.concurrent.Executor;

@Configuration
public class RestTemplateConfig {

    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final Duration responseTimeout;

    public RestTemplateConfig(@Value("${exchange.webclient.connect-timeout:10s}") Duration connectTimeout,
                             @Value("${exchange.webclient.read-timeout:10s}") Duration readTimeout,
                             @Value("${exchange.webclient.response-timeout:15s}") Duration responseTimeout) {
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.responseTimeout = responseTimeout;
    }

    @Bean
    public RestTemplate restTemplate() {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(100);
        connectionManager.setDefaultMaxPerRoute(20);

        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(connectTimeout.toMillis()))
                .setResponseTimeout(Timeout.ofMilliseconds(readTimeout.toMillis()))
                .build();

        CloseableHttpClient client = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(config)
                .build();

        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setHttpClient(client);
        factory.setConnectTimeout((int) connectTimeout.toMillis());
        factory.setConnectionRequestTimeout((int) responseTimeout.toMillis());

        return new RestTemplate(factory);
    }

    @Bean("virtualThreadExecutor")
    public Executor virtualThreadExecutor() {
        return task -> Thread.ofVirtual().name("http-client-", 0).start(task);
    }

    @Bean("asyncVirtualThreadExecutor")
    public Executor asyncVirtualThreadExecutor() {
        return task -> Thread.ofVirtual().name("async-", 0).start(task);
    }
} 