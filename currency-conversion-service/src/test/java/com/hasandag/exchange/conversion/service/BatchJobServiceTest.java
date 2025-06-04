package com.hasandag.exchange.conversion.service;

import com.hasandag.exchange.common.exception.BatchJobException;
import com.hasandag.exchange.conversion.model.BatchJobResponse;
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
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("BatchJobService Tests - Refactored")
class BatchJobServiceTest {

    
    @Mock
    private FileValidationService fileValidationService;

    @Mock
    private JobExecutionService jobExecutionService;

    @Mock
    private AsyncTaskManager asyncTaskManager;

    @Mock
    private JobStatusServiceAdapter jobStatusServiceAdapter;

    @Mock
    private FileContentStoreService fileContentStoreService;

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
        
        
        lenient().when(multipartFile.getOriginalFilename()).thenReturn("test.csv");
        lenient().when(multipartFile.getSize()).thenReturn(1024L);
    }

    @Test
    @DisplayName("Should process synchronous job successfully")
    void shouldProcessSynchronousJobSuccessfully() {
        
        BatchJobResponse mockResponse = BatchJobResponse.sync(
            100L, 1L, "COMPLETED", "test.csv", 1024L, "content-key-123"
        );
        
        when(jobExecutionService.executeSyncJob(any())).thenReturn(mockResponse);

        
        Map<String, Object> result = batchJobService.processJob(multipartFile, jobLauncher, bulkConversionJob);

        
        assertThat(result).isNotNull();
        assertThat(result.get("jobId")).isEqualTo(100L);
        assertThat(result.get("status")).isEqualTo("COMPLETED");
        
        verify(fileValidationService).validateFile(multipartFile);
        verify(jobExecutionService).executeSyncJob(any());
    }

    @Test
    @DisplayName("Should process asynchronous job successfully")
    void shouldProcessAsynchronousJobSuccessfully() throws Exception {
        
        String taskId = "task-123";
        String contentKey = "content-key-123";
        
        when(asyncTaskManager.generateTaskId()).thenReturn(taskId);
        when(jobExecutionService.prepareFileContent(multipartFile)).thenReturn(contentKey);
        when(jobExecutionService.executeAsyncJob(any(), eq(contentKey))).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(
            BatchJobResponse.async(taskId, "SUBMITTED", "Job submitted asynchronously", "test.csv", 1024L, contentKey)
        ));

        
        Map<String, Object> result = batchJobService.processJobAsync(multipartFile, asyncJobLauncher, bulkConversionJob);

        
        assertThat(result).isNotNull();
        assertThat(result.get("taskId")).isEqualTo(taskId);
        assertThat(result.get("status")).isEqualTo("SUBMITTED");
        
        verify(fileValidationService).validateFile(multipartFile);
        verify(asyncTaskManager).generateTaskId();
        verify(jobExecutionService).prepareFileContent(multipartFile);
        verify(asyncTaskManager).registerTask(eq(taskId), any());
    }

    @Test
    @DisplayName("Should return job status successfully")
    void shouldReturnJobStatusSuccessfully() {
        
        Long jobId = 100L;
        Map<String, Object> expectedResponse = new HashMap<>();
        expectedResponse.put("jobId", jobId);
        expectedResponse.put("status", "COMPLETED");

        when(jobStatusServiceAdapter.getJobStatus(jobId)).thenReturn(expectedResponse);

        
        Map<String, Object> result = batchJobService.getJobStatus(jobId);

        
        assertThat(result).isNotNull();
        assertThat(result.get("jobId")).isEqualTo(jobId);
        verify(jobStatusServiceAdapter).getJobStatus(jobId);
    }

    @Test
    @DisplayName("Should throw exception for async task not found")
    void shouldThrowExceptionForAsyncTaskNotFound() {
        
        String taskId = "999";
        when(asyncTaskManager.getTaskStatus(taskId)).thenThrow(
            new BatchJobException("TASK_NOT_FOUND", "Task not found", org.springframework.http.HttpStatus.NOT_FOUND)
        );

        
        assertThatThrownBy(() -> batchJobService.getAsyncJobStatus(taskId))
                .isInstanceOf(BatchJobException.class)
                .hasMessageContaining("Task not found");
                
        verify(asyncTaskManager).getTaskStatus(taskId);
    }

    @Test
    @DisplayName("Should throw exception when job status contains error")
    void shouldThrowExceptionWhenJobStatusContainsError() {
        
        Long jobId = 999L;
        when(jobStatusServiceAdapter.getJobStatus(jobId)).thenThrow(
            BatchJobException.jobNotFound(jobId)
        );

        
        assertThatThrownBy(() -> batchJobService.getJobStatus(jobId))
                .isInstanceOf(BatchJobException.class);
        
        verify(jobStatusServiceAdapter).getJobStatus(jobId);
    }

    @Test
    @DisplayName("Should throw exception for empty file")
    void shouldThrowExceptionForEmptyFile() {
        
        doThrow(BatchJobException.emptyFile()).when(fileValidationService).validateFile(multipartFile);

        
        assertThatThrownBy(() -> batchJobService.processJob(multipartFile, jobLauncher, bulkConversionJob))
                .isInstanceOf(BatchJobException.class);
                
        verify(fileValidationService).validateFile(multipartFile);
    }

    @Test
    @DisplayName("Should throw exception for invalid file type")
    void shouldThrowExceptionForInvalidFileType() {
        
        doThrow(BatchJobException.invalidFileType()).when(fileValidationService).validateFile(multipartFile);

        
        assertThatThrownBy(() -> batchJobService.processJob(multipartFile, jobLauncher, bulkConversionJob))
                .isInstanceOf(BatchJobException.class);
                
        verify(fileValidationService).validateFile(multipartFile);
    }

    @Test
    @DisplayName("Should return all jobs successfully")
    void shouldReturnAllJobsSuccessfully() {
        
        Map<String, Object> expectedResponse = new HashMap<>();
        expectedResponse.put("jobs", Arrays.asList("job1", "job2"));
        expectedResponse.put("count", 2);

        when(jobStatusServiceAdapter.getAllJobs()).thenReturn(expectedResponse);

        
        Map<String, Object> result = batchJobService.getAllJobs();

        
        assertThat(result).isNotNull();
        assertThat(result).containsEntry("count", 2);
        verify(jobStatusServiceAdapter).getAllJobs();
    }

    @Test
    @DisplayName("Should return running jobs successfully")
    void shouldReturnRunningJobsSuccessfully() {
        
        Map<String, Object> expectedResponse = new HashMap<>();
        expectedResponse.put("runningJobs", Arrays.asList("job1"));
        expectedResponse.put("count", 1);

        when(jobStatusServiceAdapter.getRunningJobs()).thenReturn(expectedResponse);

        
        Map<String, Object> result = batchJobService.getRunningJobs();

        
        assertThat(result).isNotNull();
        assertThat(result).containsEntry("count", 1);
        verify(jobStatusServiceAdapter).getRunningJobs();
    }

    @Test
    @DisplayName("Should return job statistics successfully")
    void shouldReturnJobStatisticsSuccessfully() {
        
        Map<String, Object> expectedResponse = new HashMap<>();
        expectedResponse.put("totalJobs", 10);
        expectedResponse.put("completedJobs", 8);
        expectedResponse.put("failedJobs", 1);
        expectedResponse.put("runningJobs", 1);

        when(jobStatusServiceAdapter.getJobStatistics()).thenReturn(expectedResponse);

        
        Map<String, Object> result = batchJobService.getJobStatistics();

        
        assertThat(result).isNotNull();
        assertThat(result).containsEntry("totalJobs", 10);
        assertThat(result).containsEntry("completedJobs", 8);
        verify(jobStatusServiceAdapter).getJobStatistics();
    }

    @Test
    @DisplayName("Should cleanup job content successfully")
    void shouldCleanupJobContentSuccessfully() {
        
        String contentKey = "test-content-key";

        
        batchJobService.cleanupJobContent(contentKey);

        
        verify(fileContentStoreService).removeContent(contentKey);
    }

    @Test
    @DisplayName("Should return content store stats successfully")
    void shouldReturnContentStoreStatsSuccessfully() {
        
        Map<String, Object> expectedStats = new HashMap<>();
        expectedStats.put("totalFiles", 5);
        expectedStats.put("totalSize", 1024L);

        when(fileContentStoreService.getStoreStats()).thenReturn(expectedStats);

        
        Map<String, Object> result = batchJobService.getContentStoreStats();

        
        assertThat(result).isNotNull();
        assertThat(result).containsEntry("totalFiles", 5);
        verify(fileContentStoreService).getStoreStats();
    }
    
    @Test
    @DisplayName("Should return active async task count")
    void shouldReturnActiveAsyncTaskCount() {
        
        when(asyncTaskManager.getActiveTaskCount()).thenReturn(3);

        
        int result = batchJobService.getActiveAsyncTaskCount();

        
        assertThat(result).isEqualTo(3);
        verify(asyncTaskManager).getActiveTaskCount();
    }
    
    @Test
    @DisplayName("Should cleanup completed async tasks")
    void shouldCleanupCompletedAsyncTasks() {
        
        when(asyncTaskManager.cleanupCompletedTasks()).thenReturn(2);

        
        int result = batchJobService.cleanupCompletedAsyncTasks();

        
        assertThat(result).isEqualTo(2);
        verify(asyncTaskManager).cleanupCompletedTasks();
    }
} 