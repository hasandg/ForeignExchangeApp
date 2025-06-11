package com.hasandag.exchange.conversion.service.impl;

import com.hasandag.exchange.common.dto.ConversionRequest;
import com.hasandag.exchange.common.dto.ConversionResponse;
import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import com.hasandag.exchange.common.dto.cqrs.ConversionEvent;
import com.hasandag.exchange.conversion.kafka.producer.ConversionEventProducer;
import com.hasandag.exchange.conversion.model.CurrencyConversionDocument;
import com.hasandag.exchange.conversion.repository.command.CurrencyConversionMongoRepository;
import com.hasandag.exchange.conversion.service.ConversionCommandService;
import com.hasandag.exchange.conversion.service.ExchangeRateProvider;
import lombok.extern.slf4j.Slf4j;
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
    private final CurrencyConversionMongoRepository mongoRepository;

    public ConversionCommandServiceImpl(
            ExchangeRateProvider exchangeRateProvider,
            ConversionEventProducer eventProducer,
            CurrencyConversionMongoRepository mongoRepository) {
        this.exchangeRateProvider = exchangeRateProvider;
        this.eventProducer = eventProducer;
        this.mongoRepository = mongoRepository;
    }

    @Override
    @Transactional
    public ConversionResponse processConversionWithEvents(ConversionRequest request) {
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

        try {
            CurrencyConversionDocument document = CurrencyConversionDocument.builder()
                    .transactionId(transactionId)
                    .sourceCurrency(request.getSourceCurrency().getCode())
                    .targetCurrency(request.getTargetCurrency().getCode())
                    .sourceAmount(request.getSourceAmount())
                    .targetAmount(targetAmount)
                    .exchangeRate(exchangeRate)
                    .timestamp(timestamp)
                    .status("COMPLETED")
                    .build();

            return mongoRepository.save(document);
            
        }catch (Exception e) {
            log.error("Failed to save to MongoDB: {}", e.getMessage());
            throw new RuntimeException("Failed to persist conversion", e);
        }
    }

    private void publishConversionEvent(CurrencyConversionDocument document) {
        if (eventProducer != null) {
            ConversionEvent event = ConversionEvent.builder()
                    .transactionId(document.getTransactionId())
                    .sourceCurrency(document.getSourceCurrencyEnum())
                    .targetCurrency(document.getTargetCurrencyEnum())
                    .sourceAmount(document.getSourceAmount())
                    .targetAmount(document.getTargetAmount())
                    .exchangeRate(document.getExchangeRate())
                    .timestamp(document.getTimestamp())
                    .eventType(ConversionEvent.EventType.CONVERSION_CREATED)
                    .build();

            eventProducer.sendConversionEvent(event);
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