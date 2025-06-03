package com.hasandag.exchange.common.dto.cqrs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversionEvent {
    @Builder.Default
    private String eventId = UUID.randomUUID().toString();
    private String commandId;
    private String transactionId;
    private String sourceCurrency;
    private String targetCurrency;
    private BigDecimal sourceAmount;
    private BigDecimal targetAmount;
    private BigDecimal exchangeRate;
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    private EventType eventType;
    private String userId;
    private String correlationId;
    private String errorMessage;
    
    public enum EventType {
        CONVERSION_CREATED,
        CONVERSION_FAILED
    }
} 