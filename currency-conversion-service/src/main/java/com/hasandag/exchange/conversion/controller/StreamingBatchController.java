package com.hasandag.exchange.conversion.controller;

import com.hasandag.exchange.common.dto.ConversionResponse;
import com.hasandag.exchange.conversion.model.StreamingProcessingEvent;
import com.hasandag.exchange.conversion.service.reactive.StreamingFileProcessor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/batch/streaming")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Streaming Batch Processing", description = "Real-time streaming processing with Flux and backpressure")
public class StreamingBatchController {

    private final StreamingFileProcessor streamingFileProcessor;

    @PostMapping(value = "/events", 
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
                 produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Process file with real-time streaming events (SSE)")
    public Flux<StreamingProcessingEvent> processFileWithStreamingEvents(
            @Parameter(description = "CSV file", content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
            @RequestParam("file") MultipartFile file) {
        
        log.info("Starting streaming processing for file: {} (size: {} bytes)", 
                file.getOriginalFilename(), file.getSize());
        
        return streamingFileProcessor.processFileWithStreaming(file)
                .onBackpressureLatest()
                .doOnSubscribe(subscription -> 
                    log.info("Client subscribed to streaming events for: {}", file.getOriginalFilename()))
                .doOnCancel(() -> 
                    log.info("Client cancelled streaming events for: {}", file.getOriginalFilename()))
                .doOnComplete(() -> 
                    log.info("Streaming processing completed for: {}", file.getOriginalFilename()));
    }

    @PostMapping(value = "/conversions", 
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
                 produces = MediaType.APPLICATION_NDJSON_VALUE)
    @Operation(summary = "Stream conversion results in real-time (NDJSON)")
    public Flux<ConversionResponse> streamConversionResults(
            @Parameter(description = "CSV file", content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
            @RequestParam("file") MultipartFile file) {
        
        log.info("Starting conversion streaming for file: {} (size: {} bytes)", 
                file.getOriginalFilename(), file.getSize());
        
        return streamingFileProcessor.processFileAsConversionStream(file)
                .onBackpressureBuffer(1000)
                .doOnNext(conversion -> 
                    log.debug("Streaming conversion: {} {} -> {} {}", 
                            conversion.getSourceAmount(), conversion.getSourceCurrency(),
                            conversion.getTargetAmount(), conversion.getTargetCurrency()))
                .onErrorContinue((error, item) -> 
                    log.error("Error streaming conversion: {}", item, error));
    }

    @PostMapping(value = "/live-progress", 
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
                 produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Process file with live progress updates")
    public Flux<StreamingProcessingEvent> processWithLiveProgress(
            @Parameter(description = "CSV file", content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "2") int updateIntervalSeconds) {
        
        log.info("Starting live progress processing for file: {} with {}s intervals", 
                file.getOriginalFilename(), updateIntervalSeconds);
        
        return streamingFileProcessor.processWithLiveUpdates(file, Duration.ofSeconds(updateIntervalSeconds))
                .onBackpressureLatest()
                .filter(event -> 
                    event.isProgressEvent() || event.isCompletionEvent()
                )
                .doOnNext(event -> 
                    log.debug("Live progress update: {} - {}", event.getEventType(), event.getMessage()));
    }

    @PostMapping(value = "/controlled", 
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
                 produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Process file with controlled backpressure")
    public Flux<StreamingProcessingEvent> processWithControlledBackpressure(
            @Parameter(description = "CSV file", content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "BUFFER") String backpressureStrategy) {
        
        log.info("Starting controlled processing for file: {} with {} backpressure", 
                file.getOriginalFilename(), backpressureStrategy);
        
        Flux<StreamingProcessingEvent> stream = streamingFileProcessor.processFileWithStreaming(file);
        
        return switch (backpressureStrategy.toUpperCase()) {
            case "LATEST" -> stream.onBackpressureLatest();
            case "DROP" -> stream.onBackpressureDrop(dropped -> 
                log.warn("Dropped event due to backpressure: {}", dropped.getEventType()));
            case "ERROR" -> stream.onBackpressureError();
            default -> stream.onBackpressureBuffer(1000);
        };
    }

    @PostMapping(value = "/chunked", 
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
                 produces = MediaType.APPLICATION_NDJSON_VALUE)
    @Operation(summary = "Stream processing results in chunks")
    public Flux<Map<String, Object>> processInChunks(
            @Parameter(description = "CSV file", content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "50") int chunkSize) {
        
        log.info("Starting chunked processing for file: {} with chunk size: {}", 
                file.getOriginalFilename(), chunkSize);
        
        return streamingFileProcessor.processFileAsConversionStream(file)
                .buffer(chunkSize)
                .map(conversions -> Map.of(
                    "chunkSize", conversions.size(),
                    "conversions", conversions,
                    "timestamp", java.time.LocalDateTime.now()
                ))
                .onBackpressureBuffer(100)
                .doOnNext(chunk -> 
                    log.debug("Streaming chunk with {} conversions", chunk.get("chunkSize")));
    }

    @GetMapping("/comparison/traditional") 
    @Operation(summary = "Traditional approach - single response")
    public Mono<ResponseEntity<Map<String, Object>>> traditionalApproach() {
        return Mono.just(ResponseEntity.ok(Map.of(
            "approach", "traditional",
            "description", "Single response after all processing is complete",
            "disadvantages", new String[]{
                "No real-time feedback",
                "Client waits for entire operation",
                "No progress indication",
                "Memory intensive for large files"
            }
        )));
    }

    @GetMapping("/comparison/streaming")
    @Operation(summary = "Streaming approach benefits")
    public ResponseEntity<Map<String, Object>> streamingBenefits() {
        return ResponseEntity.ok(Map.of(
            "approach", "streaming",
            "description", "Real-time streaming of processing events and results",
            "advantages", new String[]{
                "Real-time feedback",
                "Progressive processing",
                "Memory efficient",
                "Backpressure handling",
                "Cancellation support",
                "Live progress updates"
            },
            "backpressureStrategies", new String[]{
                "BUFFER - Buffer events up to limit",
                "LATEST - Keep only latest event",
                "DROP - Drop events if consumer slow",
                "ERROR - Error if consumer can't keep up"
            }
        ));
    }
} 