package com.hasandag.exchange.conversion.repository;

import com.hasandag.exchange.conversion.model.CurrencyConversionEntity;
import com.hasandag.exchange.conversion.repository.query.CurrencyConversionPostgresRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.liquibase.enabled=false"
})
class CurrencyConversionRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CurrencyConversionPostgresRepository repository;

    @Test
    void testSaveAndFindByTransactionId() {
        CurrencyConversionEntity conversion = CurrencyConversionEntity.builder()
                .transactionId("test-tx-123")
                .sourceCurrency("USD")
                .targetCurrency("EUR")
                .sourceAmount(BigDecimal.valueOf(100))
                .targetAmount(BigDecimal.valueOf(85))
                .exchangeRate(BigDecimal.valueOf(0.85))
                .timestamp(LocalDateTime.now())
                .build();

        repository.save(conversion);
        var result = repository.findByTransactionId("test-tx-123");

        assertThat(result).isPresent();
        assertThat(result.get().getTransactionId()).isEqualTo("test-tx-123");
        assertThat(result.get().getSourceCurrency()).isEqualTo("USD");
        assertThat(result.get().getTargetCurrency()).isEqualTo("EUR");
    }
} 