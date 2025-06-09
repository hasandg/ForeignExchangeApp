package com.hasandag.exchange.rate.service.reactive;

import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import com.hasandag.exchange.common.exception.RateServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReactiveExchangeRateService {

    @Qualifier("exchangeRateApiWebClient")
    private final WebClient webClient;

    @Value("${exchange.api.rate-limit-status-code:429}")
    private int rateLimitStatusCode;

    @Value("${exchange.reactive.max-retries:3}")
    private int maxRetries;

    @Value("${exchange.reactive.base-delay:1000}")
    private long baseDelayMs;

    private final Map<String, ExchangeRateResponse> rateCache = new ConcurrentHashMap<>();

    public Mono<ExchangeRateResponse> getExchangeRateReactive(String sourceCurrency, String targetCurrency) {
        String cacheKey = sourceCurrency + "-" + targetCurrency;
        
        return Mono.fromCallable(() -> rateCache.get(cacheKey))
                .filter(cached -> cached != null)
                .switchIfEmpty(fetchExchangeRateFromApiReactive(sourceCurrency, targetCurrency))
                .doOnNext(rate -> rateCache.put(cacheKey, rate))
                .doOnNext(rate -> log.debug("Retrieved exchange rate reactively: {} -> {} = {}", 
                        sourceCurrency, targetCurrency, rate.getRate()));
    }

    public Flux<ExchangeRateResponse> getMultipleExchangeRatesReactive(Flux<String> currencyPairs) {
        return currencyPairs
                .flatMap(pair -> {
                    String[] currencies = pair.split("-");
                    if (currencies.length != 2) {
                        return Mono.error(new IllegalArgumentException("Invalid currency pair format: " + pair));
                    }
                    return getExchangeRateReactive(currencies[0], currencies[1]);
                }, 5)
                .onBackpressureBuffer(1000)
                .onErrorContinue((error, item) -> 
                    log.error("Error processing currency pair: {}", item, error));
    }

    public Flux<ExchangeRateResponse> streamRealTimeRates(String baseCurrency) {
        return Flux.interval(Duration.ofSeconds(1))
                .flatMap(tick -> 
                    Flux.just("USD", "EUR", "GBP", "JPY", "CHF", "CAD", "AUD")
                            .filter(currency -> !currency.equals(baseCurrency))
                            .flatMap(targetCurrency -> 
                                getExchangeRateReactive(baseCurrency, targetCurrency)
                                    .onErrorResume(error -> {
                                        log.warn("Failed to get rate for {}-{}: {}", baseCurrency, targetCurrency, error.getMessage());
                                        return Mono.empty();
                                    })
                            )
                )
                .distinctUntilChanged(ExchangeRateResponse::getRate)
                .sample(Duration.ofSeconds(2))
                .doOnNext(rate -> log.debug("Streaming rate update: {} -> {} = {}", 
                        rate.getSourceCurrency(), rate.getTargetCurrency(), rate.getRate()));
    }

    public Mono<Map<String, ExchangeRateResponse>> getBatchExchangeRates(Flux<String> currencyPairs, int batchSize) {
        return currencyPairs
                .buffer(batchSize)
                .flatMap(batch -> 
                    Flux.fromIterable(batch)
                            .flatMap(pair -> {
                                String[] currencies = pair.split("-");
                                return getExchangeRateReactive(currencies[0], currencies[1])
                                        .map(rate -> Map.entry(pair, rate));
                            })
                            .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                )
                .reduce((map1, map2) -> {
                    map1.putAll(map2);
                    return map1;
                });
    }

    private Mono<ExchangeRateResponse> fetchExchangeRateFromApiReactive(String sourceCurrency, String targetCurrency) {
        log.debug("Making reactive HTTP call: {}-{}", sourceCurrency, targetCurrency);
        
        return webClient
                .get()
                .uri("/{sourceCurrency}", sourceCurrency)
                .header("X-Request-ID", java.util.UUID.randomUUID().toString())
                .header("X-Source-Currency", sourceCurrency)
                .header("X-Target-Currency", targetCurrency)
                .retrieve()
                .onStatus(status -> status.value() == rateLimitStatusCode,
                        clientResponse -> Mono.error(new RateServiceException("External API rate limit exceeded")))
                .onStatus(HttpStatusCode::is4xxClientError,
                        clientResponse -> Mono.error(new RateServiceException("Client error: " + clientResponse.statusCode())))
                .onStatus(HttpStatusCode::is5xxServerError,
                        clientResponse -> Mono.error(new RateServiceException("Server error: " + clientResponse.statusCode())))
                .bodyToMono(Map.class)
                .map(responseMap -> parseExchangeRateResponse(responseMap, sourceCurrency, targetCurrency))
                .retryWhen(Retry.backoff(maxRetries, Duration.ofMillis(baseDelayMs))
                        .filter(throwable -> !(throwable instanceof RateServiceException) || 
                                !throwable.getMessage().contains("rate limit"))
                        .doBeforeRetry(retrySignal -> 
                            log.warn("Retrying exchange rate request for {}-{}, attempt: {}", 
                                    sourceCurrency, targetCurrency, retrySignal.totalRetries() + 1))
                )
                .doOnError(error -> log.error("Failed to fetch exchange rate for {}-{}: {}", 
                        sourceCurrency, targetCurrency, error.getMessage()))
                .onErrorMap(error -> new RateServiceException("Failed to get exchange rate: " + error.getMessage()));
    }

    private ExchangeRateResponse parseExchangeRateResponse(Map<String, Object> responseMap, String sourceCurrency, String targetCurrency) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> rates = (Map<String, Object>) responseMap.get("rates");
            
            if (rates == null || !rates.containsKey(targetCurrency)) {
                throw new RateServiceException("Target currency rate not found: " + targetCurrency);
            }

            Object rateValue = rates.get(targetCurrency);
            java.math.BigDecimal rate;
            
            if (rateValue instanceof Number) {
                rate = new java.math.BigDecimal(rateValue.toString());
            } else {
                rate = new java.math.BigDecimal(rateValue.toString());
            }

            return ExchangeRateResponse.builder()
                    .sourceCurrency(sourceCurrency)
                    .targetCurrency(targetCurrency)
                    .rate(rate)
                    .lastUpdated(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Error parsing exchange rate response: {}", responseMap, e);
            throw new RateServiceException("Invalid response format from exchange rate API");
        }
    }

    @Cacheable(value = "exchangeRates", key = "#sourceCurrency + '-' + #targetCurrency")
    public Mono<ExchangeRateResponse> getCachedExchangeRateReactive(String sourceCurrency, String targetCurrency) {
        return getExchangeRateReactive(sourceCurrency, targetCurrency)
                .cache(Duration.ofSeconds(30));
    }

    public void clearCache() {
        rateCache.clear();
        log.info("Exchange rate cache cleared");
    }

    public int getCacheSize() {
        return rateCache.size();
    }
} 