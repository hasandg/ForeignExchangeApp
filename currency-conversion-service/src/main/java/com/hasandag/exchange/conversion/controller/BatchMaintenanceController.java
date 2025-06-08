package com.hasandag.exchange.conversion.controller;

import com.hasandag.exchange.conversion.service.CleanupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Batch Maintenance Controller
 * Single Responsibility: Handle cleanup operations and administrative tasks
 * Part of fat controller refactoring to separate concerns.
 */
@RestController
@RequestMapping("/api/v1/batch/maintenance")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Batch Maintenance", description = "Cleanup and administrative operations")
public class BatchMaintenanceController {

    private final CleanupService cleanupService;

    @GetMapping("/content-store/stats")
    @Operation(summary = "Get content store statistics")
    public ResponseEntity<Map<String, Object>> getContentStoreStats() {
        Map<String, Object> stats = cleanupService.getContentStoreStats();
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/content-store/cleanup/{contentKey}")
    @Operation(summary = "Cleanup specific content by key")
    public ResponseEntity<Map<String, Object>> cleanupSpecificContent(@PathVariable String contentKey) {
        cleanupService.cleanupJobContent(contentKey);
        
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("message", "Content cleanup initiated for key: " + contentKey);
        response.put("contentKey", contentKey);
        response.put("timestamp", java.time.Instant.now());
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/content-store/cleanup-all")
    @Operation(summary = "Cleanup all content store")
    public ResponseEntity<Map<String, Object>> cleanupAllContent() {
        int cleanedCount = cleanupService.cleanupAllContent();
        
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("cleanedFileCount", cleanedCount);
        response.put("message", "All content store cleaned up successfully");
        response.put("timestamp", java.time.Instant.now());
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/tasks/active-count")
    @Operation(summary = "Get count of active async tasks")
    public ResponseEntity<Map<String, Object>> getActiveAsyncTaskCount() {
        int activeCount = cleanupService.getActiveAsyncTaskCount();
        
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("activeTaskCount", activeCount);
        response.put("timestamp", java.time.Instant.now());
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/tasks/cleanup-completed")
    @Operation(summary = "Cleanup completed async tasks")
    public ResponseEntity<Map<String, Object>> cleanupCompletedAsyncTasks() {
        int cleanedCount = cleanupService.cleanupCompletedAsyncTasks();
        
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("cleanedTaskCount", cleanedCount);
        response.put("message", "Completed async tasks cleaned up successfully");
        response.put("timestamp", java.time.Instant.now());
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/full-cleanup")
    @Operation(summary = "Perform comprehensive cleanup")
    public ResponseEntity<Map<String, Object>> performFullCleanup() {
        Map<String, Object> result = cleanupService.performFullCleanup();
        result.put("message", "Full cleanup operation completed successfully");
        
        return ResponseEntity.ok(result);
    }
} 