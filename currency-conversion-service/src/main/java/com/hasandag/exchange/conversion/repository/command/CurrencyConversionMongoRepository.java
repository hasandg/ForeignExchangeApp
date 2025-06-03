package com.hasandag.exchange.conversion.repository.command;

import com.hasandag.exchange.conversion.model.CurrencyConversionDocument;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "spring.data.mongodb.enabled", havingValue = "true", matchIfMissing = true)
public interface CurrencyConversionMongoRepository extends MongoRepository<CurrencyConversionDocument, String> {
    
    Optional<CurrencyConversionDocument> findByTransactionId(String transactionId);

}