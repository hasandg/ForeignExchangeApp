package com.hasandag.exchange.conversion.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Component
public class JobCompletionNotificationListener implements JobExecutionListener {

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("BATCH JOB: {} STARTING with Job ID: {}. Job Parameters: {}", 
            jobExecution.getJobInstance().getJobName(), 
            jobExecution.getId(),
            jobExecution.getJobParameters());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        LocalDateTime startTime = jobExecution.getStartTime();
        LocalDateTime endTime = jobExecution.getEndTime();
        long durationMillis = 0;
        if (startTime != null && endTime != null) {
            durationMillis = Duration.between(startTime, endTime).toMillis();
        }

        log.info("BATCH JOB: {} FINISHED with Job ID: {} and Status: {}. Duration: {}ms",
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getId(),
                jobExecution.getStatus(),
                durationMillis);

        if (jobExecution.getStatus() == org.springframework.batch.core.BatchStatus.COMPLETED) {
            log.info("Job {} completed successfully.", jobExecution.getJobInstance().getJobName());
        } else if (jobExecution.getStatus() == org.springframework.batch.core.BatchStatus.FAILED) {
            log.error("Job {} failed. See exceptions below:", jobExecution.getJobInstance().getJobName());
            jobExecution.getAllFailureExceptions().forEach(ex -> 
                log.error("Failure Exception for job {}: {}", jobExecution.getJobInstance().getJobName(), ex.getMessage(), ex)
            );
        }
        
        jobExecution.getStepExecutions().forEach(stepExecution -> {
            log.info("Step {} summary: Read={}, Write={}, Commit={}, Filter={}, SkipRead={}, SkipProcess={}, SkipWrite={}",
                    stepExecution.getStepName(),
                    stepExecution.getReadCount(),
                    stepExecution.getWriteCount(),
                    stepExecution.getCommitCount(),
                    stepExecution.getFilterCount(),
                    stepExecution.getReadSkipCount(),
                    stepExecution.getProcessSkipCount(),
                    stepExecution.getWriteSkipCount());
        });
    }
} 