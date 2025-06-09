package com.hasandag.exchange.conversion.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactiveProcessingResult {
    
    private String taskId;
    private String filename;
    private boolean success;
    private String errorMessage;
    private long processedRecords;
    private long successfulRecords;
    private long failedRecords;
    private long estimatedRecords;
    private String backpressureStrategy;
    private int batchSize;
    private String processingMode;
    
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    public static ReactiveProcessingResult success(String taskId, String filename, 
                                                 long processedRecords, long successfulRecords, 
                                                 long failedRecords, String backpressureStrategy, 
                                                 int batchSize) {
        return ReactiveProcessingResult.builder()
                .taskId(taskId)
                .filename(filename)
                .success(true)
                .processedRecords(processedRecords)
                .successfulRecords(successfulRecords)
                .failedRecords(failedRecords)
                .backpressureStrategy(backpressureStrategy)
                .batchSize(batchSize)
                .estimatedRecords(processedRecords)
                .processingMode("REACTIVE")
                .build();
    }
    
    public static ReactiveProcessingResult failed(String taskId, String errorMessage) {
        return ReactiveProcessingResult.builder()
                .taskId(taskId)
                .success(false)
                .errorMessage(errorMessage)
                .processingMode("FAILED")
                .build();
    }
    
    public double getSuccessRate() {
        return processedRecords > 0 ? (double) successfulRecords / processedRecords * 100 : 0.0;
    }
    
    public boolean hasErrors() {
        return failedRecords > 0;
    }
} 