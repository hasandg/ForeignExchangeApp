package com.hasandag.exchange.conversion.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.*;
import org.springframework.batch.core.explore.JobExplorer;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobStatusService Tests")
class JobStatusServiceTest {

    @Mock
    private JobExplorer jobExplorer;

    @InjectMocks
    private JobStatusService jobStatusService;

    private JobExecution mockJobExecution;
    private JobInstance mockJobInstance;

    @BeforeEach
    void setUp() {
        mockJobInstance = new JobInstance(1L, "testJob");
        mockJobExecution = new JobExecution(mockJobInstance, 100L, new JobParameters());
        mockJobExecution.setStatus(BatchStatus.COMPLETED);
        mockJobExecution.setExitStatus(ExitStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should include elapsed time for completed job")
    void shouldIncludeElapsedTimeForCompletedJob() {
        
        LocalDateTime startTime = LocalDateTime.of(2024, 1, 1, 10, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2024, 1, 1, 10, 5, 30); 
        
        mockJobExecution.setStartTime(startTime);
        mockJobExecution.setEndTime(endTime);
        
        Long jobId = 100L;
        when(jobExplorer.getJobExecution(jobId)).thenReturn(mockJobExecution);

        
        Map<String, Object> result = jobStatusService.getJobStatus(jobId);

        
        assertThat(result).isNotNull();
        assertThat(result).containsKey("elapsedTimeSeconds");
        assertThat(result).containsKey("elapsedTimeMillis");
        assertThat(result).containsKey("elapsedTimeFormatted");
        assertThat(result).containsKey("isRunning");
        
        assertThat(result.get("elapsedTimeSeconds")).isEqualTo(330L); 
        assertThat(result.get("elapsedTimeMillis")).isEqualTo(330000L); 
        assertThat(result.get("elapsedTimeFormatted")).isEqualTo("00:05:30");
        assertThat(result.get("isRunning")).isEqualTo(false);
    }

    @Test
    @DisplayName("Should include elapsed time for running job")
    void shouldIncludeElapsedTimeForRunningJob() {
        
        LocalDateTime startTime = LocalDateTime.now().minusMinutes(2); 
        
        mockJobExecution.setStartTime(startTime);
        mockJobExecution.setEndTime(null); 
        mockJobExecution.setStatus(BatchStatus.STARTED);
        
        Long jobId = 100L;
        when(jobExplorer.getJobExecution(jobId)).thenReturn(mockJobExecution);

        
        Map<String, Object> result = jobStatusService.getJobStatus(jobId);

        
        assertThat(result).isNotNull();
        assertThat(result).containsKey("elapsedTimeSeconds");
        assertThat(result).containsKey("elapsedTimeMillis");
        assertThat(result).containsKey("elapsedTimeFormatted");
        assertThat(result).containsKey("isRunning");
        
        Long elapsedSeconds = (Long) result.get("elapsedTimeSeconds");
        Long elapsedMillis = (Long) result.get("elapsedTimeMillis");
        assertThat(elapsedSeconds).isGreaterThan(110L); 
        assertThat(elapsedSeconds).isLessThan(130L); 
        assertThat(elapsedMillis).isGreaterThan(110000L); 
        assertThat(elapsedMillis).isLessThan(130000L); 
        assertThat(result.get("isRunning")).isEqualTo(true);
    }

    @Test
    @DisplayName("Should handle job that hasn't started yet")
    void shouldHandleJobThatHasntStartedYet() {
        
        mockJobExecution.setStartTime(null);
        mockJobExecution.setEndTime(null);
        mockJobExecution.setStatus(BatchStatus.STARTING);
        
        Long jobId = 100L;
        when(jobExplorer.getJobExecution(jobId)).thenReturn(mockJobExecution);

        
        Map<String, Object> result = jobStatusService.getJobStatus(jobId);

        
        assertThat(result).isNotNull();
        assertThat(result).containsKey("elapsedTimeSeconds");
        assertThat(result).containsKey("elapsedTimeMillis");
        assertThat(result).containsKey("elapsedTimeFormatted");
        assertThat(result).containsKey("isRunning");
        
        assertThat(result.get("elapsedTimeSeconds")).isEqualTo(0);
        assertThat(result.get("elapsedTimeMillis")).isEqualTo(0L);
        assertThat(result.get("elapsedTimeFormatted")).isEqualTo("00:00:00");
        assertThat(result.get("isRunning")).isEqualTo(false);
    }

    @Test
    @DisplayName("Should return error when job not found")
    void shouldReturnErrorWhenJobNotFound() {
        
        Long jobId = 999L;
        when(jobExplorer.getJobExecution(jobId)).thenReturn(null);

        
        Map<String, Object> result = jobStatusService.getJobStatus(jobId);

        
        assertThat(result).isNotNull();
        assertThat(result).containsKey("error");
        assertThat(result.get("error")).isEqualTo("Job not found");
    }
} 