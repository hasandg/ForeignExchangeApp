package com.hasandag.exchange.conversion.service;

import com.hasandag.exchange.conversion.dto.JobStatusResponse;

/**
 * Interface for job control operations following Interface Segregation Principle.
 * Separates job control concerns from batch processing concerns.
 */
public interface JobOperationService {
    
    /**
     * Restart a failed or stopped job
     */
    JobStatusResponse restartJob(Long jobId);
    
    /**
     * Stop a running job
     */
    JobStatusResponse stopJob(Long jobId);
} 