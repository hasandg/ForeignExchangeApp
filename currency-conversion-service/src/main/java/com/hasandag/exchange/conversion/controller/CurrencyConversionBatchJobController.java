package com.hasandag.exchange.conversion.controller;

import com.hasandag.exchange.conversion.service.BatchJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/batch")
@RequiredArgsConstructor
@Slf4j
public class CurrencyConversionBatchJobController {

    private final JobLauncher jobLauncher;
    @Qualifier("asyncJobLauncher")
    private final JobLauncher asyncJobLauncher;
    private final Job bulkConversionJob;
    private final BatchJobService batchJobService;

    @PostMapping(value = "/conversions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Start bulk conversion job (synchronous)")
    public ResponseEntity<Map<String, Object>> startBulkConversionJob(
            @Parameter(description = "CSV file", content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
            @RequestParam("file") MultipartFile file) {
        
        log.info("🚀 Starting synchronous batch job");
        long startTime = System.currentTimeMillis();
        
        // Uses unified BatchJobService with proper exception handling
        Map<String, Object> response = batchJobService.processJob(file, jobLauncher, bulkConversionJob);
        
        long responseTime = System.currentTimeMillis() - startTime;
        log.info("⏱️ Synchronous batch job submission took: {}ms", responseTime);
        
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/conversions/async", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Start bulk conversion job (asynchronous)")
    public ResponseEntity<Map<String, Object>> startAsyncBulkConversionJob(
            @Parameter(description = "CSV file", content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
            @RequestParam("file") MultipartFile file) {
        
        log.info("🚀 Starting asynchronous batch job");
        long startTime = System.currentTimeMillis();
        
        // Uses unified BatchJobService for async processing
        Map<String, Object> response = batchJobService.processJobAsync(file, asyncJobLauncher, bulkConversionJob);
        
        long responseTime = System.currentTimeMillis() - startTime;
        log.info("⏱️ Asynchronous batch job submission took: {}ms", responseTime);
        
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/conversions/async/{taskId}/status")
    @Operation(summary = "Get status of asynchronous job by task ID")
    public ResponseEntity<Map<String, Object>> getAsyncJobStatus(@PathVariable String taskId) {
        Map<String, Object> response = batchJobService.getAsyncJobStatus(taskId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/conversions/{jobId}/status")
    @Operation(summary = "Get status of job by job execution ID")
    public ResponseEntity<Map<String, Object>> getJobStatus(@PathVariable Long jobId) {
        Map<String, Object> response = batchJobService.getJobStatus(jobId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/conversions/jobs")
    @Operation(summary = "Get all jobs")
    public ResponseEntity<Map<String, Object>> getAllJobs() {
        Map<String, Object> response = batchJobService.getAllJobs();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/conversions/jobs/{jobName}")
    @Operation(summary = "Get jobs by name with pagination")
    public ResponseEntity<Map<String, Object>> getJobsByName(
            @PathVariable String jobName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        if (size > 100) {
            return ResponseEntity.badRequest().body(
                Map.of("error", "Page size cannot exceed 100")
            );
        }
        
        Map<String, Object> response = batchJobService.getJobsByName(jobName, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/conversions/jobs/running")
    @Operation(summary = "Get currently running jobs")
    public ResponseEntity<Map<String, Object>> getRunningJobs() {
        Map<String, Object> response = batchJobService.getRunningJobs();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/conversions/statistics")
    @Operation(summary = "Get batch job statistics")
    public ResponseEntity<Map<String, Object>> getJobStatistics() {
        Map<String, Object> response = batchJobService.getJobStatistics();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/conversions/health")
    @Operation(summary = "Get batch service health status")
    public ResponseEntity<Map<String, Object>> getBatchHealthStatus() {
        Map<String, Object> response = new java.util.HashMap<>();
        
        try {
            Map<String, Object> runningJobsResponse = batchJobService.getRunningJobs();
            int runningCount = (Integer) runningJobsResponse.get("count");
            
            Map<String, Object> statsResponse = batchJobService.getJobStatistics();
            
            response.put("status", "UP");
            response.put("runningJobs", runningCount);
            response.put("timestamp", java.time.Instant.now());
            response.put("totalJobTypes", statsResponse.get("totalJobTypes"));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error checking batch health status", e);
            response.put("status", "DOWN");
            response.put("error", e.getMessage());
            response.put("timestamp", java.time.Instant.now());
            return ResponseEntity.status(503).body(response);
        }
    }

    @GetMapping("/conversions/content-store/stats")
    @Operation(summary = "Get content store statistics")
    public ResponseEntity<Map<String, Object>> getContentStoreStats() {
        try {
            Map<String, Object> stats = batchJobService.getContentStoreStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error retrieving content store stats", e);
            Map<String, Object> errorResponse = new java.util.HashMap<>();
            errorResponse.put("error", "Error retrieving content store stats: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    @PostMapping("/conversions/content-store/cleanup/{contentKey}")
    @Operation(summary = "Cleanup specific content by key")
    public ResponseEntity<Map<String, Object>> cleanupSpecificContent(@PathVariable String contentKey) {
        try {
            batchJobService.cleanupJobContent(contentKey);
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("message", "Content cleanup initiated for key: " + contentKey);
            response.put("timestamp", java.time.Instant.now());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error cleaning up content for key: {}", contentKey, e);
            Map<String, Object> errorResponse = new java.util.HashMap<>();
            errorResponse.put("error", "Error cleaning up content: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
} 