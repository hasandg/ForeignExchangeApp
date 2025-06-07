package com.hasandag.exchange.conversion.service;

import com.hasandag.exchange.common.exception.BatchJobException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobStatusServiceAdapter {
    
    private final JobStatusService jobStatusService;
    
    public Map<String, Object> getJobStatus(Long jobId) {
        Map<String, Object> result = jobStatusService.getJobStatus(jobId);
        return handleJobStatusResponse(result, jobId);
    }
    
    public Map<String, Object> getAllJobs() {
        Map<String, Object> result = jobStatusService.getAllJobs();
        return handleResponse(result, "retrieving all jobs");
    }
    
    public Map<String, Object> getRunningJobs() {
        Map<String, Object> result = jobStatusService.getRunningJobs();
        return handleResponse(result, "retrieving running jobs");
    }
    
    public Map<String, Object> getJobStatistics() {
        Map<String, Object> result = jobStatusService.getJobStatistics();
        return handleResponse(result, "retrieving job statistics");
    }
    
    public Map<String, Object> getJobsByName(String jobName, int page, int size) {
        Map<String, Object> result = jobStatusService.getJobsByName(jobName, page, size);
        return handleResponse(result, "retrieving jobs by name: " + jobName);
    }
    
    private Map<String, Object> handleJobStatusResponse(Map<String, Object> result, Long jobId) {
        if (result.containsKey("error")) {
            String error = (String) result.get("error");
            if ("Job not found".equals(error)) {
                throw BatchJobException.jobNotFound(jobId);
            }
            throw BatchJobException.executionFailed(error, null);
        }
        return result;
    }
    
    private Map<String, Object> handleResponse(Map<String, Object> result, String operation) {
        if (result.containsKey("error")) {
            String error = (String) result.get("error");
            log.error("Error in {}: {}", operation, error);
            throw BatchJobException.executionFailed(error, null);
        }
        return result;
    }
} 