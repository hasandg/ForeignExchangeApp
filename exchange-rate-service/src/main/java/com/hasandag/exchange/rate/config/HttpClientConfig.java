package com.hasandag.exchange.rate.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
@Slf4j
public class HttpClientConfig {

    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final Duration virtualThreadsConnectTimeout;
    private final Duration virtualThreadsReadTimeout;
    private final String defaultUserAgent;
    private final String virtualThreadsUserAgent;
    private final int retrySchedulerThreads;
    private final int maxConnectionsTotal;
    private final int maxConnectionsPerRoute;

    public HttpClientConfig(
            @Value("${exchange.webclient.connect-timeout:10s}") Duration connectTimeout,
            @Value("${exchange.webclient.read-timeout:10s}") Duration readTimeout,
            @Value("${exchange.virtual-threads.connect-timeout:5s}") Duration virtualThreadsConnectTimeout,
            @Value("${exchange.virtual-threads.read-timeout:30s}") Duration virtualThreadsReadTimeout,
            @Value("${exchange.user-agent.default:Exchange-Rate-Service/1.0}") String defaultUserAgent,
            @Value("${exchange.user-agent.virtual-threads:Exchange-Rate-Service-VirtualThreads/1.0}") String virtualThreadsUserAgent,
            @Value("${exchange.retry.scheduler.threads:2}") int retrySchedulerThreads,
            @Value("${exchange.httpclient.max-connections-total:100}") int maxConnectionsTotal,
            @Value("${exchange.httpclient.max-connections-per-route:20}") int maxConnectionsPerRoute) {
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.virtualThreadsConnectTimeout = virtualThreadsConnectTimeout;
        this.virtualThreadsReadTimeout = virtualThreadsReadTimeout;
        this.defaultUserAgent = defaultUserAgent;
        this.virtualThreadsUserAgent = virtualThreadsUserAgent;
        this.retrySchedulerThreads = retrySchedulerThreads;
        this.maxConnectionsTotal = maxConnectionsTotal;
        this.maxConnectionsPerRoute = maxConnectionsPerRoute;
    }

    @Bean("retryScheduler")
    public ScheduledExecutorService retryScheduler() {
        log.info("Creating retry scheduler with {} threads", retrySchedulerThreads);
        return Executors.newScheduledThreadPool(retrySchedulerThreads, 
            r -> {
                Thread thread = new Thread(r, "retry-scheduler-");
                thread.setDaemon(true);
                return thread;
            });
    }

    @Bean("exchangeRateApiHttpClient")
    public CloseableHttpClient exchangeRateApiHttpClient() {
        log.info("Creating Apache HttpClient 5 for exchange rate API");
        
        // Connection configuration
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.of(connectTimeout))
                .setSocketTimeout(Timeout.of(readTimeout))
                .build();

        // Socket configuration
        SocketConfig socketConfig = SocketConfig.custom()
                .setSoTimeout(Timeout.of(readTimeout))
                .build();

        // Connection manager
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(maxConnectionsTotal);
        connectionManager.setDefaultMaxPerRoute(maxConnectionsPerRoute);
        connectionManager.setDefaultConnectionConfig(connectionConfig);
        connectionManager.setDefaultSocketConfig(socketConfig);

        // Request configuration
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.of(connectTimeout))
                .setResponseTimeout(Timeout.of(readTimeout))
                .build();

        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .setUserAgent(defaultUserAgent)
                .build();
    }

    @Bean("generalPurposeHttpClient")
    public CloseableHttpClient generalPurposeHttpClient() {
        log.info("Creating general purpose Apache HttpClient 5");
        
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.of(connectTimeout))
                .setSocketTimeout(Timeout.of(readTimeout))
                .build();

        SocketConfig socketConfig = SocketConfig.custom()
                .setSoTimeout(Timeout.of(readTimeout))
                .build();

        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(maxConnectionsTotal);
        connectionManager.setDefaultMaxPerRoute(maxConnectionsPerRoute);
        connectionManager.setDefaultConnectionConfig(connectionConfig);
        connectionManager.setDefaultSocketConfig(socketConfig);

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.of(connectTimeout))
                .setResponseTimeout(Timeout.of(readTimeout))
                .build();

        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .setUserAgent(defaultUserAgent)
                .build();
    }

    @Bean("virtualThreadOptimizedHttpClient")
    public CloseableHttpClient virtualThreadOptimizedHttpClient() {
        log.info("Creating virtual thread optimized Apache HttpClient 5");
        
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.of(virtualThreadsConnectTimeout))
                .setSocketTimeout(Timeout.of(virtualThreadsReadTimeout))
                .build();

        SocketConfig socketConfig = SocketConfig.custom()
                .setSoTimeout(Timeout.of(virtualThreadsReadTimeout))
                .build();

        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(maxConnectionsTotal);
        connectionManager.setDefaultMaxPerRoute(maxConnectionsPerRoute);
        connectionManager.setDefaultConnectionConfig(connectionConfig);
        connectionManager.setDefaultSocketConfig(socketConfig);

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.of(virtualThreadsConnectTimeout))
                .setResponseTimeout(Timeout.of(virtualThreadsReadTimeout))
                .build();

        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .setUserAgent(virtualThreadsUserAgent)
                .build();
    }
} 