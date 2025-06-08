package com.hasandag.exchange.conversion.service;

public interface JobOperationService {
    
    boolean isJobRunning(String jobName);
    
    void stopJob(String jobName);
} 