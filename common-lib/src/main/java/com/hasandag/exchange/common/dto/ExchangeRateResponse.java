package com.hasandag.exchange.common.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
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
public class ExchangeRateResponse {
    
    private Currency sourceCurrency;
    private Currency targetCurrency;
    private BigDecimal rate;
    
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime lastUpdated;
} 