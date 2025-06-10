package com.hasandag.exchange.conversion.batch;

import com.hasandag.exchange.common.client.InternalExchangeRateClient;
import com.hasandag.exchange.common.dto.ConversionRequest;
import com.hasandag.exchange.common.dto.ConversionResponse;
import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import com.hasandag.exchange.conversion.repository.command.CurrencyConversionMongoRepository;
import com.hasandag.exchange.conversion.repository.query.CurrencyConversionPostgresRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.lang.NonNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class ConversionItemProcessor implements ItemProcessor<ConversionRequest, ConversionResponse> {

    private final InternalExchangeRateClient exchangeRateClient;

    @Override
    public ConversionResponse process(@NonNull ConversionRequest request) throws Exception {
        log.debug("Processing conversion: {} {} -> {}", 
                request.getSourceAmount(), request.getSourceCurrency(), request.getTargetCurrency());

        try {
            ExchangeRateResponse exchangeRate = exchangeRateClient.getExchangeRate(
                    request.getSourceCurrency(), 
                    request.getTargetCurrency()
            );

            BigDecimal targetAmount = request.getSourceAmount()
                    .multiply(exchangeRate.getRate())
                    .setScale(2, RoundingMode.HALF_UP);

            return ConversionResponse.builder()
                    .transactionId(UUID.randomUUID().toString())
                    .sourceAmount(request.getSourceAmount())
                    .sourceCurrency(request.getSourceCurrency())
                    .targetAmount(targetAmount)
                    .targetCurrency(request.getTargetCurrency())
                    .exchangeRate(exchangeRate.getRate())
                    .timestamp(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to process conversion for {} {} to {}: {}", 
                    request.getSourceAmount(), request.getSourceCurrency(), request.getTargetCurrency(), e.getMessage());
            throw e;
        }
    }
} 