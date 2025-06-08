package com.hasandag.exchange.conversion.service;

import com.hasandag.exchange.conversion.dto.JobListResponse;
import com.hasandag.exchange.conversion.dto.JobStatisticsResponse;
import com.hasandag.exchange.conversion.dto.JobStatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.batch.core.*;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.NoSuchJobException;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JobStatusService Tests")
class JobStatusServiceTest {

    @Mock
    private JobExplorer jobExplorer;

    @InjectMocks
    private JobStatusService jobStatusService;

    private JobExecution mockJobExecution;
    private JobInstance mockJobInstance;
    private StepExecution mockStepExecution;

    @BeforeEach
    void setUp() {
        mockJobInstance = mock(JobInstance.class);
        mockJobExecution = mock(JobExecution.class);
        mockStepExecution = mock(StepExecution.class);
        ExitStatus exitStatus = mock(ExitStatus.class);
        JobParameters jobParameters = mock(JobParameters.class);

        // Setup common mock behavior
        when(mockJobInstance.getInstanceId()).thenReturn(1L);
        when(mockJobInstance.getJobName()).thenReturn("testJob");
        
        when(mockJobExecution.getId()).thenReturn(100L);
        when(mockJobExecution.getJobInstance()).thenReturn(mockJobInstance);
        when(mockJobExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(mockJobExecution.getStartTime()).thenReturn(LocalDateTime.now().minusMinutes(10));
        when(mockJobExecution.getEndTime()).thenReturn(LocalDateTime.now());
        when(mockJobExecution.getCreateTime()).thenReturn(LocalDateTime.now().minusMinutes(15));
        when(mockJobExecution.getExitStatus()).thenReturn(exitStatus);
        when(mockJobExecution.getJobParameters()).thenReturn(jobParameters);
        when(mockJobExecution.getStepExecutions()).thenReturn(Collections.singleton(mockStepExecution));
        
        when(exitStatus.getExitCode()).thenReturn("COMPLETED");
        when(jobParameters.getParameters()).thenReturn(new HashMap<>());
        
        // Setup step execution
        when(mockStepExecution.getReadCount()).thenReturn(100L);
        when(mockStepExecution.getWriteCount()).thenReturn(95L);
        when(mockStepExecution.getCommitCount()).thenReturn(10L);
        when(mockStepExecution.getReadSkipCount()).thenReturn(2L);
        when(mockStepExecution.getWriteSkipCount()).thenReturn(3L);
        when(mockStepExecution.getProcessSkipCount()).thenReturn(0L);
    }

    @Nested
    @DisplayName("getJobStatus Tests")
    class GetJobStatusTests {

        @Test
        @DisplayName("Should return job status when job exists")
        void shouldReturnJobStatusWhenJobExists() {
            // Arrange
            Long jobId = 100L;
            when(jobExplorer.getJobExecution(jobId)).thenReturn(mockJobExecution);

            // Act
            JobStatusResponse result = jobStatusService.getJobStatus(jobId);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getJobId()).isEqualTo(100L);
            assertThat(result.getJobInstanceId()).isEqualTo(1L);
            assertThat(result.getJobName()).isEqualTo("testJob");
            assertThat(result.getStatus()).isEqualTo("COMPLETED");
            assertThat(result.getExitStatus()).isEqualTo("COMPLETED");
            assertThat(result.getStartTime()).isNotNull();
            assertThat(result.getEndTime()).isNotNull();
            assertThat(result.getProgress()).isNotNull();
            assertThat(result.getProgress().getReadCount()).isEqualTo(100);
            assertThat(result.getProgress().getWriteCount()).isEqualTo(95);
            assertThat(result.getError()).isNull();
        }

        @Test
        @DisplayName("Should return error when job not found")
        void shouldReturnErrorWhenJobNotFound() {
            // Arrange
            Long jobId = 999L;
            when(jobExplorer.getJobExecution(jobId)).thenReturn(null);

            // Act
            JobStatusResponse result = jobStatusService.getJobStatus(jobId);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getError()).isEqualTo("Job not found");
            assertThat(result.getJobId()).isNull();
        }

        @Test
        @DisplayName("Should handle exception gracefully")
        void shouldHandleExceptionGracefully() {
            // Arrange
            Long jobId = 100L;
            when(jobExplorer.getJobExecution(jobId)).thenThrow(new RuntimeException("Database error"));

            // Act
            JobStatusResponse result = jobStatusService.getJobStatus(jobId);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getError()).contains("Error retrieving job status: Database error");
        }
    }

    @Nested
    @DisplayName("getRunningJobs Tests")
    class GetRunningJobsTests {

        @Test
        @DisplayName("Should return running jobs successfully")
        void shouldReturnRunningJobsSuccessfully() {
            // Arrange
            when(jobExplorer.getJobNames()).thenReturn(Arrays.asList("job1", "job2"));
            when(jobExplorer.findRunningJobExecutions("job1")).thenReturn(Collections.singleton(mockJobExecution));
            when(jobExplorer.findRunningJobExecutions("job2")).thenReturn(Collections.emptySet());

            // Act
            JobListResponse result = jobStatusService.getRunningJobs();

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getJobs()).hasSize(1);
            assertThat(result.getTotalJobs()).isEqualTo(1);
            assertThat(result.getError()).isNull();
        }

        @Test
        @DisplayName("Should handle exception in getRunningJobs")
        void shouldHandleExceptionInGetRunningJobs() {
            // Arrange
            when(jobExplorer.getJobNames()).thenThrow(new RuntimeException("Database error"));

            // Act
            JobListResponse result = jobStatusService.getRunningJobs();

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getError()).contains("Error retrieving running jobs: Database error");
            assertThat(result.getJobs()).isNull();
        }
    }

    @Nested
    @DisplayName("getJobStatistics Tests")
    class GetJobStatisticsTests {

        @Test
        @DisplayName("Should return job statistics successfully")
        void shouldReturnJobStatisticsSuccessfully() {
            // Arrange
            when(jobExplorer.getJobNames()).thenReturn(Arrays.asList("job1", "job2"));
            
            // Mock for job1
            JobInstance mockInstance1 = mock(JobInstance.class);
            JobExecution mockExecution1 = mock(JobExecution.class);
            when(mockExecution1.getStatus()).thenReturn(BatchStatus.COMPLETED);
            when(mockExecution1.getStartTime()).thenReturn(LocalDateTime.now().minusMinutes(10));
            when(mockExecution1.getEndTime()).thenReturn(LocalDateTime.now());
            
            when(jobExplorer.getJobInstances("job1", 0, 1000)).thenReturn(Arrays.asList(mockInstance1));
            when(jobExplorer.getJobExecutions(mockInstance1)).thenReturn(Arrays.asList(mockExecution1));
            
            // Mock for job2
            when(jobExplorer.getJobInstances("job2", 0, 1000)).thenReturn(Collections.emptyList());

            // Act
            JobStatisticsResponse result = jobStatusService.getJobStatistics();

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getStatistics()).hasSize(2);
            assertThat(result.getTotalJobTypes()).isEqualTo(2);
            assertThat(result.getError()).isNull();
            
            JobStatisticsResponse.JobTypeStatistics job1Stats = result.getStatistics().get("job1");
            assertThat(job1Stats.getTotalJobs()).isEqualTo(1);
            assertThat(job1Stats.getCompletedJobs()).isEqualTo(1);
            assertThat(job1Stats.getFailedJobs()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should handle exception in getJobStatistics")
        void shouldHandleExceptionInGetJobStatistics() {
            // Arrange
            when(jobExplorer.getJobNames()).thenThrow(new RuntimeException("Database error"));

            // Act
            JobStatisticsResponse result = jobStatusService.getJobStatistics();

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getError()).contains("Error retrieving job statistics: Database error");
        }
    }

    @Nested
    @DisplayName("getJobsByName Tests")
    class GetJobsByNameTests {

        @Test
        @DisplayName("Should return paginated jobs by name")
        void shouldReturnPaginatedJobsByName() throws NoSuchJobException {
            // Arrange
            String jobName = "testJob";
            int page = 0;
            int size = 10;
            
            JobInstance mockInstance = mock(JobInstance.class);
            when(jobExplorer.getJobInstances(jobName, 0, size)).thenReturn(Arrays.asList(mockInstance));
            when(jobExplorer.getJobExecutions(mockInstance)).thenReturn(Arrays.asList(mockJobExecution));
            when(jobExplorer.getJobInstanceCount(jobName)).thenReturn(25L);

            // Act
            JobListResponse result = jobStatusService.getJobsByName(jobName, page, size);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getJobs()).hasSize(1);
            assertThat(result.getTotalJobs()).isEqualTo(25);
            assertThat(result.getCurrentPage()).isEqualTo(0);
            assertThat(result.getPageSize()).isEqualTo(10);
            assertThat(result.getTotalPages()).isEqualTo(3); // ceil(25/10) = 3
            assertThat(result.getError()).isNull();
        }

        @Test
        @DisplayName("Should handle exception in getJobsByName")
        void shouldHandleExceptionInGetJobsByName() {
            // Arrange
            String jobName = "testJob";
            when(jobExplorer.getJobInstances(anyString(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("Database error"));

            // Act
            JobListResponse result = jobStatusService.getJobsByName(jobName, 0, 10);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getError()).contains("Error retrieving jobs: Database error");
        }
    }

    @Nested
    @DisplayName("getAllJobs Tests")
    class GetAllJobsTests {

        @Test
        @DisplayName("Should return all jobs with limit")
        void shouldReturnAllJobsWithLimit() {
            // Arrange
            when(jobExplorer.getJobNames()).thenReturn(Arrays.asList("job1"));
            
            JobInstance mockInstance = mock(JobInstance.class);
            when(jobExplorer.getJobInstances("job1", 0, 50)).thenReturn(Arrays.asList(mockInstance));
            when(jobExplorer.getJobExecutions(mockInstance)).thenReturn(Arrays.asList(mockJobExecution));

            // Act
            JobListResponse result = jobStatusService.getAllJobs();

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getJobs()).hasSize(1);
            assertThat(result.getTotalJobs()).isEqualTo(1);
            assertThat(result.getError()).isNull();
        }
    }

    @Nested
    @DisplayName("Helper Methods Tests")
    class HelperMethodsTests {

        @Test
        @DisplayName("Should format elapsed time correctly")
        void shouldFormatElapsedTimeCorrectly() {
            // This would require exposing the private method or testing through public methods
            // For now, we test the behavior through the public API
            
            // Arrange
            Long jobId = 100L;
            LocalDateTime start = LocalDateTime.now().minusHours(2).minusMinutes(30).minusSeconds(45);
            LocalDateTime end = LocalDateTime.now();
            
            when(mockJobExecution.getStartTime()).thenReturn(start);
            when(mockJobExecution.getEndTime()).thenReturn(end);
            when(jobExplorer.getJobExecution(jobId)).thenReturn(mockJobExecution);

            // Act
            JobStatusResponse result = jobStatusService.getJobStatus(jobId);

            // Assert
            assertThat(result.getElapsedTime()).isNotNull();
            assertThat(result.getElapsedTimeMs()).isGreaterThan(0L);
        }

        @Test
        @DisplayName("Should calculate progress info correctly")
        void shouldCalculateProgressInfoCorrectly() {
            // Arrange
            Long jobId = 100L;
            when(jobExplorer.getJobExecution(jobId)).thenReturn(mockJobExecution);

            // Act
            JobStatusResponse result = jobStatusService.getJobStatus(jobId);

            // Assert
            JobStatusResponse.JobProgressInfo progress = result.getProgress();
            assertThat(progress).isNotNull();
            assertThat(progress.getReadCount()).isEqualTo(100);
            assertThat(progress.getWriteCount()).isEqualTo(95);
            assertThat(progress.getCommitCount()).isEqualTo(10);
            assertThat(progress.getTotalSkipCount()).isEqualTo(5); // 2+3+0
            assertThat(progress.getReadSkipCount()).isEqualTo(2);
            assertThat(progress.getWriteSkipCount()).isEqualTo(3);
            assertThat(progress.getProcessSkipCount()).isEqualTo(0);
        }
    }
} 