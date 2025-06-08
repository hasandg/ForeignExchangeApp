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

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchJobService {

    private final FileValidationService fileValidationService;
    private final JobExecutionService jobExecutionService;
    private final AsyncTaskManager asyncTaskManager;

    public Map<String, Object> processJob(MultipartFile file, JobLauncher jobLauncher, Job job) {
        log.info("Starting synchronous batch job processing for file: {}", file.getOriginalFilename());
        
        fileValidationService.validateFile(file);
        
        BatchJobRequest request = BatchJobRequest.builder()
                .file(file)
                .jobLauncher(jobLauncher)
                .job(job)
                .operationType("sync")
                .build();
                
        BatchJobResponse response = jobExecutionService.executeSyncJob(request);
        
        log.info("Synchronous batch job completed for file: {} with job ID: {}", 
                file.getOriginalFilename(), response.getJobId());
        return response.toMap();
    }

    public Map<String, Object> processJobAsync(MultipartFile file, JobLauncher jobLauncher, Job job) {
        log.info("Starting asynchronous batch job processing for file: {}", file.getOriginalFilename());
        
        fileValidationService.validateFile(file);
        
        String taskId = asyncTaskManager.generateTaskId();
        log.info("Generated async task ID: {} for file: {}", taskId, file.getOriginalFilename());

        try {
            String contentKey = jobExecutionService.prepareFileContent(file);
            
            BatchJobRequest request = BatchJobRequest.builder()
                    .file(file)
                    .jobLauncher(jobLauncher)
                    .job(job)
                    .operationType("async")
                    .build();
                    
            CompletableFuture<BatchJobResponse> asyncTask = jobExecutionService.executeAsyncJob(request, contentKey);
            asyncTaskManager.registerTask(taskId, asyncTask);
            
            Map<String, Object> response = Map.of(
                "taskId", taskId,
                "status", "SUBMITTED",
                "fileName", file.getOriginalFilename(),
                "submittedAt", java.time.Instant.now().toString()
            );

            log.info("Async task {} submitted for file: {}", taskId, file.getOriginalFilename());
            return response;
            
        } catch (Exception e) {
            log.error("Error processing async job for file: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("Error processing uploaded file: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> getAsyncJobStatus(String taskId) {
        BatchJobResponse response = asyncTaskManager.getTaskStatus(taskId);
        return response.toMap();
    }
}