package com.hasandag.exchange.rate.exception;

import com.hasandag.exchange.common.exception.GlobalExceptionHandler;
import com.hasandag.exchange.common.exception.RateServiceException;
import com.hasandag.exchange.common.exception.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @Mock
    private WebRequest mockWebRequest;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        exceptionHandler = new GlobalExceptionHandler();
        ReflectionTestUtils.setField(exceptionHandler, "serviceName", "Test Service");
        when(mockWebRequest.getDescription(false)).thenReturn("test-uri");
    }

    @Test
    void testRateServiceException() {
        RateServiceException exception = new RateServiceException("API error");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleRateServiceException(exception, mockWebRequest);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        ErrorResponse responseBody = response.getBody();
        assertNotNull(responseBody);
        assertEquals("API error", responseBody.getMessage());
        assertEquals("RATE_SERVICE_ERROR", responseBody.getCode());
        assertNotNull(responseBody.getTimestamp());
        assertEquals("test-uri", responseBody.getPath());
    }

    @Test
    void testValidationError() {
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("objectName", "field", "Invalid field");
        
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(Collections.singletonList(fieldError));

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleMethodArgumentNotValid(exception, mockWebRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ErrorResponse responseBody = response.getBody();
        assertNotNull(responseBody);
        assertEquals("Validation failed", responseBody.getMessage());
        assertEquals("VALIDATION_ERROR", responseBody.getCode());
        assertNotNull(responseBody.getTimestamp());
        assertEquals("test-uri", responseBody.getPath());
        assertNotNull(responseBody.getValidationErrors());
        assertEquals(1, responseBody.getValidationErrors().size());
    }

    @Test
    void testUnexpectedError() {
        Exception exception = new RuntimeException("Unexpected error");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGlobalException(exception, mockWebRequest);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ErrorResponse responseBody = response.getBody();
        assertNotNull(responseBody);
        assertEquals("Internal server error", responseBody.getMessage());
        assertEquals("INTERNAL_SERVER_ERROR", responseBody.getCode());
        assertNotNull(responseBody.getTimestamp());
        assertEquals("test-uri", responseBody.getPath());
        assertEquals("Unexpected error", responseBody.getDetails());
    }
} 