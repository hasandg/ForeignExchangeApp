package com.hasandag.exchange.conversion.service;

import com.hasandag.exchange.common.dto.ConversionRequest;
import org.springframework.stereotype.Service;

@Service
public class ConversionValidationService {

    public void validateConversionRequest(ConversionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Conversion request cannot be null");
        }
        
        if (request.getSourceCurrency() != null && request.getTargetCurrency() != null 
            && request.getSourceCurrency().equals(request.getTargetCurrency())) {
            throw new IllegalArgumentException("Source and target currencies cannot be the same");
        }
    }

} 