package com.hasandag.exchange.conversion.batch;

import com.hasandag.exchange.common.dto.ConversionResponse;
import com.hasandag.exchange.conversion.model.CurrencyConversionDocument;
import com.hasandag.exchange.conversion.repository.command.CurrencyConversionMongoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.lang.NonNull;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class CurrencyConversionMongoItemWriter implements ItemWriter<ConversionResponse> {

    private final CurrencyConversionMongoRepository mongoRepository;

    @Override
    public void write(@NonNull Chunk<? extends ConversionResponse> chunk) throws Exception {
        log.debug("Writing {} items to MongoDB", chunk.size());
        
        List<CurrencyConversionDocument> documents = new ArrayList<>();
        
        for (ConversionResponse response : chunk.getItems()) {
            if (response == null) {
                continue;
            }
            
            try {
                CurrencyConversionDocument mongoDocument = mapToMongoDocument(response);
                documents.add(mongoDocument);
                log.debug("Prepared MongoDB document: {}", response.getTransactionId());
            } catch (Exception e) {
                log.error("Error preparing MongoDB document for transaction ID {}: {}", response.getTransactionId(), e.getMessage(), e);
                throw e;
            }
        }
        
        if (!documents.isEmpty()) {
            try {
                mongoRepository.saveAll(documents);
                log.debug("Successfully saved {} items to MongoDB", documents.size());
            } catch (Exception e) {
                log.error("Error saving to MongoDB: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to batch save to MongoDB", e);
            }
        }
    }

    private CurrencyConversionDocument mapToMongoDocument(ConversionResponse response) {
        return CurrencyConversionDocument.builder()
                .transactionId(response.getTransactionId())
                .sourceCurrency(response.getSourceCurrency().getCode())
                .targetCurrency(response.getTargetCurrency().getCode())
                .sourceAmount(response.getSourceAmount())
                .targetAmount(response.getTargetAmount())
                .exchangeRate(response.getExchangeRate())
                .timestamp(response.getTimestamp())
                .build();
    }
} 