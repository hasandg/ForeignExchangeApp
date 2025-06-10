package com.hasandag.exchange.common.dto;

import com.hasandag.exchange.common.enums.Currency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversionRequest {
    
    @NotNull(message = "Source amount is required")
    @DecimalMin(value = "0.01", message = "Source amount must be greater than zero")
    private BigDecimal sourceAmount;
    
    @NotNull(message = "Source currency is required")
    private Currency sourceCurrency;
    
    @NotNull(message = "Target currency is required")
    private Currency targetCurrency;
} 