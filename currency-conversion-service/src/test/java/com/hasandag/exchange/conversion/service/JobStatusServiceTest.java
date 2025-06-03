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
        // Arrange
        LocalDateTime startTime = LocalDateTime.of(2024, 1, 1, 10, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2024, 1, 1, 10, 5, 30); // 5 minutes 30 seconds later
        
        mockJobExecution.setStartTime(startTime);
        mockJobExecution.setEndTime(endTime);
        
        Long jobId = 100L;
        when(jobExplorer.getJobExecution(jobId)).thenReturn(mockJobExecution);

        // Act
        Map<String, Object> result = jobStatusService.getJobStatus(jobId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).containsKey("elapsedTimeSeconds");
        assertThat(result).containsKey("elapsedTimeMillis");
        assertThat(result).containsKey("elapsedTimeFormatted");
        assertThat(result).containsKey("isRunning");
        
        assertThat(result.get("elapsedTimeSeconds")).isEqualTo(330L); // 5 minutes 30 seconds = 330 seconds
        assertThat(result.get("elapsedTimeMillis")).isEqualTo(330000L); // 330 seconds = 330000 milliseconds
        assertThat(result.get("elapsedTimeFormatted")).isEqualTo("00:05:30");
        assertThat(result.get("isRunning")).isEqualTo(false);
    }

    @Test
    @DisplayName("Should include elapsed time for running job")
    void shouldIncludeElapsedTimeForRunningJob() {
        // Arrange
        LocalDateTime startTime = LocalDateTime.now().minusMinutes(2); // Started 2 minutes ago
        
        mockJobExecution.setStartTime(startTime);
        mockJobExecution.setEndTime(null); // Still running
        mockJobExecution.setStatus(BatchStatus.STARTED);
        
        Long jobId = 100L;
        when(jobExplorer.getJobExecution(jobId)).thenReturn(mockJobExecution);

        // Act
        Map<String, Object> result = jobStatusService.getJobStatus(jobId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).containsKey("elapsedTimeSeconds");
        assertThat(result).containsKey("elapsedTimeMillis");
        assertThat(result).containsKey("elapsedTimeFormatted");
        assertThat(result).containsKey("isRunning");
        
        Long elapsedSeconds = (Long) result.get("elapsedTimeSeconds");
        Long elapsedMillis = (Long) result.get("elapsedTimeMillis");
        assertThat(elapsedSeconds).isGreaterThan(110L); // Should be around 120 seconds (2 minutes)
        assertThat(elapsedSeconds).isLessThan(130L); // Allow some tolerance
        assertThat(elapsedMillis).isGreaterThan(110000L); // Should be around 120000 milliseconds
        assertThat(elapsedMillis).isLessThan(130000L); // Allow some tolerance
        assertThat(result.get("isRunning")).isEqualTo(true);
    }

    @Test
    @DisplayName("Should handle job that hasn't started yet")
    void shouldHandleJobThatHasntStartedYet() {
        // Arrange
        mockJobExecution.setStartTime(null);
        mockJobExecution.setEndTime(null);
        mockJobExecution.setStatus(BatchStatus.STARTING);
        
        Long jobId = 100L;
        when(jobExplorer.getJobExecution(jobId)).thenReturn(mockJobExecution);

        // Act
        Map<String, Object> result = jobStatusService.getJobStatus(jobId);

        // Assert
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
        // Arrange
        Long jobId = 999L;
        when(jobExplorer.getJobExecution(jobId)).thenReturn(null);

        // Act
        Map<String, Object> result = jobStatusService.getJobStatus(jobId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).containsKey("error");
        assertThat(result.get("error")).isEqualTo("Job not found");
    }
} 