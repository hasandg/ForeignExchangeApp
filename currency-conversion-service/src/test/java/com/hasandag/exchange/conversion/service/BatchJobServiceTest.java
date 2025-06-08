package com.hasandag.exchange.conversion.service;

import com.hasandag.exchange.common.exception.BatchJobException;
import com.hasandag.exchange.conversion.dto.JobStatusResponse;
import com.hasandag.exchange.conversion.dto.JobListResponse;
import com.hasandag.exchange.conversion.dto.JobStatisticsResponse;
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
@DisplayName("BatchJobService Tests - Direct Service Usage (No Delegation)")
class BatchJobServiceTest {

    @Mock
    private FileValidationService fileValidationService;

    @Mock
    private JobExecutionService jobExecutionService;

    @Mock
    private AsyncTaskManager asyncTaskManager;

    @Mock
    private JobLauncher jobLauncher;

    @Mock
    private JobLauncher asyncJobLauncher;

    @Mock
    private Job bulkConversionJob;

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
        // Arrange
        BatchJobResponse mockResponse = BatchJobResponse.sync(
            100L, 1L, "COMPLETED", "test.csv", 1024L, "content-key-123"
        );
        
        when(jobExecutionService.executeSyncJob(any())).thenReturn(mockResponse);

        // Act
        Map<String, Object> result = batchJobService.processJob(multipartFile, jobLauncher, bulkConversionJob);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.get("jobId")).isEqualTo(100L);
        assertThat(result.get("status")).isEqualTo("COMPLETED");
        
        verify(fileValidationService).validateFile(multipartFile);
        verify(jobExecutionService).executeSyncJob(any());
    }

    @Test
    @DisplayName("Should process asynchronous job successfully")
    void shouldProcessAsynchronousJobSuccessfully() throws Exception {
        // Arrange
        String taskId = "task-123";
        String contentKey = "content-key-123";
        
        when(asyncTaskManager.generateTaskId()).thenReturn(taskId);
        when(jobExecutionService.prepareFileContent(multipartFile)).thenReturn(contentKey);
        when(jobExecutionService.executeAsyncJob(any(), eq(contentKey))).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(
            BatchJobResponse.async(taskId, "SUBMITTED", "Job submitted asynchronously", "test.csv", 1024L, contentKey)
        ));

        // Act
        Map<String, Object> result = batchJobService.processJobAsync(multipartFile, asyncJobLauncher, bulkConversionJob);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.get("taskId")).isEqualTo(taskId);
        assertThat(result.get("status")).isEqualTo("SUBMITTED");
        
        verify(fileValidationService).validateFile(multipartFile);
        verify(asyncTaskManager).generateTaskId();
        verify(jobExecutionService).prepareFileContent(multipartFile);
        verify(asyncTaskManager).registerTask(eq(taskId), any());
    }

    @Test
    @DisplayName("Should return async job status successfully")
    void shouldReturnAsyncJobStatusSuccessfully() {
        // Arrange
        String taskId = "task-123";
        BatchJobResponse mockResponse = BatchJobResponse.async(
            taskId, "RUNNING", "Job is running", "test.csv", 1024L, "content-key-123"
        );
        
        when(asyncTaskManager.getTaskStatus(taskId)).thenReturn(mockResponse);

        // Act
        Map<String, Object> result = batchJobService.getAsyncJobStatus(taskId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.get("taskId")).isEqualTo(taskId);
        assertThat(result.get("status")).isEqualTo("RUNNING");
        
        verify(asyncTaskManager).getTaskStatus(taskId);
    }

    @Test
    @DisplayName("Should throw exception for async task not found")
    void shouldThrowExceptionForAsyncTaskNotFound() {
        // Arrange
        String taskId = "999";
        when(asyncTaskManager.getTaskStatus(taskId)).thenThrow(
            new BatchJobException("TASK_NOT_FOUND", "Task not found", org.springframework.http.HttpStatus.NOT_FOUND)
        );

        // Act & Assert
        assertThatThrownBy(() -> batchJobService.getAsyncJobStatus(taskId))
                .isInstanceOf(BatchJobException.class)
                .hasMessageContaining("Task not found");
                
        verify(asyncTaskManager).getTaskStatus(taskId);
    }

    @Test
    @DisplayName("Should throw exception for empty file")
    void shouldThrowExceptionForEmptyFile() {
        // Arrange
        doThrow(BatchJobException.emptyFile()).when(fileValidationService).validateFile(multipartFile);

        // Act & Assert
        assertThatThrownBy(() -> batchJobService.processJob(multipartFile, jobLauncher, bulkConversionJob))
                .isInstanceOf(BatchJobException.class);
                
        verify(fileValidationService).validateFile(multipartFile);
    }

    @Test
    @DisplayName("Should throw exception for invalid file type")
    void shouldThrowExceptionForInvalidFileType() {
        // Arrange
        doThrow(BatchJobException.invalidFileType()).when(fileValidationService).validateFile(multipartFile);

        // Act & Assert
        assertThatThrownBy(() -> batchJobService.processJob(multipartFile, jobLauncher, bulkConversionJob))
                .isInstanceOf(BatchJobException.class);
                
        verify(fileValidationService).validateFile(multipartFile);
    }

    @Test
    @DisplayName("Should handle async job processing error")
    void shouldHandleAsyncJobProcessingError() throws Exception {
        // Arrange
        String taskId = "task-123";
        when(asyncTaskManager.generateTaskId()).thenReturn(taskId);
        when(jobExecutionService.prepareFileContent(multipartFile)).thenThrow(new RuntimeException("File processing error"));

        // Act & Assert
        assertThatThrownBy(() -> batchJobService.processJobAsync(multipartFile, asyncJobLauncher, bulkConversionJob))
                .isInstanceOf(BatchJobException.class)
                .hasMessageContaining("Error processing uploaded file");
                
        verify(fileValidationService).validateFile(multipartFile);
        verify(asyncTaskManager).generateTaskId();
        verify(jobExecutionService).prepareFileContent(multipartFile);
    }
} 