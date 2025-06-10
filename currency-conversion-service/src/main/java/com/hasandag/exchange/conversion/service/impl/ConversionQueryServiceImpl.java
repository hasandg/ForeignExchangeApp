package com.hasandag.exchange.conversion.service.impl;

import com.hasandag.exchange.conversion.dto.ConversionSearchRequest;
import com.hasandag.exchange.conversion.model.CurrencyConversionEntity;
import com.hasandag.exchange.conversion.repository.query.CurrencyConversionPostgresRepository;
import com.hasandag.exchange.conversion.repository.specification.ConversionSpecifications;
import com.hasandag.exchange.conversion.service.ConversionQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
public class ConversionQueryServiceImpl implements ConversionQueryService {

    private final CurrencyConversionPostgresRepository repository;

    public ConversionQueryServiceImpl(CurrencyConversionPostgresRepository repository) {
        this.repository = repository;
    }

    @Override
    public Page<CurrencyConversionEntity> findConversions(String transactionId, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        Specification<CurrencyConversionEntity> spec = Specification
                .where(ConversionSpecifications.hasTransactionId(transactionId))
                .and(ConversionSpecifications.hasTimestampBetween(startDate, endDate));

        return repository.findAll(spec, pageable);
    }

    @Override
    public Page<CurrencyConversionEntity> findConversions(ConversionSearchRequest searchRequest, Pageable pageable) {
        Specification<CurrencyConversionEntity> spec = Specification
                .where(ConversionSpecifications.hasTransactionId(searchRequest.getTransactionId()))
                .and(ConversionSpecifications.hasSourceCurrency(searchRequest.getSourceCurrency()))
                .and(ConversionSpecifications.hasTargetCurrency(searchRequest.getTargetCurrency()))
                .and(ConversionSpecifications.hasSourceAmountBetween(searchRequest.getMinSourceAmount(), searchRequest.getMaxSourceAmount()))
                .and(ConversionSpecifications.hasExchangeRateBetween(searchRequest.getMinExchangeRate(), searchRequest.getMaxExchangeRate()))
                .and(ConversionSpecifications.hasTimestampBetween(searchRequest.getStartDate(), searchRequest.getEndDate()));

        return repository.findAll(spec, pageable);
    }

    private Page<CurrencyConversionEntity> getEmptyConversionsPage(Pageable pageable) {
        return Page.empty(pageable);
    }
} 