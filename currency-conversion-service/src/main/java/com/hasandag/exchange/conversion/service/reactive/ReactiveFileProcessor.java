package com.hasandag.exchange.conversion.service.reactive;

import com.hasandag.exchange.common.client.InternalExchangeRateClient;
import com.hasandag.exchange.common.dto.ConversionRequest;
import com.hasandag.exchange.common.dto.ConversionResponse;
import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import com.hasandag.exchange.conversion.kafka.producer.ConversionEventProducer;
import com.hasandag.exchange.conversion.model.ReactiveProcessingResult;
import com.hasandag.exchange.conversion.repository.command.CurrencyConversionMongoRepository;
import com.hasandag.exchange.conversion.repository.query.CurrencyConversionPostgresRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReactiveFileProcessor {

    private final InternalExchangeRateClient exchangeRateClient;
    private final ConversionEventProducer eventProducer;
    private final CurrencyConversionMongoRepository mongoRepository;
    private final CurrencyConversionPostgresRepository postgresRepository;

    @Value("${conversion.reactive.batch-size:100}")
    private int batchSize;

    @Value("${conversion.reactive.max-concurrency:10}")
    private int maxConcurrency;

    @Value("${conversion.reactive.backpressure-buffer:1000}")
    private int backpressureBuffer;

    public Mono<ReactiveProcessingResult> processLargeFileWithBackpressure(MultipartFile file) {
        String taskId = UUID.randomUUID().toString();
        log.info("Starting reactive processing for large file: {} with task ID: {}", file.getOriginalFilename(), taskId);

        return Mono.fromCallable(() -> {
            try {
                return new String(file.getBytes(), "UTF-8");
            } catch (IOException e) {
                throw new RuntimeException("Failed to read file content", e);
            }
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(content -> processContentWithBackpressure(content, taskId, file.getOriginalFilename()))
        .onErrorResume(error -> {
            log.error("Large file processing failed for task: {}", taskId, error);
            return Mono.just(ReactiveProcessingResult.failed(taskId, error.getMessage()));
        });
    }

    public Mono<ReactiveProcessingResult> processWithControlledBackpressure(MultipartFile file) {
        String taskId = UUID.randomUUID().toString();
        log.info("Starting backpressure-controlled processing for file: {} with task ID: {}", file.getOriginalFilename(), taskId);

        return Mono.fromCallable(() -> {
            try {
                return new String(file.getBytes(), "UTF-8");
            } catch (IOException e) {
                throw new RuntimeException("Failed to read file content", e);
            }
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(content -> processWithAdaptiveBackpressure(content, taskId, file.getOriginalFilename()))
        .onErrorResume(error -> {
            log.error("Backpressure processing failed for task: {}", taskId, error);
            return Mono.just(ReactiveProcessingResult.failed(taskId, error.getMessage()));
        });
    }

    private Mono<ReactiveProcessingResult> processContentWithBackpressure(String content, String taskId, String filename) {
        AtomicLong processedCount = new AtomicLong(0);
        AtomicLong successCount = new AtomicLong(0);
        AtomicLong errorCount = new AtomicLong(0);

        return Flux.fromStream(content.lines())
                .skip(1)
                .map(this::parseCSVLine)
                .onBackpressureBuffer(backpressureBuffer)
                .window(batchSize)
                .flatMap(window -> 
                    window.collectList()
                        .flatMap(batch -> processBatchReactive(batch, taskId))
                        .doOnNext(results -> {
                            processedCount.addAndGet(results.size());
                            successCount.addAndGet(results.stream().mapToLong(r -> r.isSuccess() ? 1 : 0).sum());
                            errorCount.addAndGet(results.stream().mapToLong(r -> r.isSuccess() ? 0 : 1).sum());
                        })
                        .onErrorResume(error -> {
                            log.error("Batch processing error in task: {}", taskId, error);
                            errorCount.addAndGet(batchSize);
                            return Mono.just(List.of());
                        }),
                    maxConcurrency
                )
                .then(Mono.fromCallable(() -> 
                    ReactiveProcessingResult.success(
                        taskId, 
                        filename, 
                        processedCount.get(), 
                        successCount.get(), 
                        errorCount.get(),
                        "LARGE_FILE_STREAMING",
                        batchSize
                    )
                ));
    }

    private Mono<ReactiveProcessingResult> processWithAdaptiveBackpressure(String content, String taskId, String filename) {
        AtomicLong processedCount = new AtomicLong(0);
        AtomicLong successCount = new AtomicLong(0);
        AtomicLong errorCount = new AtomicLong(0);

        return Flux.fromStream(content.lines())
                .skip(1)
                .map(this::parseCSVLine)
                .bufferTimeout(batchSize, Duration.ofMillis(100))
                .flatMap(batch -> {
                    if (batch.size() < batchSize / 2) {
                        return processBatchReactive(batch, taskId)
                                .delayElement(Duration.ofMillis(50));
                    } else {
                        return processBatchReactive(batch, taskId);
                    }
                }, maxConcurrency / 2)
                .doOnNext(results -> {
                    processedCount.addAndGet(results.size());
                    successCount.addAndGet(results.stream().mapToLong(r -> r.isSuccess() ? 1 : 0).sum());
                    errorCount.addAndGet(results.stream().mapToLong(r -> r.isSuccess() ? 0 : 1).sum());
                })
                .onErrorContinue((error, item) -> {
                    log.error("Item processing error in task: {}", taskId, error);
                    errorCount.incrementAndGet();
                })
                .then(Mono.fromCallable(() -> 
                    ReactiveProcessingResult.success(
                        taskId, 
                        filename, 
                        processedCount.get(), 
                        successCount.get(), 
                        errorCount.get(),
                        "ADAPTIVE_BACKPRESSURE",
                        batchSize / 2
                    )
                ));
    }

    private Mono<List<ConversionProcessingResult>> processBatchReactive(List<ConversionRequest> batch, String taskId) {
        return Flux.fromIterable(batch)
                .flatMap(request -> processConversionReactive(request, taskId)
                        .onErrorResume(error -> {
                            log.error("Conversion error for request in task: {}", taskId, error);
                            return Mono.just(ConversionProcessingResult.failed(request, error.getMessage()));
                        })
                )
                .collectList();
    }

    private Mono<ConversionProcessingResult> processConversionReactive(ConversionRequest request, String taskId) {
        return Mono.fromCallable(() -> exchangeRateClient.getExchangeRate(request.getSourceCurrency(), request.getTargetCurrency()))
                .subscribeOn(Schedulers.boundedElastic())
                .map(rateResponse -> calculateConversion(request, rateResponse))
                .flatMap(this::saveConversionReactive)
                .map(ConversionProcessingResult::success)
                .onErrorMap(error -> new RuntimeException("Conversion processing failed: " + error.getMessage(), error));
    }

    private ConversionResponse calculateConversion(ConversionRequest request, ExchangeRateResponse rateResponse) {
        BigDecimal targetAmount = request.getSourceAmount()
                .multiply(rateResponse.getRate())
                .setScale(2, RoundingMode.HALF_UP);

        return ConversionResponse.builder()
                .transactionId(UUID.randomUUID().toString())
                .sourceCurrency(request.getSourceCurrency())
                .targetCurrency(request.getTargetCurrency())
                .sourceAmount(request.getSourceAmount())
                .targetAmount(targetAmount)
                .exchangeRate(rateResponse.getRate())
                .timestamp(LocalDateTime.now())
                .build();
    }

    private Mono<ConversionResponse> saveConversionReactive(ConversionResponse response) {
        return Mono.fromCallable(() -> {
            log.debug("Saving conversion reactively: {}", response.getTransactionId());
            return response;
        })
        .subscribeOn(Schedulers.boundedElastic());
    }

    private ConversionRequest parseCSVLine(String line) {
        String[] fields = line.split(",");
        if (fields.length < 3) {
            throw new IllegalArgumentException("Invalid CSV line format: " + line);
        }

        return ConversionRequest.builder()
                .sourceAmount(new BigDecimal(fields[0].trim()))
                .sourceCurrency(fields[1].trim().toUpperCase())
                .targetCurrency(fields[2].trim().toUpperCase())
                .build();
    }

    public static class ConversionProcessingResult {
        private final ConversionRequest request;
        private final ConversionResponse response;
        private final boolean success;
        private final String errorMessage;

        private ConversionProcessingResult(ConversionRequest request, ConversionResponse response, boolean success, String errorMessage) {
            this.request = request;
            this.response = response;
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public static ConversionProcessingResult success(ConversionResponse response) {
            return new ConversionProcessingResult(null, response, true, null);
        }

        public static ConversionProcessingResult failed(ConversionRequest request, String errorMessage) {
            return new ConversionProcessingResult(request, null, false, errorMessage);
        }

        public boolean isSuccess() { return success; }
        public ConversionResponse getResponse() { return response; }
        public String getErrorMessage() { return errorMessage; }
    }
} 