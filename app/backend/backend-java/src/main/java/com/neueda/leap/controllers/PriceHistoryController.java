package com.neueda.leap.controllers;

import jakarta.validation.Valid;
import com.neueda.leap.dtos.PriceHistoryResponse;
import com.neueda.leap.dtos.PriceHistoryRequest;
import com.neueda.leap.utils.LogMaskingUtil;

import com.neueda.leap.models.PriceHistory;
import com.neueda.leap.services.PriceHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping(value = "/v1/price-history", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Price history", description = "Historical prices per instrument")
public class PriceHistoryController {
    
    private static final Logger logger = LoggerFactory.getLogger(PriceHistoryController.class);
    
    @Autowired
    private PriceHistoryService priceHistoryService;
    
    @GetMapping("/{symbol}")
    public ResponseEntity<List<PriceHistoryResponse>> getPriceHistory(@PathVariable String symbol) {
        logger.info("GET /v1/price-history/{} - Fetching price history", LogMaskingUtil.sanitize(symbol));
        try {
            List<PriceHistory> history = priceHistoryService.getPriceHistoryBySymbol(symbol);
            logger.info("Found {} price history records for symbol {}", history.size(), LogMaskingUtil.sanitize(symbol));
            return ResponseEntity.ok(history.stream().map(PriceHistoryResponse::from).toList());
        } catch (Exception e) {
            logger.error("Error fetching price history for symbol {}: {}", LogMaskingUtil.sanitize(symbol), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/{symbol}/range")
    public ResponseEntity<List<PriceHistoryResponse>> getPriceHistoryByDateRange(
            @PathVariable String symbol,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        logger.info("GET /v1/price-history/{}/range - Fetching price history from {} to {}", LogMaskingUtil.sanitize(symbol), LogMaskingUtil.sanitize(from), LogMaskingUtil.sanitize(to));
        try {
            if (from == null || to == null) {
                logger.warn("Missing required date parameters for symbol {}", LogMaskingUtil.sanitize(symbol));
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            
            List<PriceHistory> history = priceHistoryService.getPriceHistoryBySymbolAndDateRange(symbol, from, to);
            logger.info("Found {} price history records for symbol {} in date range", history.size(), LogMaskingUtil.sanitize(symbol));
            return ResponseEntity.ok(history.stream().map(PriceHistoryResponse::from).toList());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid date range for symbol {}: {}", LogMaskingUtil.sanitize(symbol), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Error fetching price history for symbol {}: {}", LogMaskingUtil.sanitize(symbol), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/{symbol}/latest")
    public ResponseEntity<PriceHistoryResponse> getLatestPriceHistory(@PathVariable String symbol) {
        logger.info("GET /v1/price-history/{}/latest - Fetching latest price history", LogMaskingUtil.sanitize(symbol));
        try {
            Optional<PriceHistory> latestPrice = priceHistoryService.getLatestPriceHistory(symbol);
            if (latestPrice.isPresent()) {
                logger.info("Found latest price history for symbol {}", LogMaskingUtil.sanitize(symbol));
                return ResponseEntity.ok(PriceHistoryResponse.from(latestPrice.get()));
            }
            logger.warn("No price history found for symbol {}", LogMaskingUtil.sanitize(symbol));
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            logger.error("Error fetching latest price history for symbol {}: {}", LogMaskingUtil.sanitize(symbol), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping
    public ResponseEntity<PriceHistoryResponse> savePriceHistory(@Valid @RequestBody PriceHistoryRequest request) {
        logger.info("POST /v1/price-history - Creating price history for symbol {}", LogMaskingUtil.sanitize(request.symbol()));
        try {
            PriceHistory savedPriceHistory = priceHistoryService.savePriceHistory(request.toEntity());
            logger.info("Price history saved successfully for symbol: {}", LogMaskingUtil.sanitize(request.symbol()));
            return ResponseEntity.status(HttpStatus.CREATED).body(PriceHistoryResponse.from(savedPriceHistory));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid price history data: {}", LogMaskingUtil.sanitize(e.getMessage()));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Error saving price history: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
