package com.hasandag.exchange.conversion.batch;

import com.hasandag.exchange.common.dto.ConversionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.CompositeItemWriter;
import org.springframework.lang.NonNull;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ConversionItemWriter implements ItemWriter<ConversionResponse> {

    private final CompositeItemWriter<ConversionResponse> compositeWriter;

    public ConversionItemWriter(List<ItemWriter<? super ConversionResponse>> delegates) {
        this.compositeWriter = new CompositeItemWriter<>();
        this.compositeWriter.setDelegates(delegates);
        try {
            this.compositeWriter.afterPropertiesSet();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize composite writer", e);
        }
    }

    @Override
    public void write(@NonNull Chunk<? extends ConversionResponse> chunk) throws Exception {
        log.warn("🔴 CHUNK PROCESSING - Writing a chunk of {} conversion responses", chunk.size());
        log.warn("🔴 CHUNK PROCESSING - This proves chunking is working! Chunk size: {}", chunk.size());
        log.warn("🔴 CHUNK PROCESSING - Items in this chunk: {}", chunk.getItems().size());
        compositeWriter.write(chunk);
        log.warn("🔴 CHUNK PROCESSING - Successfully completed writing chunk of {} items", chunk.size());
    }
} 