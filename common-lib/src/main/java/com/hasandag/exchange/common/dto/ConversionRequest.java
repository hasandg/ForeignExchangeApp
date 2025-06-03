package com.hasandag.exchange.common.dto;

import com.hasandag.exchange.common.validation.CurrencyCode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
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
    
    @NotBlank(message = "Source currency code is required")
    @CurrencyCode
    private String sourceCurrency;
    
    @NotBlank(message = "Target currency code is required")
    @CurrencyCode
    private String targetCurrency;
} 