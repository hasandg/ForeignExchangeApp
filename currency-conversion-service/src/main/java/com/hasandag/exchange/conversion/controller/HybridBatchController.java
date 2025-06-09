package com.hasandag.exchange.conversion.controller;

import com.hasandag.exchange.conversion.service.HybridProcessingService;
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
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/batch/hybrid")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Hybrid Batch Processing", description = "Intelligent processing that chooses between traditional batch and reactive streams")
public class HybridBatchController {

    private final HybridProcessingService hybridProcessingService;

    @PostMapping(value = "/intelligent", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Intelligently process file using hybrid approach")
    public Mono<ResponseEntity<Map<String, Object>>> processFileIntelligently(
            @Parameter(description = "CSV file", content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
            @RequestParam("file") MultipartFile file) {
        
        log.info("Received intelligent processing request for file: {} (size: {} bytes)", 
                file.getOriginalFilename(), file.getSize());
        
        return hybridProcessingService.processFileIntelligently(file)
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    log.error("Error in intelligent processing", error);
                    return Mono.just(ResponseEntity.badRequest()
                            .body(Map.of(
                                "error", "Processing failed: " + error.getMessage(),
                                "strategy", "ERROR_FALLBACK",
                                "fileName", file.getOriginalFilename()
                            )));
                });
    }

    @GetMapping("/strategies")
    @Operation(summary = "Get available processing strategies and their criteria")
    public ResponseEntity<Map<String, Object>> getProcessingStrategies() {
        return ResponseEntity.ok(Map.of(
            "strategies", Map.of(
                "REACTIVE_LARGE_FILE", Map.of(
                    "description", "Reactive streaming for files > 50MB",
                    "features", new String[]{"Backpressure", "Memory efficient", "Streaming processing"}
                ),
                "REACTIVE_HIGH_FREQUENCY", Map.of(
                    "description", "Reactive with controlled backpressure for high-frequency data",
                    "features", new String[]{"Adaptive batching", "Backpressure control", "Rate limiting"}
                ),
                "HYBRID_ASYNC", Map.of(
                    "description", "Traditional async batch for medium files",
                    "features", new String[]{"Spring Batch", "Async execution", "Reliable processing"}
                ),
                "TRADITIONAL_BATCH", Map.of(
                    "description", "Traditional synchronous batch for small files",
                    "features", new String[]{"Spring Batch", "Immediate response", "Simple processing"}
                )
            ),
            "criteria", Map.of(
                "largeFileThreshold", "50MB",
                "highFrequencyThreshold", "100 records",
                "hybridAsyncThreshold", "12.5MB"
            )
        ));
    }

    @GetMapping("/metrics")
    @Operation(summary = "Get hybrid processing metrics")
    public ResponseEntity<Map<String, Object>> getProcessingMetrics() {
        return ResponseEntity.ok(Map.of(
            "status", "metrics_placeholder",
            "note", "Metrics collection would be implemented here"
        ));
    }
} 