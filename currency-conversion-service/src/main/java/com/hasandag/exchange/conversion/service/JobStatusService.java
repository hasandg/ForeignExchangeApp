package com.hasandag.exchange.conversion.service;

import com.hasandag.exchange.conversion.dto.JobListResponse;
import com.hasandag.exchange.conversion.dto.JobStatisticsResponse;
import com.hasandag.exchange.conversion.dto.JobStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobStatusService {

    private final JobExplorer jobExplorer;

    public JobStatusResponse getJobStatus(Long jobId) {
        try {
            JobExecution jobExecution = jobExplorer.getJobExecution(jobId);
            
            if (jobExecution == null) {
                return JobStatusResponse.builder()
                    .error("Job not found")
                    .build();
            }
            
            return buildJobStatusResponse(jobExecution);
            
        } catch (Exception e) {
            log.error("Error retrieving job status for job ID: {}", jobId, e);
            return JobStatusResponse.builder()
                .error("Error retrieving job status: " + e.getMessage())
                .build();
        }
    }

    public JobListResponse getRunningJobs() {
        try {
            List<String> jobNames = jobExplorer.getJobNames();
            List<JobExecution> runningExecutions = new ArrayList<>();
            
            for (String jobName : jobNames) {
                Set<JobExecution> jobExecutions = jobExplorer.findRunningJobExecutions(jobName);
                runningExecutions.addAll(jobExecutions);
            }
            
            List<JobStatusResponse> runningJobs = runningExecutions.stream()
                    .map(this::buildJobStatusResponse)
                    .collect(Collectors.toList());
            
            return JobListResponse.builder()
                .jobs(runningJobs)
                .totalJobs(runningJobs.size())
                .build();
            
        } catch (Exception e) {
            log.error("Error retrieving running jobs", e);
            return JobListResponse.builder()
                .error("Error retrieving running jobs: " + e.getMessage())
                .build();
        }
    }

    public JobStatisticsResponse getJobStatistics() {
        try {
            List<String> jobNames = jobExplorer.getJobNames();
            Map<String, JobStatisticsResponse.JobTypeStatistics> statistics = new HashMap<>();
            
            for (String jobName : jobNames) {
                statistics.put(jobName, calculateJobStatisticsOptimized(jobName));
            }
            
            return JobStatisticsResponse.builder()
                .statistics(statistics)
                .totalJobTypes(jobNames.size())
                .build();
            
        } catch (Exception e) {
            log.error("Error retrieving job statistics", e);
            return JobStatisticsResponse.builder()
                .error("Error retrieving job statistics: " + e.getMessage())
                .build();
        }
    }

    public JobListResponse getJobsByName(String jobName, int page, int size) {
        try {
            int start = page * size;
            List<JobInstance> jobInstances = jobExplorer.getJobInstances(jobName, start, size);
            
            List<JobExecution> allExecutions = batchGetJobExecutions(jobInstances);
            
            List<JobStatusResponse> jobs = allExecutions.stream()
                    .map(this::buildJobStatusResponse)
                    .collect(Collectors.toList());
            
            int totalCount = (int) jobExplorer.getJobInstanceCount(jobName);
            
            return JobListResponse.builder()
                .jobs(jobs)
                .totalJobs(totalCount)
                .currentPage(page)
                .pageSize(size)
                .totalPages((int) Math.ceil((double) totalCount / size))
                .build();
            
        } catch (Exception e) {
            log.error("Error retrieving jobs by name: {}", jobName, e);
            return JobListResponse.builder()
                .error("Error retrieving jobs: " + e.getMessage())
                .build();
        }
    }

    public JobListResponse getAllJobs() {
        try {
            List<String> jobNames = jobExplorer.getJobNames();
            List<JobExecution> allExecutions = new ArrayList<>();
            
            for (String jobName : jobNames) {
                List<JobInstance> instances = jobExplorer.getJobInstances(jobName, 0, 50);
                List<JobExecution> executions = batchGetJobExecutions(instances);
                allExecutions.addAll(executions);
            }
            
            List<JobStatusResponse> jobs = allExecutions.stream()
                    .sorted((e1, e2) -> Long.compare(e2.getId(), e1.getId()))
                    .limit(50)
                    .map(this::buildJobStatusResponse)
                    .collect(Collectors.toList());
            
            return JobListResponse.builder()
                .jobs(jobs)
                .totalJobs(jobs.size())
                .build();
            
        } catch (Exception e) {
            log.error("Error retrieving job list", e);
            return JobListResponse.builder()
                .error("Error retrieving job list: " + e.getMessage())
                .build();
        }
    }

    private List<JobExecution> batchGetJobExecutions(List<JobInstance> jobInstances) {
        List<JobExecution> allExecutions = new ArrayList<>();
        
        for (JobInstance instance : jobInstances) {
            List<JobExecution> executions = jobExplorer.getJobExecutions(instance);
            allExecutions.addAll(executions);
        }
        
        return allExecutions;
    }

    private JobStatusResponse buildJobStatusResponse(JobExecution jobExecution) {
        JobInstance jobInstance = jobExecution.getJobInstance();
        
        return JobStatusResponse.builder()
            .jobId(jobExecution.getId())
            .jobInstanceId(jobInstance.getInstanceId())
            .jobName(jobInstance.getJobName())
            .status(jobExecution.getStatus().toString())
            .startTime(jobExecution.getStartTime())
            .endTime(jobExecution.getEndTime())
            .createTime(jobExecution.getCreateTime())
            .exitStatus(jobExecution.getExitStatus().getExitCode())
            .progress(buildProgressInfoOptimized(jobExecution))
            .parameters(buildParametersMap(jobExecution))
            .elapsedTime(formatElapsedTime(calculateElapsedTimeMs(jobExecution)))
            .elapsedTimeMs(calculateElapsedTimeMs(jobExecution))
            .build();
    }

    private JobStatusResponse.JobProgressInfo buildProgressInfoOptimized(JobExecution jobExecution) {
        Collection<StepExecution> stepExecutions = jobExecution.getStepExecutions();
        
        if (stepExecutions.isEmpty()) {
            return null;
        }
        
        StepExecution latestStep = stepExecutions.stream()
                .max(Comparator.comparing(StepExecution::getId))
                .orElse(null);
                
        if (latestStep == null) {
            return null;
        }
        
        return JobStatusResponse.JobProgressInfo.builder()
            .readCount((int) latestStep.getReadCount())
            .writeCount((int) latestStep.getWriteCount())
            .commitCount((int) latestStep.getCommitCount())
            .totalSkipCount((int) (latestStep.getReadSkipCount() + 
                           latestStep.getWriteSkipCount() + 
                           latestStep.getProcessSkipCount()))
            .readSkipCount((int) latestStep.getReadSkipCount())
            .writeSkipCount((int) latestStep.getWriteSkipCount())
            .processSkipCount((int) latestStep.getProcessSkipCount())
            .build();
    }

    private Map<String, Object> buildParametersMap(JobExecution jobExecution) {
        return jobExecution.getJobParameters().getParameters()
                .entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> entry.getValue().getValue()
                ));
    }

    private JobStatisticsResponse.JobTypeStatistics calculateJobStatisticsOptimized(String jobName) {
        try {
            List<JobInstance> instances = jobExplorer.getJobInstances(jobName, 0, 1000);
            List<JobExecution> executions = batchGetJobExecutions(instances);

            int totalJobs = executions.size();
            
            Map<String, Long> statusCounts = executions.stream()
                    .collect(Collectors.groupingBy(
                        exec -> exec.getStatus().toString(),
                        Collectors.counting()
                    ));
            
            int completedJobs = Math.toIntExact(statusCounts.getOrDefault("COMPLETED", 0L));
            int failedJobs = Math.toIntExact(statusCounts.getOrDefault("FAILED", 0L));
            int runningJobs = Math.toIntExact(statusCounts.getOrDefault("STARTED", 0L) + 
                              statusCounts.getOrDefault("STARTING", 0L));
            
            double successRate = totalJobs > 0 ? (double) completedJobs / totalJobs : 0.0;
            
            OptionalDouble avgExecutionTime = executions.stream()
                    .filter(exec -> exec.getStartTime() != null && exec.getEndTime() != null)
                    .mapToLong(this::calculateElapsedTimeMs)
                    .average();

            return JobStatisticsResponse.JobTypeStatistics.builder()
                .totalJobs(totalJobs)
                .completedJobs(completedJobs)
                .failedJobs(failedJobs)
                .runningJobs(runningJobs)
                .successRate(successRate)
                .averageExecutionTimeMs(avgExecutionTime.isPresent() ? (long) avgExecutionTime.getAsDouble() : 0L)
                .build();
        } catch (Exception e) {
            log.error("Error calculating statistics for job: {}", jobName, e);
            return JobStatisticsResponse.JobTypeStatistics.builder()
                .totalJobs(0)
                .build();
        }
    }

    private LocalDateTime convertToLocalDateTime(java.util.Date date) {
        return date != null ? 
            LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()) : null;
    }

    private long calculateElapsedTimeMs(JobExecution jobExecution) {
        if (jobExecution.getStartTime() != null && jobExecution.getEndTime() != null) {
            return java.time.Duration.between(jobExecution.getStartTime(), jobExecution.getEndTime()).toMillis();
        }
        return 0L;
    }

    private String formatElapsedTime(long totalMs) {
        if (totalMs <= 0) return "0ms";
        
        long seconds = totalMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }
} 