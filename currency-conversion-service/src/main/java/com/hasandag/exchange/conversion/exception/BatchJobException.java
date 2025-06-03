package com.hasandag.exchange.conversion.exception;

import org.springframework.http.HttpStatus;

public class BatchJobException extends BusinessException {
    
    public static final String JOB_NOT_FOUND = "JOB_NOT_FOUND";
    public static final String JOB_ALREADY_RUNNING = "JOB_ALREADY_RUNNING";
    public static final String INVALID_FILE_TYPE = "INVALID_FILE_TYPE";
    public static final String EMPTY_FILE = "EMPTY_FILE";
    public static final String JOB_EXECUTION_FAILED = "JOB_EXECUTION_FAILED";
    
    public BatchJobException(String errorCode, String message, HttpStatus httpStatus) {
        super(errorCode, message, httpStatus);
    }
    
    public BatchJobException(String errorCode, String message, HttpStatus httpStatus, Throwable cause) {
        super(errorCode, message, httpStatus, cause);
    }
    
    // Factory methods for common scenarios
    public static BatchJobException jobNotFound(Long jobId) {
        return new BatchJobException(
            JOB_NOT_FOUND, 
            "Job with ID " + jobId + " not found", 
            HttpStatus.NOT_FOUND
        );
    }
    
    public static BatchJobException jobAlreadyRunning() {
        return new BatchJobException(
            JOB_ALREADY_RUNNING,
            "A bulk conversion job is already running. Please wait for it to complete.",
            HttpStatus.CONFLICT
        );
    }
    
    public static BatchJobException invalidFileType() {
        return new BatchJobException(
            INVALID_FILE_TYPE,
            "Invalid file type. Please upload a CSV file.",
            HttpStatus.BAD_REQUEST
        );
    }
    
    public static BatchJobException emptyFile() {
        return new BatchJobException(
            EMPTY_FILE,
            "File is empty",
            HttpStatus.BAD_REQUEST
        );
    }
    
    public static BatchJobException executionFailed(String message, Throwable cause) {
        return new BatchJobException(
            JOB_EXECUTION_FAILED,
            "Job execution failed: " + message,
            HttpStatus.INTERNAL_SERVER_ERROR,
            cause
        );
    }
} 