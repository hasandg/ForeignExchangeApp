package com.hasandag.exchange.conversion.service.reactive;

import com.hasandag.exchange.common.client.InternalExchangeRateClient;
import com.hasandag.exchange.common.dto.ConversionRequest;
import com.hasandag.exchange.common.dto.ConversionResponse;
import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import com.hasandag.exchange.conversion.model.StreamingProcessingEvent;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class StreamingFileProcessor {

    private final InternalExchangeRateClient exchangeRateClient;

    @Value("${conversion.reactive.batch-size:100}")
    private int batchSize;

    @Value("${conversion.reactive.max-concurrency:10}")
    private int maxConcurrency;

    @Value("${conversion.reactive.backpressure-buffer:1000}")
    private int backpressureBuffer;

    public Flux<StreamingProcessingEvent> processFileWithStreaming(MultipartFile file) {
        String taskId = UUID.randomUUID().toString();
        AtomicLong processedCount = new AtomicLong(0);
        AtomicLong successCount = new AtomicLong(0);
        AtomicLong errorCount = new AtomicLong(0);

        return Mono.fromCallable(() -> {
            try {
                return new String(file.getBytes(), "UTF-8");
            } catch (IOException e) {
                throw new RuntimeException("Failed to read file content", e);
            }
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(content -> 
            Flux.concat(
                Flux.just(StreamingProcessingEvent.started(taskId, file.getOriginalFilename())),
                
                Flux.fromStream(content.lines())
                    .skip(1)
                    .map(this::parseCSVLine)
                    .onBackpressureBuffer(backpressureBuffer)
                    .bufferTimeout(batchSize, Duration.ofMillis(100))
                    .flatMap(batch -> 
                        processBatchStreaming(batch, taskId)
                            .doOnNext(event -> {
                                if (event.getEventType() == StreamingProcessingEvent.EventType.CONVERSION_SUCCESS) {
                                    successCount.incrementAndGet();
                                } else if (event.getEventType() == StreamingProcessingEvent.EventType.CONVERSION_ERROR) {
                                    errorCount.incrementAndGet();
                                }
                                processedCount.incrementAndGet();
                            })
                            .concatWith(Flux.just(StreamingProcessingEvent.progress(
                                taskId, 
                                processedCount.get(), 
                                successCount.get(), 
                                errorCount.get()
                            )))
                    , maxConcurrency),
                
                Flux.just(StreamingProcessingEvent.completed(
                    taskId, 
                    processedCount.get(), 
                    successCount.get(), 
                    errorCount.get()
                ))
            )
        )
        .onErrorResume(error -> {
            log.error("Streaming processing failed for task: {}", taskId, error);
            return Flux.just(StreamingProcessingEvent.failed(taskId, error.getMessage()));
        });
    }

    public Flux<ConversionResponse> processFileAsConversionStream(MultipartFile file) {
        return Mono.fromCallable(() -> {
            try {
                return new String(file.getBytes(), "UTF-8");
            } catch (IOException e) {
                throw new RuntimeException("Failed to read file content", e);
            }
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(content ->
            Flux.fromStream(content.lines())
                .skip(1)
                .map(this::parseCSVLine)
                .onBackpressureBuffer(backpressureBuffer)
                .flatMap(this::processConversionStreaming, maxConcurrency)
                .onErrorContinue((error, item) -> 
                    log.error("Error processing conversion: {}", item, error))
        );
    }

    public Flux<StreamingProcessingEvent> processWithLiveUpdates(MultipartFile file, Duration updateInterval) {
        String taskId = UUID.randomUUID().toString();
        AtomicLong processedCount = new AtomicLong(0);
        AtomicLong successCount = new AtomicLong(0);
        AtomicLong errorCount = new AtomicLong(0);

        return Mono.fromCallable(() -> {
            try {
                return new String(file.getBytes(), "UTF-8");
            } catch (IOException e) {
                throw new RuntimeException("Failed to read file content", e);
            }
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(content -> {
            Flux<ConversionResponse> conversionStream = Flux.fromStream(content.lines())
                .skip(1)
                .map(this::parseCSVLine)
                .flatMap(this::processConversionStreaming, maxConcurrency)
                .doOnNext(response -> {
                    successCount.incrementAndGet();
                    processedCount.incrementAndGet();
                })
                .onErrorContinue((error, item) -> {
                    errorCount.incrementAndGet();
                    processedCount.incrementAndGet();
                });

            Flux<StreamingProcessingEvent> progressStream = Flux.interval(updateInterval)
                .map(tick -> StreamingProcessingEvent.progress(
                    taskId,
                    processedCount.get(),
                    successCount.get(),
                    errorCount.get()
                ))
                .takeUntilOther(conversionStream.last());

            return Flux.merge(
                progressStream,
                conversionStream.map(response -> 
                    StreamingProcessingEvent.conversionSuccess(taskId, response)
                )
            );
        });
    }

    private Flux<StreamingProcessingEvent> processBatchStreaming(java.util.List<ConversionRequest> batch, String taskId) {
        return Flux.fromIterable(batch)
                .flatMap(request -> 
                    processConversionStreaming(request)
                        .map(response -> StreamingProcessingEvent.conversionSuccess(taskId, response))
                        .onErrorReturn(StreamingProcessingEvent.conversionError(taskId, request, "Processing failed"))
                );
    }

    private Mono<ConversionResponse> processConversionStreaming(ConversionRequest request) {
        return Mono.fromCallable(() -> 
                exchangeRateClient.getExchangeRate(request.getSourceCurrency(), request.getTargetCurrency())
            )
            .subscribeOn(Schedulers.boundedElastic())
            .map(rateResponse -> calculateConversion(request, rateResponse));
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
} 