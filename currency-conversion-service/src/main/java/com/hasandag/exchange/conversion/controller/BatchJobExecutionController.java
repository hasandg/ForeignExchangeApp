package com.hasandag.exchange.conversion.controller;

import com.hasandag.exchange.conversion.service.BatchJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
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
@RequestMapping("/api/v1/batch/jobs")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Batch Job Execution", description = "Job execution operations")
public class BatchJobExecutionController {

    private final BatchJobService batchJobService;
    private final JobLauncher syncJobLauncher;
    private final JobLauncher asyncJobLauncher;
    private final Job bulkConversionJob;

    @PostMapping(value = "/sync", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Start bulk conversion job (synchronous)")
    public ResponseEntity<Map<String, Object>> startSynchronousJob(
            @Parameter(description = "CSV file", content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
            @RequestParam("file") MultipartFile file) {
        
        Map<String, Object> response = batchJobService.processJob(file, syncJobLauncher, bulkConversionJob);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/async", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Start bulk conversion job (asynchronous)")
    public ResponseEntity<Map<String, Object>> startAsynchronousJob(
            @Parameter(description = "CSV file", content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
            @RequestParam("file") MultipartFile file) {
        
        Map<String, Object> response = batchJobService.processJobAsync(file, asyncJobLauncher, bulkConversionJob);
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/async/{taskId}/status")
    @Operation(summary = "Get status of asynchronous job by task ID")
    public ResponseEntity<Map<String, Object>> getAsyncJobStatus(@PathVariable String taskId) {
        Map<String, Object> response = batchJobService.getAsyncJobStatus(taskId);
        return ResponseEntity.ok(response);
    }
} 