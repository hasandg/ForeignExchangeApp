package com.hasandag.exchange.conversion.controller;

import com.hasandag.exchange.conversion.dto.JobListResponse;
import com.hasandag.exchange.conversion.dto.JobStatisticsResponse;
import com.hasandag.exchange.conversion.dto.JobStatusResponse;
import com.hasandag.exchange.conversion.service.JobStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Batch Job Status Controller
 * Single Responsibility: Handle job status queries, monitoring, and statistics
 * Part of fat controller refactoring to separate concerns.
 */
@RestController
@RequestMapping("/api/v1/batch/status")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Batch Job Status", description = "Job monitoring and statistics")
public class BatchJobStatusController {

    private final JobStatusService jobStatusService;

    @GetMapping("/jobs/{jobId}")
    @Operation(summary = "Get status of job by job execution ID")
    public ResponseEntity<JobStatusResponse> getJobStatus(@PathVariable Long jobId) {
        JobStatusResponse response = jobStatusService.getJobStatus(jobId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/jobs")
    @Operation(summary = "Get all jobs with pagination")
    public ResponseEntity<JobListResponse> getAllJobs() {
        JobListResponse response = jobStatusService.getAllJobs();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/jobs/search/{jobName}")
    @Operation(summary = "Get jobs by name with pagination")
    public ResponseEntity<JobListResponse> getJobsByName(
            @PathVariable String jobName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        if (size > 100) {
            return ResponseEntity.badRequest().body(
                JobListResponse.builder()
                    .error("Page size cannot exceed 100")
                    .build()
            );
        }
        
        JobListResponse response = jobStatusService.getJobsByName(jobName, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/jobs/running")
    @Operation(summary = "Get all currently running jobs")
    public ResponseEntity<JobListResponse> getRunningJobs() {
        JobListResponse response = jobStatusService.getRunningJobs();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/statistics")
    @Operation(summary = "Get batch job execution statistics")
    public ResponseEntity<JobStatisticsResponse> getJobStatistics() {
        JobStatisticsResponse response = jobStatusService.getJobStatistics();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    @Operation(summary = "Get system health status")
    public ResponseEntity<Map<String, Object>> getHealthStatus() {
        JobListResponse runningJobsResponse = jobStatusService.getRunningJobs();
        int runningCount = runningJobsResponse.getTotalJobs() != null ? runningJobsResponse.getTotalJobs() : 0;
        
        JobStatisticsResponse statsResponse = jobStatusService.getJobStatistics();
        
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("status", "UP");
        response.put("runningJobs", runningCount);
        response.put("totalJobTypes", statsResponse.getTotalJobTypes());
        response.put("timestamp", java.time.Instant.now());
        
        return ResponseEntity.ok(response);
    }
} 