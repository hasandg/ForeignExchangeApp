package com.hasandag.exchange.conversion.batch;

import com.hasandag.exchange.common.dto.ConversionResponse;
import com.hasandag.exchange.conversion.model.CurrencyConversionEntity;
import com.hasandag.exchange.conversion.repository.query.CurrencyConversionPostgresRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.lang.NonNull;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class CurrencyConversionPostgresItemWriter implements ItemWriter<ConversionResponse> {

    private final CurrencyConversionPostgresRepository postgresRepository;

    @Override
    public void write(@NonNull Chunk<? extends ConversionResponse> chunk) throws Exception {
        log.warn("📊 POSTGRES WRITER - Received chunk with {} items", chunk.size());
        List<CurrencyConversionEntity> entities = new ArrayList<>();
        
        for (ConversionResponse response : chunk.getItems()) {
            if (response == null) {
                continue;
            }
            entities.add(mapToEntity(response));
        }

        if (!entities.isEmpty()) {
            try {
                postgresRepository.saveAll(entities);
                log.warn("✅ POSTGRES WRITER - Successfully saved {} items to PostgreSQL (chunk size: {})", entities.size(), chunk.size());
            } catch (Exception e) {
                log.error("❌ POSTGRES WRITER - Error batch saving to PostgreSQL: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to save batch to PostgreSQL", e);
            }
        }
    }

    private CurrencyConversionEntity mapToEntity(ConversionResponse response) {
        CurrencyConversionEntity entity = new CurrencyConversionEntity();
        entity.setTransactionId(response.getTransactionId());
        entity.setSourceCurrency(response.getSourceCurrency());
        entity.setTargetCurrency(response.getTargetCurrency());
        entity.setSourceAmount(response.getSourceAmount());
        entity.setTargetAmount(response.getTargetAmount());
        entity.setExchangeRate(response.getExchangeRate());
        entity.setTimestamp(response.getTimestamp());
        return entity;
    }
} 