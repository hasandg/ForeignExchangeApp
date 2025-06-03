package com.hasandag.exchange.conversion.service;

import com.hasandag.exchange.common.exception.BatchJobException;
import com.hasandag.exchange.conversion.model.BatchJobRequest;
import com.hasandag.exchange.conversion.model.BatchJobResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobExecutionService {
    
    private final FileContentStoreService fileContentStoreService;
    
    public BatchJobResponse executeSyncJob(BatchJobRequest request) {
        try {
            String contentKey = prepareFileContent(request.getFile());
            JobParameters jobParameters = createJobParameters(request.getFile(), contentKey);
            
            log.info("Executing synchronous job for file: {}", request.getFile().getOriginalFilename());
            JobExecution jobExecution = request.getJobLauncher().run(request.getJob(), jobParameters);
            log.info("Synchronous job completed with ID: {}", jobExecution.getJobId());
            
            return BatchJobResponse.sync(
                jobExecution.getJobId(),
                jobExecution.getJobInstance().getInstanceId(),
                jobExecution.getStatus().toString(),
                request.getFile().getOriginalFilename(),
                request.getFile().getSize(),
                contentKey
            );
            
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

    @Async("taskExecutor")
    public CompletableFuture<BatchJobResponse> executeAsyncJob(BatchJobRequest request, String contentKey) {
        try {
            log.info("Starting async job execution in thread: {} for file: {}", 
                    Thread.currentThread().getName(), request.getFile().getOriginalFilename());

            JobParameters jobParameters = createJobParameters(request.getFile(), contentKey);
            JobExecution jobExecution = request.getJobLauncher().run(request.getJob(), jobParameters);

            BatchJobResponse result = BatchJobResponse.sync(
                jobExecution.getJobId(),
                jobExecution.getJobInstance().getInstanceId(),
                jobExecution.getStatus().toString(),
                request.getFile().getOriginalFilename(),
                request.getFile().getSize(),
                contentKey
            );
            
            log.info("Async job completed with ID: {}, Status: {}", 
                    jobExecution.getJobId(), jobExecution.getStatus());

            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            log.error("Error in async job execution", e);
            BatchJobResponse errorResult = BatchJobResponse.failed(null, e.getMessage());
            return CompletableFuture.completedFuture(errorResult);
        }
    }
    
    public String prepareFileContent(MultipartFile file) throws IOException {
        String fileContent = new String(file.getBytes(), "UTF-8");
        String contentKey = fileContentStoreService.generateContentKey(file.getOriginalFilename());
        fileContentStoreService.storeContent(contentKey, fileContent);
        return contentKey;
    }
    
    private JobParameters createJobParameters(MultipartFile file, String contentKey) {
        return new JobParametersBuilder()
                .addString("file.content.key", contentKey)
                .addString("original.filename", file.getOriginalFilename())
                .addLong("file.size", file.getSize())
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
    }
} 