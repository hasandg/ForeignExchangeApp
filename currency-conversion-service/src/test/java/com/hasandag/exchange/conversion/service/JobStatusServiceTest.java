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
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
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
        mockJobInstance = new JobInstance(1L, "testJob");
        mockJobExecution = new JobExecution(mockJobInstance, 100L, new JobParameters());
        mockJobExecution.setStatus(BatchStatus.COMPLETED);
        mockJobExecution.setStartTime(LocalDateTime.now().minusMinutes(30));
        mockJobExecution.setEndTime(LocalDateTime.now().minusMinutes(25));

        mockStepExecution = new StepExecution("testStep", mockJobExecution);
        mockStepExecution.setReadCount(100);
        mockStepExecution.setWriteCount(95);
        mockStepExecution.setCommitCount(10);
        mockStepExecution.setRollbackCount(1);
        
        List<StepExecution> stepExecutions = Arrays.asList(mockStepExecution);
        mockJobExecution.addStepExecutions(stepExecutions);
    }

    @Nested
    @DisplayName("Get Job Status Tests")
    class GetJobStatusTests {

        @Test
        @DisplayName("Should return job status successfully")
        void shouldReturnJobStatusSuccessfully() {
            Long jobId = 100L;
            when(jobExplorer.getJobExecution(jobId)).thenReturn(mockJobExecution);

            JobStatusResponse response = jobStatusService.getJobStatus(jobId);

            assertThat(response).isNotNull();
            assertThat(response.getJobId()).isEqualTo(jobId);
            assertThat(response.getJobName()).isEqualTo("testJob");
            assertThat(response.getStatus()).isEqualTo("COMPLETED");
            assertThat(response.getError()).isNull();
        }

        @Test
        @DisplayName("Should return error response when job not found")
        void shouldReturnErrorResponseWhenJobNotFound() {
            Long jobId = 999L;
            when(jobExplorer.getJobExecution(jobId)).thenReturn(null);

            JobStatusResponse response = jobStatusService.getJobStatus(jobId);

            assertThat(response).isNotNull();
            assertThat(response.getJobId()).isNull();
            assertThat(response.getError()).isEqualTo("Job not found");
        }

        @Test
        @DisplayName("Should return error response on exception")
        void shouldReturnErrorResponseOnException() {
            Long jobId = 100L;
            when(jobExplorer.getJobExecution(jobId)).thenThrow(new RuntimeException("Database error"));

            JobStatusResponse response = jobStatusService.getJobStatus(jobId);

            assertThat(response).isNotNull();
            assertThat(response.getJobId()).isNull();
            assertThat(response.getError()).contains("Database error");
        }
    }

    @Nested
    @DisplayName("Get Running Jobs Tests")
    class GetRunningJobsTests {

        @Test
        @DisplayName("Should return running jobs successfully")
        void shouldReturnRunningJobsSuccessfully() {
            Set<JobExecution> runningJobs = new HashSet<>();
            runningJobs.add(mockJobExecution);
            when(jobExplorer.findRunningJobExecutions("testJob")).thenReturn(runningJobs);
            when(jobExplorer.getJobNames()).thenReturn(Arrays.asList("testJob"));

            JobListResponse response = jobStatusService.getRunningJobs();

            assertThat(response).isNotNull();
            assertThat(response.getJobs()).hasSize(1);
            assertThat(response.getTotalJobs()).isEqualTo(1);
            assertThat(response.getError()).isNull();
        }

        @Test
        @DisplayName("Should return error response on exception")
        void shouldReturnErrorResponseOnException() {
            when(jobExplorer.getJobNames()).thenThrow(new RuntimeException("Database error"));

            JobListResponse response = jobStatusService.getRunningJobs();

            assertThat(response).isNotNull();
            assertThat(response.getError()).contains("Database error");
        }
    }

    @Nested
    @DisplayName("Get All Jobs Tests")
    class GetAllJobsTests {

        @Test
        @DisplayName("Should return all jobs successfully")
        void shouldReturnAllJobsSuccessfully() {
            List<JobInstance> jobInstances = Arrays.asList(mockJobInstance);
            when(jobExplorer.getJobNames()).thenReturn(Arrays.asList("testJob"));
            when(jobExplorer.getJobInstances(eq("testJob"), eq(0), eq(50))).thenReturn(jobInstances);
            when(jobExplorer.getJobExecutions(mockJobInstance)).thenReturn(Arrays.asList(mockJobExecution));

            JobListResponse response = jobStatusService.getAllJobs();

            assertThat(response).isNotNull();
            assertThat(response.getJobs()).hasSize(1);
            assertThat(response.getTotalJobs()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Get Job Statistics Tests")
    class GetJobStatisticsTests {

        @Test
        @DisplayName("Should return job statistics successfully")
        void shouldReturnJobStatisticsSuccessfully() {
            when(jobExplorer.getJobNames()).thenReturn(Arrays.asList("testJob1", "testJob2"));

            JobStatisticsResponse response = jobStatusService.getJobStatistics();

            assertThat(response).isNotNull();
            assertThat(response.getTotalJobTypes()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should return error response on exception")
        void shouldReturnErrorResponseOnException() {
            when(jobExplorer.getJobNames()).thenThrow(new RuntimeException("Database error"));

            JobStatisticsResponse response = jobStatusService.getJobStatistics();

            assertThat(response).isNotNull();
            assertThat(response.getError()).contains("Database error");
        }
    }

    @Nested
    @DisplayName("Get Jobs By Name Tests")
    class GetJobsByNameTests {

        @Test
        @DisplayName("Should return jobs by name successfully")
        void shouldReturnJobsByNameSuccessfully() {
            String jobName = "testJob";
            int page = 0;
            int size = 10;
            
            List<JobInstance> jobInstances = Arrays.asList(mockJobInstance);
            when(jobExplorer.getJobInstances(eq(jobName), eq(page * size), eq(size))).thenReturn(jobInstances);
            when(jobExplorer.getJobExecutions(mockJobInstance)).thenReturn(Arrays.asList(mockJobExecution));
            try {
                doReturn(1L).when(jobExplorer).getJobInstanceCount(jobName);
            } catch (Exception e) {
                // This won't happen in the test
            }

            JobListResponse response = jobStatusService.getJobsByName(jobName, page, size);

            assertThat(response).isNotNull();
            assertThat(response.getJobs()).hasSize(1);
            assertThat(response.getTotalJobs()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should return error response on exception")
        void shouldReturnErrorResponseOnException() {
            String jobName = "testJob";
            when(jobExplorer.getJobInstances(anyString(), any(Integer.class), any(Integer.class)))
                    .thenThrow(new RuntimeException("Database error"));

            JobListResponse response = jobStatusService.getJobsByName(jobName, 0, 10);

            assertThat(response).isNotNull();
            assertThat(response.getError()).contains("Database error");
        }
    }

    @Nested
    @DisplayName("Helper Method Tests")
    class HelperMethodTests {

        @Test
        @DisplayName("Should convert job execution to DTO")
        void shouldConvertJobExecutionToDto() {
            when(jobExplorer.getJobExecution(100L)).thenReturn(mockJobExecution);
            
            JobStatusResponse response = jobStatusService.getJobStatus(100L);
            
            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Should calculate execution time correctly")
        void shouldCalculateExecutionTimeCorrectly() {
            when(jobExplorer.getJobExecution(100L)).thenReturn(mockJobExecution);

            JobStatusResponse response = jobStatusService.getJobStatus(100L);

            assertThat(response.getElapsedTimeMs()).isGreaterThan(0);
        }
    }
} 