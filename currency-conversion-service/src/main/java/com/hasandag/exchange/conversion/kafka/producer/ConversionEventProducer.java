package com.hasandag.exchange.conversion.kafka.producer;

import com.hasandag.exchange.common.constants.KafkaConstants;
import com.hasandag.exchange.common.dto.cqrs.ConversionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConversionEventProducer {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    public void sendConversionEvent(ConversionEvent event) {
        log.debug("Sending conversion event: {}", event.getTransactionId());
        try {
            kafkaTemplate.send(KafkaConstants.CONVERSION_EVENT_TOPIC, event.getEventId(), event);
            log.debug("Successfully sent conversion event: {}", event.getTransactionId());
        } catch (Exception e) {
            log.error("Failed to send conversion event for transaction: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to publish conversion event for transaction: " + event.getTransactionId(), e);
        }
    }
} 