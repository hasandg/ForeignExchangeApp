package com.hasandag.exchange.conversion.batch;

import com.hasandag.exchange.common.dto.ConversionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.CompositeItemWriter;
import org.springframework.lang.NonNull;

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
        log.debug("Writing chunk of {} conversion responses", chunk.size());
        compositeWriter.write(chunk);
        log.debug("Successfully completed writing chunk of {} items", chunk.size());
    }
} 