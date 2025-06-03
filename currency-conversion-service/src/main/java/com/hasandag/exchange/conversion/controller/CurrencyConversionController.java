package com.hasandag.exchange.conversion.controller;

import com.hasandag.exchange.common.dto.ConversionRequest;
import com.hasandag.exchange.common.dto.ConversionResponse;
import com.hasandag.exchange.conversion.model.CurrencyConversionEntity;
import com.hasandag.exchange.conversion.service.ConversionCommandService;
import com.hasandag.exchange.conversion.service.ConversionQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/conversions")
public class CurrencyConversionController {

    private final ConversionCommandService commandService;
    private final ConversionQueryService queryService;

    public CurrencyConversionController(ConversionCommandService commandService, 
                                        ConversionQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping
    public ResponseEntity<ConversionResponse> convertCurrency(@RequestBody @Valid ConversionRequest request) {
        ConversionResponse response = commandService.processConversionWithEvents(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history")
    @Operation(summary = "Get conversion history with date-time precision")
    public ResponseEntity<Page<CurrencyConversionEntity>> getConversionHistory(
            @Parameter(description = "Transaction ID")
            @RequestParam(required = false) String transactionId,
            @Parameter(description = "Start date-time (ISO format)", example = "2025-05-29T10:30:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date-time (ISO format)", example = "2025-05-29T18:45:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Pageable pageable) {
        
        Page<CurrencyConversionEntity> history = queryService.findConversions(
                transactionId, startDate, endDate, pageable);

        return ResponseEntity.ok(history);
    }
    
} 