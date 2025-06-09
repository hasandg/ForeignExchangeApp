package com.hasandag.exchange.conversion.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/v1/backpressure/analysis")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Backpressure Analysis", description = "Analyze event dropping behavior in different backpressure strategies")
public class BackpressureAnalysisController {

    @GetMapping(value = "/test-dropping/{strategy}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Test event dropping with different backpressure strategies")
    public Flux<Map<String, Object>> testEventDropping(@PathVariable String strategy) {
        
        AtomicLong generatedEvents = new AtomicLong(0);
        AtomicLong droppedEvents = new AtomicLong(0);
        
        Flux<Map<String, Object>> fastStream = Flux.interval(Duration.ofMillis(10))
                .take(1000)
                .map(i -> {
                    long eventNumber = generatedEvents.incrementAndGet();
                    Map<String, Object> event = new HashMap<>();
                    event.put("eventNumber", eventNumber);
                    event.put("timestamp", System.currentTimeMillis());
                    event.put("strategy", strategy);
                    event.put("message", "Event " + eventNumber);
                    return event;
                })
                .doOnNext(event -> log.debug("Generated: {}", event.get("eventNumber")));

        return switch (strategy.toUpperCase()) {
            case "LATEST" -> fastStream
                    .onBackpressureLatest()
                    .doOnNext(event -> log.info("Delivered: {}", event.get("eventNumber")));
                    
            case "DROP" -> fastStream
                    .onBackpressureDrop(dropped -> {
                        long dropCount = droppedEvents.incrementAndGet();
                        log.warn("DROPPED Event: {} (Total dropped: {})", 
                                dropped.get("eventNumber"), dropCount);
                    })
                    .doOnNext(event -> log.info("Delivered: {}", event.get("eventNumber")));
                    
            case "BUFFER" -> fastStream
                    .onBackpressureBuffer(50, dropped -> {
                        long dropCount = droppedEvents.incrementAndGet();
                        log.warn("BUFFER OVERFLOW - DROPPED Event: {} (Total dropped: {})", 
                                dropped.get("eventNumber"), dropCount);
                    })
                    .doOnNext(event -> log.info("Delivered: {}", event.get("eventNumber")));
                    
            case "ERROR" -> fastStream
                    .onBackpressureError()
                    .doOnNext(event -> log.info("Delivered: {}", event.get("eventNumber")))
                    .onErrorResume(error -> {
                        log.error("Backpressure error occurred: {}", error.getMessage());
                        Map<String, Object> errorEvent = new HashMap<>();
                        errorEvent.put("error", "Backpressure limit exceeded");
                        errorEvent.put("strategy", strategy);
                        errorEvent.put("message", "Stream terminated due to backpressure");
                        return Flux.just(errorEvent);
                    });
                    
            default -> fastStream.delayElements(Duration.ofMillis(100));
        };
    }

    @GetMapping("/event-loss-simulation")
    @Operation(summary = "Simulate event loss with slow consumer")
    public Flux<Map<String, Object>> simulateEventLoss() {
        AtomicLong generated = new AtomicLong(0);
        AtomicLong delivered = new AtomicLong(0);
        AtomicLong dropped = new AtomicLong(0);

        return Flux.interval(Duration.ofMillis(1))
                .take(100)
                .map(i -> {
                    long eventNum = generated.incrementAndGet();
                    Map<String, Object> event = new HashMap<>();
                    event.put("generated", eventNum);
                    event.put("timestamp", System.currentTimeMillis());
                    return event;
                })
                .onBackpressureDrop(droppedEvent -> {
                    long dropCount = dropped.incrementAndGet();
                    log.warn("🔥 DROPPED: Event {} (Total drops: {})", 
                            droppedEvent.get("generated"), dropCount);
                })
                .doOnNext(event -> {
                    long deliveredCount = delivered.incrementAndGet();
                    log.info("✅ DELIVERED: Event {} (Delivered: {}, Generated: {}, Dropped: {})", 
                            event.get("generated"), deliveredCount, generated.get(), dropped.get());
                })
                .map(event -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("delivered", event.get("generated"));
                    result.put("totalGenerated", generated.get());
                    result.put("totalDelivered", delivered.get());
                    result.put("totalDropped", dropped.get());
                    result.put("dropRate", String.format("%.2f%%", (double) dropped.get() / generated.get() * 100));
                    return result;
                })
                .delayElements(Duration.ofMillis(50));
    }

    @GetMapping("/comparison/no-backpressure")
    @Operation(summary = "Show what happens without backpressure handling")
    public Flux<Map<String, Object>> noBackpressureExample() {
        return Flux.interval(Duration.ofMillis(1))
                .take(1000)
                .map(i -> {
                    Map<String, Object> event = new HashMap<>();
                    event.put("eventNumber", i);
                    event.put("warning", "This will likely cause OutOfMemoryError or resource exhaustion");
                    event.put("timestamp", System.currentTimeMillis());
                    return event;
                })
                .delayElements(Duration.ofMillis(100));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get backpressure statistics explanation")
    public Map<String, Object> getBackpressureStats() {
        return Map.of(
            "strategies", Map.of(
                "onBackpressureLatest", Map.of(
                    "dropsEvents", true,
                    "behavior", "Keeps only the latest event, drops all intermediate ones",
                    "useCases", new String[]{"Real-time dashboards", "Live progress updates", "Status monitoring"}
                ),
                "onBackpressureDrop", Map.of(
                    "dropsEvents", true,
                    "behavior", "Drops events when consumer is slow, can provide callback",
                    "useCases", new String[]{"Non-critical events", "Metrics collection", "Logging"}
                ),
                "onBackpressureBuffer", Map.of(
                    "dropsEvents", "Can drop when buffer full",
                    "behavior", "Buffers events up to limit, then drops or errors",
                    "useCases", new String[]{"Bursty workloads", "Temporary slow consumers", "Controlled memory usage"}
                ),
                "onBackpressureError", Map.of(
                    "dropsEvents", false,
                    "behavior", "Throws error when backpressure occurs",
                    "useCases", new String[]{"Critical data", "Financial transactions", "Must-process events"}
                )
            ),
            "currentImplementation", Map.of(
                "/events", "onBackpressureLatest() - DROPS EVENTS",
                "/conversions", "onBackpressureBuffer(1000) - May drop when buffer full",
                "/live-progress", "onBackpressureLatest() - DROPS EVENTS",
                "/controlled", "Configurable - Can drop based on strategy",
                "/chunked", "onBackpressureBuffer(100) - May drop when buffer full"
            ),
            "recommendation", Map.of(
                "forCriticalData", "Use onBackpressureError() or larger buffers",
                "forProgressUpdates", "onBackpressureLatest() is appropriate",
                "forMetrics", "onBackpressureDrop() with logging",
                "forBulkProcessing", "onBackpressureBuffer() with adequate size"
            )
        );
    }
} 