package com.hasandag.exchange.conversion.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ConversionSearchRequest {
    
    private String transactionId;
    private String sourceCurrency;
    private String targetCurrency;
    private BigDecimal minSourceAmount;
    private BigDecimal maxSourceAmount;
    private BigDecimal minExchangeRate;
    private BigDecimal maxExchangeRate;
    
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startDate;
    
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endDate;
} 