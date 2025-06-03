package com.hasandag.exchange.conversion.service.interfaces;

import java.util.Map;

/**
 * Interface for job status operations
 * Following Interface Segregation Principle
 */
public interface JobStatusProvider {
    
    /**
     * Get status of a specific job
     * @param jobId the job execution ID
     * @return job status information
     */
    Map<String, Object> getJobStatus(Long jobId);
    
    /**
     * Get all currently running jobs
     * @return running jobs information
     */
    Map<String, Object> getRunningJobs();
    
    /**
     * Get job statistics across all job types
     * @return comprehensive job statistics
     */
    Map<String, Object> getJobStatistics();
} 