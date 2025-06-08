package com.hasandag.exchange.conversion.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JobStatusResponse {
    private Long jobId;
    private Long jobInstanceId;
    private String jobName;
    private String status;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    
    private String exitStatus;
    private JobProgressInfo progress;
    private Map<String, Object> parameters;
    private String elapsedTime;
    private Long elapsedTimeMs;
    private String error;
    
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class JobProgressInfo {
        private Integer readCount;
        private Integer writeCount;
        private Integer commitCount;
        private Integer totalSkipCount;
        private Integer readSkipCount;
        private Integer writeSkipCount;
        private Integer processSkipCount;
    }
} 