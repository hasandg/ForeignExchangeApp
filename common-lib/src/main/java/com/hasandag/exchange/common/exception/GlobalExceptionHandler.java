package com.hasandag.exchange.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @Value("${spring.application.name:Unknown Service}")
    private String serviceName;

    

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, WebRequest request) {
        log.warn("Business exception in {}: {} - {}", serviceName, ex.getErrorCode(), ex.getMessage());
        
        ErrorResponse response = ErrorResponse.of(
                ex.getErrorCode(),
                ex.getMessage(),
                ex.getDetails() != null ? ex.getDetails().toString() : null,
                getPath(request)
        );
        
        
        
        return new ResponseEntity<>(response, ex.getHttpStatus());
    }

    @ExceptionHandler(BatchJobException.class)
    public ResponseEntity<ErrorResponse> handleBatchJobException(BatchJobException ex, WebRequest request) {
        log.warn("Batch job exception in {}: {} - {}", serviceName, ex.getErrorCode(), ex.getMessage());
        
        ErrorResponse response = ErrorResponse.of(
                ex.getErrorCode(),
                ex.getMessage(),
                null,
                getPath(request)
        );
        
        return new ResponseEntity<>(response, ex.getHttpStatus());
    }

    @ExceptionHandler(RateServiceException.class)
    public ResponseEntity<ErrorResponse> handleRateServiceException(RateServiceException ex, WebRequest request) {
        log.error("RateServiceException in {}: {}", serviceName, ex.getMessage(), ex);
        
        ErrorResponse response = ErrorResponse.of(
                "RATE_SERVICE_ERROR",
                ex.getMessage(),
                ex.getCause() != null ? ex.getCause().getMessage() : null,
                getPath(request)
        );
        
        return new ResponseEntity<>(response, HttpStatus.SERVICE_UNAVAILABLE);
    }

    

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, WebRequest request) {
        log.warn("Validation error in {}: {}", serviceName, ex.getMessage());
        
        List<String> errors = ex.getBindingResult()
                                .getFieldErrors()
                                .stream()
                                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                                .collect(Collectors.toList());
        
        ErrorResponse response = ErrorResponse.validation("Validation failed", errors);
        response.setPath(getPath(request));
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNoSuchElementException(NoSuchElementException ex, WebRequest request) {
        log.error("Resource not found in {}: {}", serviceName, ex.getMessage());
        
        ErrorResponse response = ErrorResponse.of(
                "RESOURCE_NOT_FOUND",
                ex.getMessage(),
                null,
                getPath(request)
        );
        
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex, WebRequest request) {
        log.warn("IllegalArgumentException in {}: {}", serviceName, ex.getMessage());
        
        ErrorResponse response = ErrorResponse.of(
                "INVALID_ARGUMENT",
                ex.getMessage(),
                null,
                getPath(request)
        );
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxSizeException(MaxUploadSizeExceededException ex, WebRequest request) {
        log.warn("File size exceeded in {}: {}", serviceName, ex.getMessage());
        
        String message = "Uploaded file is too large. Maximum size allowed is " + ex.getMaxUploadSize() + " bytes.";
        
        ErrorResponse response = ErrorResponse.of(
                "FILE_SIZE_EXCEEDED",
                message,
                ex.getMessage(),
                getPath(request)
        );
        
        return new ResponseEntity<>(response, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex, WebRequest request) {
        log.error("Unexpected error in {}", serviceName, ex);
        
        ErrorResponse response = ErrorResponse.of(
                "INTERNAL_SERVER_ERROR",
                "Internal server error",
                ex.getMessage(),
                getPath(request)
        );
        
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    

    private String getPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
} 