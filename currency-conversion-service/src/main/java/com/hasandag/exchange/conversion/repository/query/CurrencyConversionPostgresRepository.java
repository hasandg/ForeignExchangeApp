package com.hasandag.exchange.conversion.repository.query;

import com.hasandag.exchange.conversion.model.CurrencyConversionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface CurrencyConversionPostgresRepository extends JpaRepository<CurrencyConversionEntity, Long> {
    
    Optional<CurrencyConversionEntity> findByTransactionId(String transactionId);
    
    boolean existsByTransactionId(String transactionId);
    
    Page<CurrencyConversionEntity> findByTimestampBetweenOrderByTimestampDesc(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
    
    Page<CurrencyConversionEntity> findByTimestampGreaterThanEqualOrderByTimestampDesc(LocalDateTime startDate, Pageable pageable);
    
    Page<CurrencyConversionEntity> findByTimestampLessThanEqualOrderByTimestampDesc(LocalDateTime endDate, Pageable pageable);
} 