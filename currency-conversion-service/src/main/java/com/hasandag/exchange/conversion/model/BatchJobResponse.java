package com.hasandag.exchange.conversion.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchJobResponse {
    
    private Long jobId;
    private Long jobInstanceId;
    private String taskId;
    private String status;
    private String message;
    private String filename;
    private Long fileSize;
    private String contentKey;
    private String error;
    
    public Map<String, Object> toMap() {
        Map<String, Object> map = new java.util.HashMap<>();
        
        if (jobId != null) map.put("jobId", jobId);
        if (jobInstanceId != null) map.put("jobInstanceId", jobInstanceId);
        if (taskId != null) map.put("taskId", taskId);
        if (status != null) map.put("status", status);
        if (message != null) map.put("message", message);
        if (filename != null) map.put("filename", filename);
        if (fileSize != null) map.put("fileSize", fileSize);
        if (contentKey != null) map.put("contentKey", contentKey);
        if (error != null) map.put("error", error);
        
        return map;
    }
    
    public static BatchJobResponse sync(Long jobId, Long jobInstanceId, String status, 
                                       String filename, Long fileSize, String contentKey) {
        return BatchJobResponse.builder()
                .jobId(jobId)
                .jobInstanceId(jobInstanceId)
                .status(status)
                .filename(filename)
                .fileSize(fileSize)
                .contentKey(contentKey)
                .build();
    }
    
    public static BatchJobResponse async(String taskId, String status, String message,
                                        String filename, Long fileSize, String contentKey) {
        return BatchJobResponse.builder()
                .taskId(taskId)
                .status(status)
                .message(message)
                .filename(filename)
                .fileSize(fileSize)
                .contentKey(contentKey)
                .build();
    }
    
    public static BatchJobResponse running(String taskId, String message) {
        return BatchJobResponse.builder()
                .taskId(taskId)
                .status("RUNNING")
                .message(message)
                .build();
    }
    
    public static BatchJobResponse failed(String taskId, String error) {
        return BatchJobResponse.builder()
                .taskId(taskId)
                .status("FAILED")
                .error(error)
                .build();
    }
} 