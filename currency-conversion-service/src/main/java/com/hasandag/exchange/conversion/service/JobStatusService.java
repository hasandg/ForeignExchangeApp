package com.hasandag.exchange.conversion.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobStatusService {

    private final JobExplorer jobExplorer;

    @Cacheable(value = "job-status", key = "#jobId")
    public Map<String, Object> getJobStatus(Long jobId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            JobExecution jobExecution = jobExplorer.getJobExecution(jobId);
            
            if (jobExecution == null) {
                response.put("error", "Job not found");
                return response;
            }
            
            return buildJobStatusResponse(jobExecution);
            
        } catch (Exception e) {
            log.error("Error retrieving job status for job ID: {}", jobId, e);
            response.put("error", "Error retrieving job status: " + e.getMessage());
            return response;
        }
    }

    public Map<String, Object> getRunningJobs() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<String> jobNames = jobExplorer.getJobNames();
            List<Map<String, Object>> runningJobs = jobNames.stream()
                    .flatMap(jobName -> jobExplorer.findRunningJobExecutions(jobName).stream())
                    .map(this::createJobInfoMap)
                    .collect(Collectors.toList());
            
            response.put("runningJobs", runningJobs);
            response.put("count", runningJobs.size());
            
            return response;
            
        } catch (Exception e) {
            log.error("Error retrieving running jobs", e);
            response.put("error", "Error retrieving running jobs: " + e.getMessage());
            return response;
        }
    }

    public Map<String, Object> getJobStatistics() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<String> jobNames = jobExplorer.getJobNames();
            Map<String, Object> statistics = new HashMap<>();
            
            for (String jobName : jobNames) {
                statistics.put(jobName, calculateJobStatistics(jobName));
            }
            
            response.put("statistics", statistics);
            response.put("totalJobTypes", jobNames.size());
            
            return response;
            
        } catch (Exception e) {
            log.error("Error retrieving job statistics", e);
            response.put("error", "Error retrieving job statistics: " + e.getMessage());
            return response;
        }
    }

    public Map<String, Object> getJobsByName(String jobName, int page, int size) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            int start = page * size;
            List<JobInstance> jobInstances = jobExplorer.getJobInstances(jobName, start, size);
            
            List<Map<String, Object>> jobs = jobInstances.stream()
                    .flatMap(instance -> jobExplorer.getJobExecutions(instance).stream())
                    .map(this::createJobInfoMap)
                    .collect(Collectors.toList());
            
            int totalCount = (int) jobExplorer.getJobInstanceCount(jobName);
            
            response.put("jobs", jobs);
            response.put("totalJobs", totalCount);
            response.put("currentPage", page);
            response.put("pageSize", size);
            response.put("totalPages", (int) Math.ceil((double) totalCount / size));
            
            return response;
            
        } catch (Exception e) {
            log.error("Error retrieving jobs by name: {}", jobName, e);
            response.put("error", "Error retrieving jobs: " + e.getMessage());
            return response;
        }
    }

    public Map<String, Object> getAllJobs() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<String> jobNames = jobExplorer.getJobNames();
            
            List<Map<String, Object>> allJobs = jobNames.stream()
                    .flatMap(jobName -> jobExplorer.getJobInstances(jobName, 0, 50).stream())
                    .flatMap(instance -> jobExplorer.getJobExecutions(instance).stream())
                    .sorted((e1, e2) -> Long.compare(e2.getId(), e1.getId()))
                    .limit(50)
                    .map(this::createJobInfoMap)
                    .collect(Collectors.toList());
            
            response.put("jobs", allJobs);
            response.put("totalJobs", allJobs.size());
            
            return response;
            
        } catch (Exception e) {
            log.error("Error retrieving job list", e);
            response.put("error", "Error retrieving job list: " + e.getMessage());
            return response;
        }
    }

    private Map<String, Object> buildJobStatusResponse(JobExecution jobExecution) {
        Map<String, Object> response = new HashMap<>();
        
        response.put("jobId", jobExecution.getId());
        response.put("jobInstanceId", jobExecution.getJobInstance().getInstanceId());
        response.put("jobName", jobExecution.getJobInstance().getJobName());
        response.put("status", jobExecution.getStatus().toString());
        response.put("startTime", jobExecution.getStartTime());
        response.put("endTime", jobExecution.getEndTime());
        response.put("exitStatus", jobExecution.getExitStatus().getExitCode());
        response.put("progress", buildProgressInfo(jobExecution));
        
        response.putAll(calculateElapsedTime(jobExecution));
        
        return response;
    }

    private Map<String, Object> createJobInfoMap(JobExecution jobExecution) {
        Map<String, Object> jobInfo = new HashMap<>();
        JobInstance jobInstance = jobExecution.getJobInstance();
        
        jobInfo.put("job_execution_id", jobExecution.getId());
        jobInfo.put("job_instance_id", jobInstance.getInstanceId());
        jobInfo.put("job_name", jobInstance.getJobName());
        jobInfo.put("status", jobExecution.getStatus().toString());
        jobInfo.put("start_time", jobExecution.getStartTime());
        jobInfo.put("end_time", jobExecution.getEndTime());
        jobInfo.put("create_time", jobExecution.getCreateTime());
        jobInfo.put("exit_code", jobExecution.getExitStatus().getExitCode());
        jobInfo.put("parameters", buildParametersMap(jobExecution));
        jobInfo.put("progress", buildProgressInfo(jobExecution));
        
        jobInfo.putAll(calculateElapsedTime(jobExecution));
        
        return jobInfo;
    }

    private Map<String, Object> buildProgressInfo(JobExecution jobExecution) {
        Map<String, Object> progress = new HashMap<>();
        
        for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
            progress.put("readCount", stepExecution.getReadCount());
            progress.put("writeCount", stepExecution.getWriteCount());
            progress.put("commitCount", stepExecution.getCommitCount());
            progress.put("totalSkipCount", 
                stepExecution.getReadSkipCount() + 
                stepExecution.getWriteSkipCount() + 
                stepExecution.getProcessSkipCount());
            progress.put("readSkipCount", stepExecution.getReadSkipCount());
            progress.put("writeSkipCount", stepExecution.getWriteSkipCount());
            progress.put("processSkipCount", stepExecution.getProcessSkipCount());
            break; // Take first step execution
        }
        
        return progress;
    }

    private Map<String, Object> buildParametersMap(JobExecution jobExecution) {
        Map<String, Object> parameters = new HashMap<>();
        
        if (jobExecution.getJobParameters() != null) {
            jobExecution.getJobParameters().getParameters().forEach((key, value) -> {
                if (!"file.content".equals(key)) {
                    parameters.put(key, value.getValue());
                }
            });
        }
        
        return parameters;
    }

    private Map<String, Object> calculateJobStatistics(String jobName) {
        Map<String, Object> jobStats = new HashMap<>();

        int totalInstances = 0;
        try {
            totalInstances = (int) jobExplorer.getJobInstanceCount(jobName);
        } catch (NoSuchJobException e) {
            throw new RuntimeException(e);
        }
        jobStats.put("totalInstances", totalInstances);
        
        Set<JobExecution> runningExecutions = jobExplorer.findRunningJobExecutions(jobName);
        jobStats.put("runningCount", runningExecutions.size());
        
        List<JobInstance> recentInstances = jobExplorer.getJobInstances(jobName, 0, 100);
        
        long completedCount = recentInstances.stream()
                .flatMap(instance -> jobExplorer.getJobExecutions(instance).stream())
                .filter(execution -> execution.getStatus() == org.springframework.batch.core.BatchStatus.COMPLETED)
                .count();
                
        long failedCount = recentInstances.stream()
                .flatMap(instance -> jobExplorer.getJobExecutions(instance).stream())
                .filter(execution -> execution.getStatus() == org.springframework.batch.core.BatchStatus.FAILED)
                .count();
        
        jobStats.put("completedCount", completedCount);
        jobStats.put("failedCount", failedCount);
        
        return jobStats;
    }

    private Map<String, Object> calculateElapsedTime(JobExecution jobExecution) {
        Map<String, Object> timeInfo = new HashMap<>();
        
        LocalDateTime startTime = jobExecution.getStartTime();
        LocalDateTime endTime = jobExecution.getEndTime();
        
        if (startTime != null) {
            if (endTime != null) {
                // Job is completed - calculate total elapsed time
                java.time.Duration duration = java.time.Duration.between(startTime, endTime);
                long elapsedSeconds = duration.getSeconds();
                long elapsedMillis = duration.toMillis();
                timeInfo.put("elapsedTimeSeconds", elapsedSeconds);
                timeInfo.put("elapsedTimeMillis", elapsedMillis);
                timeInfo.put("elapsedTimeFormatted", formatElapsedTime(elapsedSeconds));
                timeInfo.put("isRunning", false);
            } else {
                // Job is still running - calculate elapsed time from start to now
                java.time.Duration duration = java.time.Duration.between(startTime, LocalDateTime.now());
                long elapsedSeconds = duration.getSeconds();
                long elapsedMillis = duration.toMillis();
                timeInfo.put("elapsedTimeSeconds", elapsedSeconds);
                timeInfo.put("elapsedTimeMillis", elapsedMillis);
                timeInfo.put("elapsedTimeFormatted", formatElapsedTime(elapsedSeconds));
                timeInfo.put("isRunning", true);
            }
        } else {
            // Job hasn't started yet
            timeInfo.put("elapsedTimeSeconds", 0);
            timeInfo.put("elapsedTimeMillis", 0L);
            timeInfo.put("elapsedTimeFormatted", "00:00:00");
            timeInfo.put("isRunning", false);
        }
        
        return timeInfo;
    }
    
    private String formatElapsedTime(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
} 