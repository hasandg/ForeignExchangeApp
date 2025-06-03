package com.hasandag.exchange.conversion.batch;

import com.hasandag.exchange.common.dto.ConversionRequest;
import com.hasandag.exchange.conversion.service.OptimizedFileContentStoreService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemStream;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

@Slf4j
public class CsvConversionItemReader implements ItemReader<ConversionRequest>, ItemStream {

    private static final String FILE_CONTENT_KEY = "file.content.key";
    private static final String ORIGINAL_FILENAME_KEY = "original.filename";
    private static final String CURRENT_ITEM_COUNT_KEY = "csv.reader.current.item.count";

    @Autowired
    private OptimizedFileContentStoreService fileContentStoreService;

    private String fileContent;
    private String originalFilename;
    private String contentKey;
    private CSVParser csvParser;
    private Iterator<CSVRecord> recordIterator;
    private int currentItemCount = 0;

    @BeforeStep
    public void beforeStep(StepExecution stepExecution) {
        this.contentKey = stepExecution.getJobParameters().getString(FILE_CONTENT_KEY);
        this.originalFilename = stepExecution.getJobParameters().getString(ORIGINAL_FILENAME_KEY);
        
        if (contentKey != null) {
            this.fileContent = fileContentStoreService.getContent(contentKey);
            if (fileContent == null) {
                log.error("File content not found in store for key: {}", contentKey);
                log.error("Available store stats: {}", fileContentStoreService.getStoreStats());
            }
        }
        
        log.info("CsvConversionItemReader initialized for file: {}. Content key: {}, Content length: {}",
                originalFilename, contentKey, (fileContent != null ? fileContent.length() : 0));
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        if (fileContent == null || fileContent.trim().isEmpty()) {
            log.warn("File content is null or empty for file: {}", originalFilename);
            recordIterator = java.util.Collections.emptyIterator();
            return;
        }

        if (executionContext.containsKey(CURRENT_ITEM_COUNT_KEY)) {
            currentItemCount = executionContext.getInt(CURRENT_ITEM_COUNT_KEY);
            log.info("Restarting CSV reader for file {} from item count: {}", originalFilename, currentItemCount);
        } else {
            currentItemCount = 0;
            log.info("Opening CSV reader for file {} from the beginning.", originalFilename);
        }

        try {
            log.debug("CSV content preview (first 200 chars) for {}: {}",
                    originalFilename, fileContent.substring(0, Math.min(200, fileContent.length())));

            CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreHeaderCase(true)
                    .setTrim(true)
                    .setAllowMissingColumnNames(true)
                    .setIgnoreEmptyLines(true)
                    .build();

            StringReader stringReader = new StringReader(fileContent);
            csvParser = new CSVParser(stringReader, csvFormat);
            recordIterator = csvParser.iterator();

            if (recordIterator.hasNext() && currentItemCount == 0) {
                log.info("Detected CSV headers for {}: {}", originalFilename, csvParser.getHeaderNames());
            }

            for (int i = 0; i < currentItemCount && recordIterator.hasNext(); i++) {
                recordIterator.next();
            }

            if (currentItemCount > 0) {
                log.info("Skipped {} records to resume from previous position for file {}", currentItemCount, originalFilename);
            }

        } catch (IOException e) {
            log.error("Error opening CSV parser for file {}: {}", originalFilename, e.getMessage(), e);
            throw new ItemStreamException("Error opening CSV resources for " + originalFilename, e);
        }
    }

    @Override
    public ConversionRequest read() throws Exception {
        if (recordIterator == null || !recordIterator.hasNext()) {
            log.info("CSV reading completed for file {}. Total items processed: {}", originalFilename, currentItemCount);
            return null;
        }

        CSVRecord record = null;
        try {
            record = recordIterator.next();
            currentItemCount++;

            Map<String, String> recordMap = record.toMap();
            log.debug("Processing CSV record #{} in file {}: {}", currentItemCount, originalFilename, recordMap);

            String sourceAmountStr = getValueByPossibleNames(recordMap, "sourceAmount", "source_amount", "amount", "sourceamount");
            String sourceCurrency = getValueByPossibleNames(recordMap, "sourceCurrency", "source_currency", "from", "sourcecurrency");
            String targetCurrency = getValueByPossibleNames(recordMap, "targetCurrency", "target_currency", "to", "targetcurrency");

            if (sourceAmountStr == null || sourceAmountStr.trim().isEmpty()) {
                log.error("Missing sourceAmount in record #{} of file {}: {}", currentItemCount, originalFilename, recordMap);
                throw new IllegalArgumentException("Missing required field 'sourceAmount' in record " + currentItemCount);
            }
            if (sourceCurrency == null || sourceCurrency.trim().isEmpty()) {
                log.error("Missing sourceCurrency in record #{} of file {}: {}", currentItemCount, originalFilename, recordMap);
                throw new IllegalArgumentException("Missing required field 'sourceCurrency' in record " + currentItemCount);
            }
            if (targetCurrency == null || targetCurrency.trim().isEmpty()) {
                log.error("Missing targetCurrency in record #{} of file {}: {}", currentItemCount, originalFilename, recordMap);
                throw new IllegalArgumentException("Missing required field 'targetCurrency' in record " + currentItemCount);
            }

            BigDecimal sourceAmount;
            try {
                sourceAmount = new BigDecimal(sourceAmountStr.trim());
                if (sourceAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Source amount must be positive: " + sourceAmount);
                }
            } catch (NumberFormatException e) {
                log.error("Invalid sourceAmount '{}' in record #{} of file {}: {}", sourceAmountStr, currentItemCount, originalFilename, e.getMessage());
                throw new IllegalArgumentException("Invalid sourceAmount '" + sourceAmountStr + "' in record " + currentItemCount + ": " + e.getMessage());
            }

            ConversionRequest request = ConversionRequest.builder()
                    .sourceAmount(sourceAmount)
                    .sourceCurrency(sourceCurrency.trim().toUpperCase())
                    .targetCurrency(targetCurrency.trim().toUpperCase())
                    .build();

            log.debug("✅ Successfully parsed record #{} in file {}: {} {} -> {}", 
                    currentItemCount, originalFilename, sourceAmount, sourceCurrency, targetCurrency);
            
            return request;

        } catch (IllegalArgumentException e) {
            log.error("Validation error in record #{} of file {}: {}", currentItemCount, originalFilename, e.getMessage());
            throw e;
        } catch (NoSuchElementException e) {
            log.error("Record structure error in record #{} of file {}: {}", currentItemCount, originalFilename, e.getMessage());
            throw new IllegalArgumentException("Record structure error in record " + currentItemCount + ": " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error processing CSV record #{} in file {}: {}. Record: {}",
                    currentItemCount, originalFilename, e.getMessage(), record != null ? record.toList() : "null", e);
            throw new RuntimeException("Unexpected error processing record: " + e.getMessage(), e);
        }
    }

    private String getValueByPossibleNames(Map<String, String> recordMap, String... possibleNames) {
        for (String name : possibleNames) {
            if (recordMap.containsKey(name)) {
                String value = recordMap.get(name);
                if (value != null && !value.trim().isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        executionContext.putInt(CURRENT_ITEM_COUNT_KEY, currentItemCount);
        log.debug("Saving CsvConversionItemReader state for {}: currentItemCount={}", originalFilename, currentItemCount);
    }

    @Override
    public void close() throws ItemStreamException {
        try {
            if (csvParser != null) {
                csvParser.close();
            }
            
            // Clean up file content from optimized centralized store
            if (contentKey != null) {
                boolean removed = fileContentStoreService.removeContent(contentKey);
                if (removed) {
                    log.info("Successfully cleaned up file content from store for key: {}", contentKey);
                } else {
                    log.warn("Failed to cleanup content for key: {} (may have been already removed)", contentKey);
                }
            }
            
            log.info("CsvConversionItemReader closed for file {}. Processed {} items.", originalFilename, currentItemCount);
        } catch (IOException e) {
            log.error("Error closing CSV parser for file {}: {}", originalFilename, e.getMessage(), e);
            throw new ItemStreamException("Error closing CSV resources for " + originalFilename, e);
        }
    }
} 