package com.hasandag.exchange.conversion.service.impl;

import com.hasandag.exchange.common.dto.ConversionRequest;
import com.hasandag.exchange.common.dto.ConversionResponse;
import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import com.hasandag.exchange.common.dto.cqrs.ConversionEvent;
import com.hasandag.exchange.conversion.kafka.producer.ConversionEventProducer;
import com.hasandag.exchange.conversion.model.CurrencyConversionDocument;
import com.hasandag.exchange.conversion.repository.command.CurrencyConversionMongoRepository;
import com.hasandag.exchange.conversion.service.ConversionCommandService;
import com.hasandag.exchange.conversion.service.ConversionValidationService;
import com.hasandag.exchange.conversion.service.ExchangeRateProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
public class ConversionCommandServiceImpl implements ConversionCommandService {

    private final ExchangeRateProvider exchangeRateProvider;
    private final ConversionEventProducer eventProducer;
    private final ConversionValidationService validationService;
    private final CurrencyConversionMongoRepository mongoRepository;

    public ConversionCommandServiceImpl(
            ExchangeRateProvider exchangeRateProvider,
            @Autowired(required = false) ConversionEventProducer eventProducer,
            ConversionValidationService validationService,
            @Autowired(required = false) CurrencyConversionMongoRepository mongoRepository) {
        this.exchangeRateProvider = exchangeRateProvider;
        this.eventProducer = eventProducer;
        this.validationService = validationService;
        this.mongoRepository = mongoRepository;
    }

    @Override
    @Transactional
    public ConversionResponse processConversionWithEvents(ConversionRequest request) {
        log.info("Processing conversion with events: {} {} to {}", 
                request.getSourceAmount(), request.getSourceCurrency(), request.getTargetCurrency());
        
        validationService.validateConversionRequest(request);
        
        ExchangeRateResponse rateResponse = exchangeRateProvider.getExchangeRate(
                request.getSourceCurrency(),
                request.getTargetCurrency());

        BigDecimal targetAmount = request.getSourceAmount()
                .multiply(rateResponse.getRate())
                .setScale(2, RoundingMode.HALF_UP);
        
        String transactionId = UUID.randomUUID().toString();
        LocalDateTime timestamp = LocalDateTime.now();

        CurrencyConversionDocument savedDocument = saveToWriteModel(request, targetAmount, rateResponse.getRate(), transactionId, timestamp);
        
        publishConversionEvent(savedDocument);

        return buildResponse(request, targetAmount, rateResponse.getRate(), transactionId, timestamp);
    }

    private CurrencyConversionDocument saveToWriteModel(ConversionRequest request, BigDecimal targetAmount, 
                                                       BigDecimal exchangeRate, String transactionId, LocalDateTime timestamp) {
        if (mongoRepository == null) {
            log.error("MongoDB repository not available - Write Model is required for CQRS");
            throw new RuntimeException("Write Model (MongoDB) is unavailable - cannot process conversion");
        }
        
        try {
            
            CurrencyConversionDocument document = CurrencyConversionDocument.builder()
                    .transactionId(transactionId)
                    .sourceCurrency(request.getSourceCurrency())
                    .targetCurrency(request.getTargetCurrency())
                    .sourceAmount(request.getSourceAmount())
                    .targetAmount(targetAmount)
                    .exchangeRate(exchangeRate)
                    .timestamp(timestamp)
                    .status("COMPLETED")
                    .build();

            CurrencyConversionDocument saved = mongoRepository.save(document);
            log.debug("Saved conversion to Write Model (MongoDB): {}", transactionId);
            return saved;
            
        } catch (DuplicateKeyException e) {
            log.debug("Duplicate detected in MongoDB (handled gracefully): {}", transactionId);
            return mongoRepository.findByTransactionId(transactionId).orElseThrow();
        } catch (Exception e) {
            log.error("Failed to save to Write Model (MongoDB): {} - {}", transactionId, e.getMessage());
            throw new RuntimeException("Failed to persist conversion to Write Model", e);
        }
    }

    private void publishConversionEvent(CurrencyConversionDocument document) {
        if (eventProducer != null) {
            ConversionEvent event = ConversionEvent.builder()
                    .transactionId(document.getTransactionId())
                    .sourceCurrency(document.getSourceCurrency())
                    .targetCurrency(document.getTargetCurrency())
                    .sourceAmount(document.getSourceAmount())
                    .targetAmount(document.getTargetAmount())
                    .exchangeRate(document.getExchangeRate())
                    .timestamp(document.getTimestamp())
                    .eventType(ConversionEvent.EventType.CONVERSION_CREATED)
                    .build();

            eventProducer.sendConversionEvent(event);
            log.debug("Published conversion event: {}", document.getTransactionId());
        } else {
            log.warn("Event producer not available - CQRS event not published");
        }
    }

    private ConversionResponse buildResponse(ConversionRequest request, BigDecimal targetAmount, 
                                           BigDecimal exchangeRate, String transactionId, LocalDateTime timestamp) {
        return ConversionResponse.builder()
                .transactionId(transactionId)
                .sourceCurrency(request.getSourceCurrency())
                .targetCurrency(request.getTargetCurrency())
                .sourceAmount(request.getSourceAmount())
                .targetAmount(targetAmount)
                .exchangeRate(exchangeRate)
                .timestamp(timestamp)
                .build();
    }
} 