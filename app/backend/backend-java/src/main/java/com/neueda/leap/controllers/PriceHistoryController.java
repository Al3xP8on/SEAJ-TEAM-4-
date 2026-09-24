package com.neueda.leap.controllers;

import com.neueda.leap.models.PriceHistory;
import com.neueda.leap.services.PriceHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/v1/price-history")
public class PriceHistoryController {
    
    private static final Logger logger = LoggerFactory.getLogger(PriceHistoryController.class);
    
    @Autowired
    private PriceHistoryService priceHistoryService;
    
    @GetMapping("/{symbol}")
    public ResponseEntity<List<PriceHistory>> getPriceHistory(@PathVariable String symbol) {
        logger.info("GET /v1/price-history/{} - Fetching price history", symbol);
        try {
            List<PriceHistory> history = priceHistoryService.getPriceHistoryBySymbol(symbol);
            logger.info("Found {} price history records for symbol {}", history.size(), symbol);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            logger.error("Error fetching price history for symbol {}: {}", symbol, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/{symbol}/range")
    public ResponseEntity<List<PriceHistory>> getPriceHistoryByDateRange(
            @PathVariable String symbol,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        logger.info("GET /v1/price-history/{}/range - Fetching price history from {} to {}", symbol, from, to);
        try {
            if (from == null || to == null) {
                logger.warn("Missing required date parameters for symbol {}", symbol);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            
            List<PriceHistory> history = priceHistoryService.getPriceHistoryBySymbolAndDateRange(symbol, from, to);
            logger.info("Found {} price history records for symbol {} in date range", history.size(), symbol);
            return ResponseEntity.ok(history);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid date range for symbol {}: {}", symbol, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Error fetching price history for symbol {}: {}", symbol, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/{symbol}/latest")
    public ResponseEntity<PriceHistory> getLatestPriceHistory(@PathVariable String symbol) {
        logger.info("GET /v1/price-history/{}/latest - Fetching latest price history", symbol);
        try {
            Optional<PriceHistory> latestPrice = priceHistoryService.getLatestPriceHistory(symbol);
            if (latestPrice.isPresent()) {
                logger.info("Found latest price history for symbol {}", symbol);
                return ResponseEntity.ok(latestPrice.get());
            }
            logger.warn("No price history found for symbol {}", symbol);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            logger.error("Error fetching latest price history for symbol {}: {}", symbol, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping
    public ResponseEntity<PriceHistory> savePriceHistory(@RequestBody PriceHistory priceHistory) {
        logger.info("POST /v1/price-history - Creating price history for symbol {}", priceHistory.getSymbol());
        try {
            if (priceHistory == null || priceHistory.getSymbol() == null || priceHistory.getSymbol().isEmpty()) {
                logger.warn("Invalid price history data provided");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            
            PriceHistory savedPriceHistory = priceHistoryService.savePriceHistory(priceHistory);
            logger.info("Price history saved successfully for symbol: {}", priceHistory.getSymbol());
            return ResponseEntity.status(HttpStatus.CREATED).body(savedPriceHistory);
        } catch (Exception e) {
            logger.error("Error saving price history: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
