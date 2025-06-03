package com.hasandag.exchange.conversion.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.web.multipart.MultipartFile;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchJobRequest {
    
    private MultipartFile file;
    private JobLauncher jobLauncher;
    private Job job;
    private String operationType;
    
    public boolean isAsync() {
        return "async".equalsIgnoreCase(operationType);
    }
} 