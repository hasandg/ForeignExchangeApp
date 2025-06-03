package com.hasandag.exchange.conversion.kafka.producer;

import com.hasandag.exchange.common.constants.KafkaConstants;
import com.hasandag.exchange.common.dto.cqrs.ConversionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
        } catch (Exception e) {
            log.error("Error sending conversion event", e);
        }
    }
} 