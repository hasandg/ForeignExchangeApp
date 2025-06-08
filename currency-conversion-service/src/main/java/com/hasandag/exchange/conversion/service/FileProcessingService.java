package com.hasandag.exchange.conversion.service;

import com.hasandag.exchange.conversion.dto.JobStatusResponse;
import org.springframework.batch.core.JobParameters;

/**
 * Interface for file processing operations following Interface Segregation Principle.
 * Handles different file format processing separately.
 */
public interface FileProcessingService {
    
    /**
     * Process CSV file content
     */
    JobStatusResponse processCsvFile(byte[] fileContent, JobParameters jobParameters);
    
    /**
     * Process XML file content  
     */
    JobStatusResponse processXmlFile(byte[] fileContent, JobParameters jobParameters);
    
    /**
     * Process JSON file content
     */
    JobStatusResponse processJsonFile(byte[] fileContent, JobParameters jobParameters);
    
    /**
     * Get processed file data for download
     */
    byte[] getProcessedFileData(Long jobId);
} 