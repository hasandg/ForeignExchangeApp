package com.hasandag.exchange.conversion.service;

import com.hasandag.exchange.conversion.model.BatchJobResponse;
import com.hasandag.exchange.conversion.service.reactive.ReactiveFileProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class HybridProcessingService {

    private final BatchJobService batchJobService;
    private final ReactiveFileProcessor reactiveFileProcessor;
    private final JobLauncher asyncJobLauncher;
    private final Job bulkConversionJob;

    @Value("${conversion.hybrid.large-file-threshold:52428800}")
    private long largeFileThreshold;

    @Value("${conversion.hybrid.reactive-enabled:true}")
    private boolean reactiveEnabled;

    @Value("${conversion.hybrid.high-frequency-threshold:100}")
    private int highFrequencyThreshold;

    public Mono<Map<String, Object>> processFileIntelligently(MultipartFile file) {
        log.info("Processing file intelligently: {} (size: {} bytes)", 
                file.getOriginalFilename(), file.getSize());

        ProcessingStrategy strategy = determineProcessingStrategy(file);
        
        return switch (strategy) {
            case REACTIVE_LARGE_FILE -> processWithReactiveStreams(file)
                .doOnSubscribe(s -> log.info("Using REACTIVE processing for large file: {}", file.getOriginalFilename()));
                
            case REACTIVE_HIGH_FREQUENCY -> processWithBackpressure(file)
                .doOnSubscribe(s -> log.info("Using REACTIVE with backpressure for high-frequency file: {}", file.getOriginalFilename()));
                
            case TRADITIONAL_BATCH -> processWithTraditionalBatch(file)
                .doOnSubscribe(s -> log.info("Using TRADITIONAL batch processing for file: {}", file.getOriginalFilename()));
                
            case HYBRID_ASYNC -> processWithHybridAsync(file)
                .doOnSubscribe(s -> log.info("Using HYBRID async processing for file: {}", file.getOriginalFilename()));
        };
    }

    private ProcessingStrategy determineProcessingStrategy(MultipartFile file) {
        if (!reactiveEnabled) {
            return ProcessingStrategy.TRADITIONAL_BATCH;
        }

        boolean isLargeFile = file.getSize() > largeFileThreshold;
        boolean isHighFrequency = estimateRecordCount(file) > highFrequencyThreshold;
        
        if (isLargeFile) {
            return ProcessingStrategy.REACTIVE_LARGE_FILE;
        } else if (isHighFrequency) {
            return ProcessingStrategy.REACTIVE_HIGH_FREQUENCY;
        } else if (file.getSize() > (largeFileThreshold / 4)) {
            return ProcessingStrategy.HYBRID_ASYNC;
        } else {
            return ProcessingStrategy.TRADITIONAL_BATCH;
        }
    }

    private Mono<Map<String, Object>> processWithReactiveStreams(MultipartFile file) {
        return reactiveFileProcessor.processLargeFileWithBackpressure(file)
                .map(result -> {
                    Map<String, Object> response = new java.util.HashMap<>();
                    response.put("strategy", "REACTIVE_LARGE_FILE");
                    response.put("taskId", result.getTaskId());
                    response.put("status", "PROCESSING");
                    response.put("fileName", file.getOriginalFilename());
                    response.put("estimatedRecords", result.getEstimatedRecords());
                    response.put("processingMode", "STREAMING");
                    return response;
                })
                .onErrorResume(error -> {
                    log.error("Reactive processing failed, falling back to batch", error);
                    return processWithTraditionalBatch(file);
                });
    }

    private Mono<Map<String, Object>> processWithBackpressure(MultipartFile file) {
        return reactiveFileProcessor.processWithControlledBackpressure(file)
                .map(result -> {
                    Map<String, Object> response = new java.util.HashMap<>();
                    response.put("strategy", "REACTIVE_HIGH_FREQUENCY");
                    response.put("taskId", result.getTaskId());
                    response.put("status", "PROCESSING");
                    response.put("fileName", file.getOriginalFilename());
                    response.put("backpressureStrategy", result.getBackpressureStrategy());
                    response.put("batchSize", result.getBatchSize());
                    return response;
                })
                .onErrorResume(error -> {
                    log.error("Backpressure processing failed, falling back to hybrid", error);
                    return processWithHybridAsync(file);
                });
    }

    private Mono<Map<String, Object>> processWithTraditionalBatch(MultipartFile file) {
        return Mono.fromCallable(() -> batchJobService.processJob(file, asyncJobLauncher, bulkConversionJob))
                .subscribeOn(Schedulers.boundedElastic())
                .map(result -> {
                    result.put("strategy", "TRADITIONAL_BATCH");
                    result.put("processingMode", "BATCH");
                    return result;
                });
    }

    private Mono<Map<String, Object>> processWithHybridAsync(MultipartFile file) {
        return Mono.fromCallable(() -> batchJobService.processJobAsync(file, asyncJobLauncher, bulkConversionJob))
                .subscribeOn(Schedulers.boundedElastic())
                .map(result -> {
                    result.put("strategy", "HYBRID_ASYNC");
                    result.put("processingMode", "ASYNC_BATCH");
                    return result;
                });
    }

    private int estimateRecordCount(MultipartFile file) {
        return (int) (file.getSize() / 50);
    }

    public enum ProcessingStrategy {
        REACTIVE_LARGE_FILE,
        REACTIVE_HIGH_FREQUENCY,
        TRADITIONAL_BATCH,
        HYBRID_ASYNC
    }
} 