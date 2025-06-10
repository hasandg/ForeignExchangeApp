package com.hasandag.exchange.common.dto;

import com.hasandag.exchange.common.enums.Currency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversionResponse {
    
    private String transactionId;
    private Currency sourceCurrency;
    private Currency targetCurrency;
    private BigDecimal sourceAmount;
    private BigDecimal targetAmount;
    private BigDecimal exchangeRate;
    private LocalDateTime timestamp;
} 