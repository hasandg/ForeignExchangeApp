package com.hasandag.exchange.conversion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JobStatisticsResponse {
    private Map<String, JobTypeStatistics> statistics;
    private Integer totalJobTypes;
    private String error;
    
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class JobTypeStatistics {
        private Integer totalJobs;
        private Integer completedJobs;
        private Integer failedJobs;
        private Integer runningJobs;
        private Double successRate;
        private Long averageExecutionTimeMs;
    }
} 