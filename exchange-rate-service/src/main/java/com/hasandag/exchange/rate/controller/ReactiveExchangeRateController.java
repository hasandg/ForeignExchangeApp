package com.hasandag.exchange.rate.controller;

import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import com.hasandag.exchange.rate.service.reactive.ReactiveExchangeRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/exchange-rates/reactive")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Reactive Exchange Rates", description = "Reactive streaming endpoints with backpressure support")
public class ReactiveExchangeRateController {

    private final ReactiveExchangeRateService reactiveExchangeRateService;

    @GetMapping(value = "/single")
    @Operation(summary = "Get single exchange rate reactively")
    public Mono<ResponseEntity<ExchangeRateResponse>> getExchangeRateReactive(
            @RequestParam String sourceCurrency,
            @RequestParam String targetCurrency) {
        
        log.debug("Reactive exchange rate request: {} -> {}", sourceCurrency, targetCurrency);
        
        return reactiveExchangeRateService.getExchangeRateReactive(sourceCurrency, targetCurrency)
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    log.error("Error getting reactive exchange rate", error);
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    @GetMapping(value = "/stream/{baseCurrency}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream real-time exchange rates with backpressure")
    public Flux<ExchangeRateResponse> streamExchangeRates(@PathVariable String baseCurrency) {
        log.info("Starting real-time exchange rate stream for base currency: {}", baseCurrency);
        
        return reactiveExchangeRateService.streamRealTimeRates(baseCurrency)
                .onBackpressureLatest()
                .doOnSubscribe(subscription -> 
                    log.info("Client subscribed to exchange rate stream for: {}", baseCurrency))
                .doOnCancel(() -> 
                    log.info("Client cancelled exchange rate stream for: {}", baseCurrency))
                .doOnComplete(() -> 
                    log.info("Exchange rate stream completed for: {}", baseCurrency));
    }

    @PostMapping("/batch")
    @Operation(summary = "Get multiple exchange rates with backpressure control")
    public Mono<ResponseEntity<Map<String, ExchangeRateResponse>>> getBatchExchangeRates(
            @RequestBody Flux<String> currencyPairs,
            @RequestParam(defaultValue = "10") int batchSize) {
        
        log.debug("Batch exchange rate request with batch size: {}", batchSize);
        
        return reactiveExchangeRateService.getBatchExchangeRates(currencyPairs, batchSize)
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    log.error("Error getting batch exchange rates", error);
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    @GetMapping(value = "/multiple", produces = MediaType.APPLICATION_NDJSON_VALUE)
    @Operation(summary = "Get multiple exchange rates as streaming NDJSON")
    public Flux<ExchangeRateResponse> getMultipleExchangeRatesStream(
            @RequestParam String[] pairs) {
        
        log.debug("Multiple exchange rates stream request for {} pairs", pairs.length);
        
        return Flux.fromArray(pairs)
                .onBackpressureBuffer(1000)
                .flatMap(pair -> {
                    String[] currencies = pair.split("-");
                    if (currencies.length != 2) {
                        return Mono.empty();
                    }
                    return reactiveExchangeRateService.getExchangeRateReactive(currencies[0], currencies[1]);
                }, 5)
                .onErrorContinue((error, item) -> 
                    log.warn("Error processing currency pair: {}", item, error));
    }

    @GetMapping("/cache/stats")
    @Operation(summary = "Get reactive service cache statistics")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        return ResponseEntity.ok(Map.of(
            "cacheSize", reactiveExchangeRateService.getCacheSize(),
            "cacheType", "ConcurrentHashMap",
            "cachingStrategy", "In-memory with TTL"
        ));
    }

    @PostMapping("/cache/clear")
    @Operation(summary = "Clear reactive service cache")
    public ResponseEntity<Map<String, Object>> clearCache() {
        reactiveExchangeRateService.clearCache();
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Cache cleared successfully"
        ));
    }

    @GetMapping(value = "/sample", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Sample exchange rates with controlled frequency")
    public Flux<ExchangeRateResponse> sampleExchangeRates(
            @RequestParam String baseCurrency,
            @RequestParam(defaultValue = "5") int intervalSeconds) {
        
        log.info("Starting sampled exchange rate stream: {} with {}s interval", baseCurrency, intervalSeconds);
        
        return reactiveExchangeRateService.streamRealTimeRates(baseCurrency)
                .sample(Duration.ofSeconds(intervalSeconds))
                .onBackpressureDrop(dropped -> 
                    log.debug("Dropped exchange rate update due to backpressure: {}", dropped))
                .doOnNext(rate -> 
                    log.debug("Emitting sampled rate: {} -> {} = {}", 
                            rate.getSourceCurrency(), rate.getTargetCurrency(), rate.getRate()));
    }
} 