package com.hasandag.exchange.conversion.batch;

import com.hasandag.exchange.common.dto.ConversionRequest;
import com.hasandag.exchange.common.dto.ConversionResponse;
import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import com.hasandag.exchange.conversion.client.ExchangeRateFeignClient;
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

    private final ExchangeRateFeignClient exchangeRateFeignClient;
    private final CurrencyConversionPostgresRepository postgresRepository; 
    private final CurrencyConversionMongoRepository mongoRepository;

    @Override
    public ConversionResponse process(@NonNull ConversionRequest request) throws Exception {
        String transactionId = "BATCH-" + UUID.randomUUID();
        log.debug("Processing request for transaction ID {}: {}", transactionId, request);

        try {
            log.info("Fetching exchange rate for {} to {}", request.getSourceCurrency(), request.getTargetCurrency());
            ExchangeRateResponse rateResponse = exchangeRateFeignClient.getExchangeRate(
                    request.getSourceCurrency(),
                    request.getTargetCurrency());

            if (rateResponse == null || rateResponse.getRate() == null) {
                log.error("Failed to fetch exchange rate for {} -> {}. Rate response was null or rate was null.", 
                    request.getSourceCurrency(), request.getTargetCurrency());
                throw new RuntimeException("Exchange rate not found or service unavailable.");
            }
            
            log.info("Successfully fetched exchange rate: {}", rateResponse.getRate());

            BigDecimal targetAmount = request.getSourceAmount()
                    .multiply(rateResponse.getRate())
                    .setScale(2, RoundingMode.HALF_UP);

            ConversionResponse response = ConversionResponse.builder()
                    .transactionId(transactionId)
                    .sourceCurrency(request.getSourceCurrency())
                    .targetCurrency(request.getTargetCurrency())
                    .sourceAmount(request.getSourceAmount())
                    .targetAmount(targetAmount)
                    .exchangeRate(rateResponse.getRate())
                    .timestamp(LocalDateTime.now())
                    .build();
            
            log.info("Processed conversion for transaction ID {}: {} {} -> {} {} (Rate: {})", 
                transactionId, request.getSourceAmount(), request.getSourceCurrency(), 
                targetAmount, request.getTargetCurrency(), rateResponse.getRate());
            return response;

        } catch (Exception e) {
            log.error("Error during conversion processing for request {}: {}. Transaction ID: {}", 
                request, e.getMessage(), transactionId, e);
            throw e; 
        }
    }
} 