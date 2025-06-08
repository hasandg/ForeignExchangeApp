package com.hasandag.exchange.conversion.service;

import com.hasandag.exchange.conversion.model.BatchJobRequest;
import com.hasandag.exchange.conversion.model.BatchJobResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Batch Job Service following Single Responsibility Principle.
 * Single responsibility: Core batch job processing and coordination.
 * No delegation - specialized services are used directly by controllers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchJobService {

    private final FileValidationService fileValidationService;
    private final JobExecutionService jobExecutionService;
    private final AsyncTaskManager asyncTaskManager;

    /**
     * Process synchronous batch job
     * Core responsibility: Coordinate sync job execution flow
     */
    public Map<String, Object> processJob(MultipartFile file, JobLauncher jobLauncher, Job bulkConversionJob) {
        log.info("Starting synchronous batch job processing for file: {}", file.getOriginalFilename());
        
        fileValidationService.validateFile(file);
        
        BatchJobRequest request = BatchJobRequest.builder()
                .file(file)
                .jobLauncher(jobLauncher)
                .job(bulkConversionJob)
                .operationType("sync")
                .build();
                
        BatchJobResponse response = jobExecutionService.executeSyncJob(request);
        
        log.info("Synchronous batch job completed for file: {} with job ID: {}", 
                file.getOriginalFilename(), response.getJobId());
        
        return response.toMap();
    }

    /**
     * Process asynchronous batch job
     * Core responsibility: Coordinate async job execution flow
     */
    public Map<String, Object> processJobAsync(MultipartFile file, JobLauncher asyncJobLauncher, Job bulkConversionJob) {
        log.info("Starting asynchronous batch job processing for file: {}", file.getOriginalFilename());
        
        fileValidationService.validateFile(file);
        
        String taskId = asyncTaskManager.generateTaskId();
        log.info("Generated async task ID: {} for file: {}", taskId, file.getOriginalFilename());
        
        try {
            String contentKey = jobExecutionService.prepareFileContent(file);
            
            BatchJobRequest request = BatchJobRequest.builder()
                    .file(file)
                    .jobLauncher(asyncJobLauncher)
                    .job(bulkConversionJob)
                    .operationType("async")
                    .build();
            
            CompletableFuture<BatchJobResponse> asyncTask = jobExecutionService.executeAsyncJob(request, contentKey);
            asyncTaskManager.registerTask(taskId, asyncTask);
            
            log.info("Async task {} submitted for file: {}", taskId, file.getOriginalFilename());

            BatchJobResponse response = BatchJobResponse.async(
                taskId, 
                "SUBMITTED", 
                "Job submitted asynchronously",
                file.getOriginalFilename(),
                file.getSize(),
                contentKey
            );
            
            return response.toMap();
            
        } catch (Exception e) {
            log.error("File processing error for async job", e);
            throw new com.hasandag.exchange.common.exception.BatchJobException(
                "FILE_PROCESSING_ERROR",
                "Error processing uploaded file: " + e.getMessage(),
                org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                e
            );
        }
    }

    /**
     * Get async job status by task ID
     * Direct async task management - no delegation
     */
    public Map<String, Object> getAsyncJobStatus(String taskId) {
        BatchJobResponse response = asyncTaskManager.getTaskStatus(taskId);
        return response.toMap();
    }
} 