package com.hasandag.exchange.conversion.service;

import com.hasandag.exchange.common.dto.ConversionRequest;
import com.hasandag.exchange.common.dto.ConversionResponse;

public interface ConversionCommandService {

    ConversionResponse processConversionWithEvents(ConversionRequest request);
} 