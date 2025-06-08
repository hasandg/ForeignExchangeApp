package com.hasandag.exchange.conversion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JobListResponse {
    private List<JobStatusResponse> jobs;
    private Integer totalJobs;
    private Integer currentPage;
    private Integer pageSize;
    private Integer totalPages;
    private String error;
} 