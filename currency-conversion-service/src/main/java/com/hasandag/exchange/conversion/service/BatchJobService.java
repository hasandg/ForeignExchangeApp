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
    private final JobStatusServiceAdapter jobStatusServiceAdapter;
    private final FileContentStoreService fileContentStoreService;

    public Map<String, Object> processJob(MultipartFile file, JobLauncher jobLauncher, Job bulkConversionJob) {
        fileValidationService.validateFile(file);
        
        BatchJobRequest request = BatchJobRequest.builder()
                .file(file)
                .jobLauncher(jobLauncher)
                .job(bulkConversionJob)
                .operationType("sync")
                .build();
                
        BatchJobResponse response = jobExecutionService.executeSyncJob(request);
        return response.toMap();
    }

    public Map<String, Object> processJobAsync(MultipartFile file, JobLauncher asyncJobLauncher, Job bulkConversionJob) {
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

    public Map<String, Object> getAsyncJobStatus(String taskId) {
        BatchJobResponse response = asyncTaskManager.getTaskStatus(taskId);
        return response.toMap();
    }

    public Map<String, Object> getJobStatus(Long jobId) {
        return jobStatusServiceAdapter.getJobStatus(jobId);
    }

    public Map<String, Object> getAllJobs() {
        return jobStatusServiceAdapter.getAllJobs();
    }

    public Map<String, Object> getRunningJobs() {
        return jobStatusServiceAdapter.getRunningJobs();
    }

    public Map<String, Object> getJobStatistics() {
        return jobStatusServiceAdapter.getJobStatistics();
    }

    public Map<String, Object> getJobsByName(String jobName, int page, int size) {
        return jobStatusServiceAdapter.getJobsByName(jobName, page, size);
    }

    public void cleanupJobContent(String contentKey) {
        fileContentStoreService.removeContent(contentKey);
    }

    public Map<String, Object> getContentStoreStats() {
        return fileContentStoreService.getStoreStats();
    }

    public int cleanupAllContent() {
        return fileContentStoreService.clearAllContent();
    }
    
    public int getActiveAsyncTaskCount() {
        return asyncTaskManager.getActiveTaskCount();
    }
    
    public int cleanupCompletedAsyncTasks() {
        return asyncTaskManager.cleanupCompletedTasks();
    }
} 