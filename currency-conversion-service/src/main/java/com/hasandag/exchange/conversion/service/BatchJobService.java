package com.hasandag.exchange.conversion.service;

import com.hasandag.exchange.conversion.exception.BatchJobException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchJobService {

    private final JobStatusService jobStatusService;
    private final OptimizedFileContentStoreService fileContentStoreService;
    
    private final AtomicLong taskIdGenerator = new AtomicLong(1);
    private final Map<String, CompletableFuture<Map<String, Object>>> asyncTasks = new ConcurrentHashMap<>();

    public Map<String, Object> processJob(MultipartFile file, JobLauncher jobLauncher, Job bulkConversionJob) {
        try {
            validateFile(file);
            
            String fileContent = new String(file.getBytes(), "UTF-8");
            String contentKey = fileContentStoreService.generateContentKey(file.getOriginalFilename());
            fileContentStoreService.storeContent(contentKey, fileContent);
            
            JobParameters jobParameters = createJobParameters(file, contentKey);
            
            log.info("🚀 Submitting synchronous job for file: {}", file.getOriginalFilename());
            JobExecution jobExecution = jobLauncher.run(bulkConversionJob, jobParameters);
            log.info("⚡ Synchronous job submitted with ID: {}", jobExecution.getJobId());
            
            return buildJobResponse(jobExecution, file, contentKey);
            
        } catch (JobExecutionAlreadyRunningException e) {
            throw BatchJobException.jobAlreadyRunning();
        } catch (JobRestartException | JobInstanceAlreadyCompleteException e) {
            throw new BatchJobException(
                "JOB_RESTART_ERROR",
                "Job cannot be restarted. This file may have already been processed.",
                org.springframework.http.HttpStatus.CONFLICT,
                e
            );
        } catch (IOException e) {
            log.error("File processing error", e);
            throw new BatchJobException(
                "FILE_PROCESSING_ERROR",
                "Error processing uploaded file: " + e.getMessage(),
                org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                e
            );
        } catch (Exception e) {
            log.error("Unexpected error in batch job", e);
            throw BatchJobException.executionFailed(e.getMessage(), e);
        }
    }

    public Map<String, Object> processJobAsync(MultipartFile file, JobLauncher asyncJobLauncher, Job bulkConversionJob) {
        try {
            validateFile(file);
            
            String taskId = String.valueOf(taskIdGenerator.getAndIncrement());
            log.info("🎯 Generated async task ID: {} for file: {}", taskId, file.getOriginalFilename());

            String fileContent = new String(file.getBytes(), "UTF-8");
            String contentKey = fileContentStoreService.generateContentKey(file.getOriginalFilename());
            fileContentStoreService.storeContent(contentKey, fileContent);

            CompletableFuture<Map<String, Object>> asyncTask = runJobAsync(file, contentKey, asyncJobLauncher, bulkConversionJob);
            asyncTasks.put(taskId, asyncTask);

            log.info("⚡ Async task {} submitted for file: {}", taskId, file.getOriginalFilename());

            Map<String, Object> response = new HashMap<>();
            response.put("taskId", taskId);
            response.put("status", "SUBMITTED");
            response.put("message", "Job submitted asynchronously");
            response.put("filename", file.getOriginalFilename());
            response.put("fileSize", file.getSize());
            response.put("contentKey", contentKey);

            return response;

        } catch (IOException e) {
            log.error("File processing error", e);
            throw new BatchJobException(
                "FILE_PROCESSING_ERROR",
                "Error processing uploaded file: " + e.getMessage(),
                org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                e
            );
        } catch (Exception e) {
            log.error("Unexpected error in async batch job", e);
            throw BatchJobException.executionFailed(e.getMessage(), e);
        }
    }

    public Map<String, Object> getAsyncJobStatus(String taskId) {
        CompletableFuture<Map<String, Object>> task = asyncTasks.get(taskId);
        
        if (task == null) {
            throw new BatchJobException(
                "TASK_NOT_FOUND",
                "Async task with ID " + taskId + " not found",
                org.springframework.http.HttpStatus.NOT_FOUND
            );
        }

        Map<String, Object> response = new HashMap<>();
        response.put("taskId", taskId);

        if (task.isDone()) {
            try {
                Map<String, Object> result = task.get();
                response.putAll(result);
                asyncTasks.remove(taskId);
            } catch (Exception e) {
                response.put("status", "FAILED");
                response.put("error", e.getMessage());
                asyncTasks.remove(taskId);
            }
        } else {
            response.put("status", "RUNNING");
            response.put("message", "Job is still running");
        }

        return response;
    }

    public Map<String, Object> getJobStatus(Long jobId) {
        Map<String, Object> result = jobStatusService.getJobStatus(jobId);
        
        if (result.containsKey("error")) {
            if ("Job not found".equals(result.get("error"))) {
                throw BatchJobException.jobNotFound(jobId);
            }
            throw BatchJobException.executionFailed((String) result.get("error"), null);
        }
        
        return result;
    }

    public Map<String, Object> getAllJobs() {
        Map<String, Object> result = jobStatusService.getAllJobs();
        
        if (result.containsKey("error")) {
            throw BatchJobException.executionFailed((String) result.get("error"), null);
        }
        
        return result;
    }

    public Map<String, Object> getRunningJobs() {
        Map<String, Object> result = jobStatusService.getRunningJobs();
        
        if (result.containsKey("error")) {
            throw BatchJobException.executionFailed((String) result.get("error"), null);
        }
        
        return result;
    }

    public Map<String, Object> getJobStatistics() {
        Map<String, Object> result = jobStatusService.getJobStatistics();
        
        if (result.containsKey("error")) {
            throw BatchJobException.executionFailed((String) result.get("error"), null);
        }
        
        return result;
    }

    public Map<String, Object> getJobsByName(String jobName, int page, int size) {
        Map<String, Object> result = jobStatusService.getJobsByName(jobName, page, size);
        
        if (result.containsKey("error")) {
            throw BatchJobException.executionFailed((String) result.get("error"), null);
        }
        
        return result;
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

    // ========== Private Helper Methods ==========

    @Async("taskExecutor")
    private CompletableFuture<Map<String, Object>> runJobAsync(MultipartFile file, String contentKey, 
                                                              JobLauncher asyncJobLauncher, Job bulkConversionJob) {
        try {
            log.info("🔥 Starting async job execution in thread: {} for file: {}", 
                    Thread.currentThread().getName(), file.getOriginalFilename());

            JobParameters jobParameters = createJobParameters(file, contentKey);
            JobExecution jobExecution = asyncJobLauncher.run(bulkConversionJob, jobParameters);

            Map<String, Object> result = buildJobResponse(jobExecution, file, contentKey);
            
            log.info("✅ Async job completed with ID: {}, Status: {}", 
                    jobExecution.getJobId(), jobExecution.getStatus());

            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            log.error("Error in async job execution", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "FAILED");
            errorResult.put("error", e.getMessage());
            return CompletableFuture.completedFuture(errorResult);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw BatchJobException.emptyFile();
        }

        if (!isValidFile(file)) {
            throw BatchJobException.invalidFileType();
        }
    }

    private boolean isValidFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        return filename != null && filename.toLowerCase().endsWith(".csv");
    }

    private JobParameters createJobParameters(MultipartFile file, String contentKey) {
        return new JobParametersBuilder()
                .addString("file.content.key", contentKey)
                .addString("original.filename", file.getOriginalFilename())
                .addLong("file.size", file.getSize())
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
    }

    private Map<String, Object> buildJobResponse(JobExecution jobExecution, MultipartFile file, String contentKey) {
        Map<String, Object> response = new HashMap<>();
        response.put("jobId", jobExecution.getJobId());
        response.put("jobInstanceId", jobExecution.getJobInstance().getInstanceId());
        response.put("status", jobExecution.getStatus().toString());
        response.put("filename", file.getOriginalFilename());
        response.put("fileSize", file.getSize());
        response.put("contentKey", contentKey);
        return response;
    }
} 