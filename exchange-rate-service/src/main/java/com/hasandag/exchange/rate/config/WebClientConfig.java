package com.hasandag.exchange.rate.config;

import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
@Slf4j
public class WebClientConfig {

    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final String userAgent;

    public WebClientConfig(
            @Value("${exchange.connect-timeout:5s}") Duration connectTimeout,
            @Value("${exchange.read-timeout:30s}") Duration readTimeout,
            @Value("${exchange.user-agent.default:Exchange-Rate-Service/1.0}") String userAgent) {
        
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.userAgent = userAgent;
        
        log.info("WebClient configured with: connectTimeout={}, readTimeout={}, userAgent={}",
                connectTimeout, readTimeout, userAgent);
    }

    @Bean("exchangeRateApiWebClient")
    public WebClient exchangeRateApiWebClient() {
        log.info("Creating exchange rate API WebClient");
        
        ConnectionProvider connectionProvider = ConnectionProvider.builder("exchange-rate-api")
                .maxConnections(100)
                .maxIdleTime(Duration.ofSeconds(20))
                .maxLifeTime(Duration.ofMinutes(5))
                .pendingAcquireMaxCount(256)
                .evictInBackground(Duration.ofSeconds(120))
                .build();

        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis())
                .responseTimeout(readTimeout);

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("User-Agent", userAgent)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
    }

    @Bean("generalPurposeWebClient")
    public WebClient generalPurposeWebClient() {
        log.info("Creating general purpose WebClient");
        
        ConnectionProvider connectionProvider = ConnectionProvider.builder("general-purpose")
                .maxConnections(50)
                .maxIdleTime(Duration.ofSeconds(30))
                .maxLifeTime(Duration.ofMinutes(10))
                .pendingAcquireMaxCount(128)
                .evictInBackground(Duration.ofSeconds(60))
                .build();

        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis())
                .responseTimeout(readTimeout);

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("User-Agent", userAgent)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(512 * 1024))
                .build();
    }

    @Bean("retryScheduler")
    public ScheduledExecutorService retryScheduler() {
        log.info("Creating retry scheduler");
        return Executors.newScheduledThreadPool(2, r -> {
            Thread thread = new Thread(r, "retry-scheduler");
            thread.setDaemon(true);
            return thread;
        });
    }
} 