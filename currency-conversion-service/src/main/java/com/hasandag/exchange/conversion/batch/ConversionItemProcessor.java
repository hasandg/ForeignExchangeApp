package com.hasandag.exchange.conversion.batch;

import com.hasandag.exchange.common.client.InternalExchangeRateClient;
import com.hasandag.exchange.common.dto.ConversionRequest;
import com.hasandag.exchange.common.dto.ConversionResponse;
import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.lang.NonNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@RequiredArgsConstructor
@Slf4j
public class ConversionItemProcessor implements ItemProcessor<ConversionRequest, ConversionResponse> {

    private final InternalExchangeRateClient exchangeRateClient;

    @Override
    public ConversionResponse process(@NonNull ConversionRequest request) throws Exception {
        try {
            ExchangeRateResponse rateResponse = exchangeRateClient.getExchangeRate(
                    request.getSourceCurrency(), 
                    request.getTargetCurrency()
            );

            BigDecimal targetAmount = request.getSourceAmount()
                    .multiply(rateResponse.getRate())
                    .setScale(2, RoundingMode.HALF_UP);

            return ConversionResponse.builder()
                    .transactionId(UUID.randomUUID().toString())
                    .sourceCurrency(request.getSourceCurrency())
                    .targetCurrency(request.getTargetCurrency())
                    .sourceAmount(request.getSourceAmount())
                    .targetAmount(targetAmount)
                    .exchangeRate(rateResponse.getRate())
                    .timestamp(LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            log.error("Conversion failed: {} {} to {} - {}", 
                    request.getSourceAmount(), request.getSourceCurrency(), 
                    request.getTargetCurrency(), e.getMessage());
            throw e;
        }
    }
} 