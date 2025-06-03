package com.hasandag.exchange.conversion.kafka.consumer;

import com.hasandag.exchange.common.constants.KafkaConstants;
import com.hasandag.exchange.common.dto.cqrs.ConversionEvent;
import com.hasandag.exchange.conversion.model.CurrencyConversionEntity;
import com.hasandag.exchange.conversion.repository.query.CurrencyConversionPostgresRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConversionEventConsumer {

    private final CurrencyConversionPostgresRepository postgresConversionRepository;

    @KafkaListener(
        topics = KafkaConstants.CONVERSION_EVENT_TOPIC,
        groupId = KafkaConstants.EVENT_HANDLER_GROUP,
        containerFactory = "eventListenerContainerFactory"
    )
    @Transactional
    public void consumeConversionEvent(ConversionEvent event) {
        log.info("Received ConversionEvent: {}, Type: {}", 
                event.getTransactionId(), event.getEventType());
        
        if (event.getEventType() == ConversionEvent.EventType.CONVERSION_CREATED) {
            
            CurrencyConversionEntity conversionEntity = CurrencyConversionEntity.builder()
                    .transactionId(event.getTransactionId())
                    .sourceCurrency(event.getSourceCurrency())
                    .targetCurrency(event.getTargetCurrency())
                    .sourceAmount(event.getSourceAmount())
                    .targetAmount(event.getTargetAmount())
                    .exchangeRate(event.getExchangeRate())
                    .timestamp(event.getTimestamp())
                    .build();
            
            try {
                if (!postgresConversionRepository.existsByTransactionId(event.getTransactionId())) {
                    postgresConversionRepository.save(conversionEntity);
                    log.debug("Saved conversion to read model: {}", event.getTransactionId());
                } else {
                    log.debug("Duplicate conversion skipped: {}", event.getTransactionId());
                }
            } catch (Exception e) {
                log.error("Error saving conversion to read model: {}", event.getTransactionId(), e);
            }
        } else {
            log.warn("Ignoring event type: {} for: {}", 
                    event.getEventType(), event.getTransactionId());
        }
    }
} 