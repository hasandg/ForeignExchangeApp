package com.hasandag.exchange.conversion.model;

import com.hasandag.exchange.common.dto.ConversionRequest;
import com.hasandag.exchange.common.dto.ConversionResponse; 
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamingProcessingEvent {
    
    private String taskId;
    private String filename;
    private EventType eventType;
    private String message;
    private long processedCount;
    private long successCount;
    private long errorCount;
    private ConversionResponse conversionResult;
    private ConversionRequest failedRequest;
    private String errorMessage;
    
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    public enum EventType {
        STARTED,
        PROGRESS_UPDATE,
        CONVERSION_SUCCESS,
        CONVERSION_ERROR,
        COMPLETED,
        FAILED
    }
    
    public static StreamingProcessingEvent started(String taskId, String filename) {
        return StreamingProcessingEvent.builder()
                .taskId(taskId)
                .filename(filename)
                .eventType(EventType.STARTED)
                .message("Processing started")
                .build();
    }
    
    public static StreamingProcessingEvent progress(String taskId, long processed, long success, long errors) {
        return StreamingProcessingEvent.builder()
                .taskId(taskId)
                .eventType(EventType.PROGRESS_UPDATE)
                .processedCount(processed)
                .successCount(success)
                .errorCount(errors)
                .message(String.format("Progress: %d processed, %d success, %d errors", processed, success, errors))
                .build();
    }
    
    public static StreamingProcessingEvent conversionSuccess(String taskId, ConversionResponse result) {
        return StreamingProcessingEvent.builder()
                .taskId(taskId)
                .eventType(EventType.CONVERSION_SUCCESS)
                .conversionResult(result)
                .message("Conversion completed successfully")
                .build();
    }
    
    public static StreamingProcessingEvent conversionError(String taskId, ConversionRequest request, String error) {
        return StreamingProcessingEvent.builder()
                .taskId(taskId)
                .eventType(EventType.CONVERSION_ERROR)
                .failedRequest(request)
                .errorMessage(error)
                .message("Conversion failed: " + error)
                .build();
    }
    
    public static StreamingProcessingEvent completed(String taskId, long processed, long success, long errors) {
        return StreamingProcessingEvent.builder()
                .taskId(taskId)
                .eventType(EventType.COMPLETED)
                .processedCount(processed)
                .successCount(success)
                .errorCount(errors)
                .message(String.format("Processing completed: %d processed, %d success, %d errors", processed, success, errors))
                .build();
    }
    
    public static StreamingProcessingEvent failed(String taskId, String errorMessage) {
        return StreamingProcessingEvent.builder()
                .taskId(taskId)
                .eventType(EventType.FAILED)
                .errorMessage(errorMessage)
                .message("Processing failed: " + errorMessage)
                .build();
    }
    
    public double getSuccessRate() {
        return processedCount > 0 ? (double) successCount / processedCount * 100 : 0.0;
    }
    
    public boolean isProgressEvent() {
        return eventType == EventType.PROGRESS_UPDATE;
    }
    
    public boolean isCompletionEvent() {
        return eventType == EventType.COMPLETED || eventType == EventType.FAILED;
    }
} 