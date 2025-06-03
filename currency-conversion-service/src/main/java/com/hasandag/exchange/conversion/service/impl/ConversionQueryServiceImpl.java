package com.hasandag.exchange.conversion.service.impl;

import com.hasandag.exchange.conversion.model.CurrencyConversionEntity;
import com.hasandag.exchange.conversion.repository.query.CurrencyConversionPostgresRepository;
import com.hasandag.exchange.conversion.service.ConversionQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@Slf4j
public class ConversionQueryServiceImpl implements ConversionQueryService {

    private final CurrencyConversionPostgresRepository repository;

    public ConversionQueryServiceImpl(CurrencyConversionPostgresRepository repository) {
        this.repository = repository;
    }

    @Override
    public Page<CurrencyConversionEntity> findConversions(String transactionId, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        log.debug("Finding conversions - Transaction ID: {}, StartDate: {}, EndDate: {}", transactionId, startDate, endDate);

        if (transactionId != null) {
            log.debug("Querying by transaction ID: {}", transactionId);
            Optional<CurrencyConversionEntity> entityOptional = repository.findByTransactionId(transactionId);
            if (entityOptional.isPresent()) {
                return new PageImpl<>(Collections.singletonList(entityOptional.get()), pageable, 1);
            }
            throw new NoSuchElementException("Conversion not found for transaction ID: " + transactionId);
        }
        
        if (startDate != null && endDate != null) {
            log.debug("Querying by date-time range: Start: {}, End: {}", startDate, endDate);
            if (startDate.isAfter(endDate)) {
                log.warn("Start date-time {} is after end date-time {}. Returning empty page.", startDate, endDate);
                return getEmptyConversionsPage(pageable);
            }
            
            return repository.findByTimestampBetweenOrderByTimestampDesc(startDate, endDate, pageable);
        }

        if (startDate != null) {
            log.debug("Querying from start date-time: {}", startDate);
            return repository.findByTimestampGreaterThanEqualOrderByTimestampDesc(startDate, pageable);
        }

        if (endDate != null) {
            log.debug("Querying up to end date-time: {}", endDate);
            return repository.findByTimestampLessThanEqualOrderByTimestampDesc(endDate, pageable);
        }

        log.warn("No valid query parameters provided (transactionId or date range).");
        throw new IllegalArgumentException("Query parameters invalid: Provide a transactionId or a valid date range (startDate and/or endDate).");
    }

    private Page<CurrencyConversionEntity> getEmptyConversionsPage(Pageable pageable) {
        log.debug("Returning empty conversions page");
        return new PageImpl<>(Collections.emptyList(), pageable, 0);
    }
} 