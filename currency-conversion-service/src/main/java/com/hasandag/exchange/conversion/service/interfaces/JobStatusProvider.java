package com.hasandag.exchange.conversion.service.interfaces;

import java.util.Map;

public interface JobStatusProvider {
    
    Map<String, Object> getJobStatus(Long jobId);
    
    Map<String, Object> getRunningJobs();
    
    Map<String, Object> getJobStatistics();
} 