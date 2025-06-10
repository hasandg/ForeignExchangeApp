package com.hasandag.exchange.conversion.model;

import com.hasandag.exchange.common.enums.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "currency_conversions", indexes = {
    @Index(name = "idx_transaction_id", columnList = "transactionId", unique = true)
})
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrencyConversionEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String transactionId;
    
    @Column(nullable = false)
    private String sourceCurrency;
    
    @Column(nullable = false)
    private String targetCurrency;
    
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal sourceAmount;
    
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal targetAmount;
    
    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal exchangeRate;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    // Currency enum convenience methods
    public Currency getSourceCurrencyEnum() {
        return sourceCurrency != null ? Currency.fromCode(sourceCurrency) : null;
    }
    
    public void setSourceCurrencyEnum(Currency currency) {
        this.sourceCurrency = currency != null ? currency.getCode() : null;
    }
    
    public Currency getTargetCurrencyEnum() {
        return targetCurrency != null ? Currency.fromCode(targetCurrency) : null;
    }
    
    public void setTargetCurrencyEnum(Currency currency) {
        this.targetCurrency = currency != null ? currency.getCode() : null;
    }
    
    @PrePersist
    protected void onCreate() {
        if (transactionId == null) {
            transactionId = UUID.randomUUID().toString();
        }
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CurrencyConversionEntity that = (CurrencyConversionEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}