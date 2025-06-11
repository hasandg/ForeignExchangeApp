package com.hasandag.exchange.conversion.repository.specification;

import com.hasandag.exchange.conversion.model.CurrencyConversionEntity;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ConversionSpecifications {

    public static Specification<CurrencyConversionEntity> hasTransactionId(String transactionId) {
        return (root, query, cb) -> transactionId == null ? 
            cb.conjunction() : cb.equal(root.get("transactionId"), transactionId);
    }

    public static Specification<CurrencyConversionEntity> hasSourceCurrency(String sourceCurrency) {
        return (root, query, cb) -> sourceCurrency == null ? 
            cb.conjunction() : cb.equal(root.get("sourceCurrency"), sourceCurrency);
    }

    public static Specification<CurrencyConversionEntity> hasTargetCurrency(String targetCurrency) {
        return (root, query, cb) -> targetCurrency == null ? 
            cb.conjunction() : cb.equal(root.get("targetCurrency"), targetCurrency);
    }

    public static Specification<CurrencyConversionEntity> hasSourceAmountBetween(BigDecimal minAmount, BigDecimal maxAmount) {
        return (root, query, cb) -> {
            if (minAmount == null && maxAmount == null) return cb.conjunction();
            if (minAmount != null && maxAmount != null) 
                return cb.between(root.get("sourceAmount"), minAmount, maxAmount);
            if (minAmount != null) 
                return cb.greaterThanOrEqualTo(root.get("sourceAmount"), minAmount);
            return cb.lessThanOrEqualTo(root.get("sourceAmount"), maxAmount);
        };
    }

    public static Specification<CurrencyConversionEntity> hasTimestampBetween(LocalDateTime startDate, LocalDateTime endDate) {
        return (root, query, cb) -> {
            if (startDate == null && endDate == null) return cb.conjunction();
            if (startDate != null && endDate != null) 
                return cb.between(root.get("timestamp"), startDate, endDate);
            if (startDate != null) 
                return cb.greaterThanOrEqualTo(root.get("timestamp"), startDate);
            return cb.lessThanOrEqualTo(root.get("timestamp"), endDate);
        };
    }

    public static Specification<CurrencyConversionEntity> hasExchangeRateBetween(BigDecimal minRate, BigDecimal maxRate) {
        return (root, query, cb) -> {
            if (minRate == null && maxRate == null) return cb.conjunction();
            if (minRate != null && maxRate != null) 
                return cb.between(root.get("exchangeRate"), minRate, maxRate);
            if (minRate != null) 
                return cb.greaterThanOrEqualTo(root.get("exchangeRate"), minRate);
            return cb.lessThanOrEqualTo(root.get("exchangeRate"), maxRate);
        };
    }
} 