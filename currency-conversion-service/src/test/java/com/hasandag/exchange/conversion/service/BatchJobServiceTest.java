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
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BatchJobService Tests")
class BatchJobServiceTest {

    @Mock
    private JobStatusService jobStatusService;

    @Mock
    private OptimizedFileContentStoreService fileContentStoreService;

    @Mock
    private JobLauncher jobLauncher;

    @Mock
    private JobLauncher asyncJobLauncher;

    @Mock
    private Job bulkConversionJob;

    @Mock
    private JobExplorer jobExplorer;

    @Mock
    private MultipartFile multipartFile;

    @InjectMocks
    private BatchJobService batchJobService;

    private JobExecution mockJobExecution;
    private JobInstance mockJobInstance;

    @BeforeEach
    void setUp() {
        mockJobInstance = new JobInstance(1L, "bulkConversionJob");
        mockJobExecution = new JobExecution(mockJobInstance, 100L, new JobParameters());
        mockJobExecution.setStatus(BatchStatus.STARTING);
        mockJobExecution.setCreateTime(LocalDateTime.now());
    }

    @Test
    @DisplayName("Should process synchronous job successfully")
    void shouldProcessSynchronousJobSuccessfully() throws Exception {
        // Arrange
        String filename = "test.csv";
        String fileContent = "sourceAmount,sourceCurrency,targetCurrency\n100,USD,EUR";
        
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getOriginalFilename()).thenReturn(filename);
        when(multipartFile.getBytes()).thenReturn(fileContent.getBytes());
        when(multipartFile.getSize()).thenReturn((long) fileContent.length());
        when(fileContentStoreService.generateContentKey(filename)).thenReturn("test-key");
        when(jobLauncher.run(eq(bulkConversionJob), any(JobParameters.class))).thenReturn(mockJobExecution);

        // Act
        Map<String, Object> result = batchJobService.processJob(multipartFile, jobLauncher, bulkConversionJob);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).containsKey("jobId");
        assertThat(result).containsEntry("filename", filename);
        verify(fileContentStoreService).storeContent(eq("test-key"), eq(fileContent));
    }

    @Test
    @DisplayName("Should process asynchronous job successfully")
    void shouldProcessAsynchronousJobSuccessfully() throws Exception {
        // Arrange
        String filename = "test.csv";
        String fileContent = "sourceAmount,sourceCurrency,targetCurrency\n100,USD,EUR";
        
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getOriginalFilename()).thenReturn(filename);
        when(multipartFile.getBytes()).thenReturn(fileContent.getBytes());
        when(multipartFile.getSize()).thenReturn((long) fileContent.length());
        when(fileContentStoreService.generateContentKey(filename)).thenReturn("test-key");

        // Act
        Map<String, Object> result = batchJobService.processJobAsync(multipartFile, asyncJobLauncher, bulkConversionJob);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).containsKey("taskId");
        assertThat(result).containsEntry("status", "SUBMITTED");
        assertThat(result).containsEntry("filename", filename);
        verify(fileContentStoreService).storeContent(eq("test-key"), eq(fileContent));
    }

    @Test
    @DisplayName("Should return job status successfully")
    void shouldReturnJobStatusSuccessfully() {
        // Arrange
        Long jobId = 100L;
        Map<String, Object> expectedResponse = new HashMap<>();
        expectedResponse.put("jobId", jobId);
        expectedResponse.put("status", "COMPLETED");

        when(jobStatusService.getJobStatus(jobId)).thenReturn(expectedResponse);

        // Act
        Map<String, Object> result = batchJobService.getJobStatus(jobId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).containsEntry("jobId", jobId);
        assertThat(result).containsEntry("status", "COMPLETED");
        verify(jobStatusService).getJobStatus(jobId);
    }

    @Test
    @DisplayName("Should throw exception for async task not found")
    void shouldThrowExceptionForAsyncTaskNotFound() {
        // Arrange
        String taskId = "999";

        // Act & Assert
        assertThatThrownBy(() -> batchJobService.getAsyncJobStatus(taskId))
                .isInstanceOf(com.hasandag.exchange.conversion.exception.BatchJobException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("Should throw exception when job status contains error")
    void shouldThrowExceptionWhenJobStatusContainsError() {
        // Arrange
        Long jobId = 999L;
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Job not found");

        when(jobStatusService.getJobStatus(jobId)).thenReturn(errorResponse);

        // Act & Assert
        assertThatThrownBy(() -> batchJobService.getJobStatus(jobId))
                .isInstanceOf(com.hasandag.exchange.conversion.exception.BatchJobException.class);
        
        verify(jobStatusService).getJobStatus(jobId);
    }

    @Test
    @DisplayName("Should throw exception for empty file")
    void shouldThrowExceptionForEmptyFile() {
        // Arrange
        when(multipartFile.isEmpty()).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> batchJobService.processJob(multipartFile, jobLauncher, bulkConversionJob))
                .isInstanceOf(com.hasandag.exchange.conversion.exception.BatchJobException.class);
    }

    @Test
    @DisplayName("Should throw exception for invalid file type")
    void shouldThrowExceptionForInvalidFileType() {
        // Arrange
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getOriginalFilename()).thenReturn("test.txt");

        // Act & Assert
        assertThatThrownBy(() -> batchJobService.processJob(multipartFile, jobLauncher, bulkConversionJob))
                .isInstanceOf(com.hasandag.exchange.conversion.exception.BatchJobException.class);
    }

    @Test
    @DisplayName("Should return all jobs successfully")
    void shouldReturnAllJobsSuccessfully() {
        // Arrange
        Map<String, Object> expectedResponse = new HashMap<>();
        expectedResponse.put("jobs", Arrays.asList("job1", "job2"));
        expectedResponse.put("count", 2);

        when(jobStatusService.getAllJobs()).thenReturn(expectedResponse);

        // Act
        Map<String, Object> result = batchJobService.getAllJobs();

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).containsEntry("count", 2);
        verify(jobStatusService).getAllJobs();
    }

    @Test
    @DisplayName("Should return running jobs successfully")
    void shouldReturnRunningJobsSuccessfully() {
        // Arrange
        Map<String, Object> expectedResponse = new HashMap<>();
        expectedResponse.put("runningJobs", Arrays.asList("job1"));
        expectedResponse.put("count", 1);

        when(jobStatusService.getRunningJobs()).thenReturn(expectedResponse);

        // Act
        Map<String, Object> result = batchJobService.getRunningJobs();

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).containsEntry("count", 1);
        verify(jobStatusService).getRunningJobs();
    }

    @Test
    @DisplayName("Should return job statistics successfully")
    void shouldReturnJobStatisticsSuccessfully() {
        // Arrange
        Map<String, Object> expectedResponse = new HashMap<>();
        expectedResponse.put("totalJobs", 10);
        expectedResponse.put("completedJobs", 8);
        expectedResponse.put("failedJobs", 1);
        expectedResponse.put("runningJobs", 1);

        when(jobStatusService.getJobStatistics()).thenReturn(expectedResponse);

        // Act
        Map<String, Object> result = batchJobService.getJobStatistics();

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).containsEntry("totalJobs", 10);
        assertThat(result).containsEntry("completedJobs", 8);
        verify(jobStatusService).getJobStatistics();
    }

    @Test
    @DisplayName("Should cleanup job content successfully")
    void shouldCleanupJobContentSuccessfully() {
        // Arrange
        String contentKey = "test-content-key";

        // Act
        batchJobService.cleanupJobContent(contentKey);

        // Assert
        verify(fileContentStoreService).removeContent(contentKey);
    }

    @Test
    @DisplayName("Should return content store stats successfully")
    void shouldReturnContentStoreStatsSuccessfully() {
        // Arrange
        Map<String, Object> expectedStats = new HashMap<>();
        expectedStats.put("totalFiles", 5);
        expectedStats.put("totalSize", 1024L);

        when(fileContentStoreService.getStoreStats()).thenReturn(expectedStats);

        // Act
        Map<String, Object> result = batchJobService.getContentStoreStats();

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).containsEntry("totalFiles", 5);
        verify(fileContentStoreService).getStoreStats();
    }

    @Test
    @DisplayName("Should cleanup all content and return count")
    void shouldCleanupAllContentAndReturnCount() {
        // Arrange
        int expectedCount = 3;
        when(fileContentStoreService.clearAllContent()).thenReturn(expectedCount);

        // Act
        int result = batchJobService.cleanupAllContent();

        // Assert
        assertThat(result).isEqualTo(expectedCount);
        verify(fileContentStoreService).clearAllContent();
    }
} 