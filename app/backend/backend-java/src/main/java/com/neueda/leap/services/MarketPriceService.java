package com.neueda.leap.services;

import com.neueda.leap.messaging.LatestPriceStore;
import com.neueda.leap.messaging.MarketPrice;
import com.neueda.leap.exceptions.InvalidSymbolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Service layer for market pricing operations.
 * Sits between the trade executor and the LatestPriceStore.
 * Provides usable prices to business logic with validation and staleness checks.
 */
@Service
public class MarketPriceService {
    private static final Logger logger = LoggerFactory.getLogger(MarketPriceService.class);
    
    private final LatestPriceStore latestPriceStore;
    
    public MarketPriceService(LatestPriceStore latestPriceStore) {
        this.latestPriceStore = latestPriceStore;
    }
    
    public Optional<MarketPrice> getLatestPrice(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            logger.error("Invalid symbol provided to getLatestPrice");
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
        
        String normalizedSymbol = symbol.trim().toUpperCase();
        
        Optional<MarketPrice> price = latestPriceStore.getPriceIfNotStale(normalizedSymbol);
        
        if (price.isEmpty()) {
            logger.warn("No fresh price available for symbol: {}", normalizedSymbol);
        } else {
            logger.debug("Retrieved fresh price for symbol: {}", normalizedSymbol);
        }
        
        return price;
    }
    
    public Optional<BigDecimal> getMidPrice(String symbol) {
        return getLatestPrice(symbol).map(price -> 
            price.bidPrice().add(price.askPrice())
                .divide(BigDecimal.valueOf(2), RoundingMode.HALF_UP)
        );
    }
    
    public Optional<BigDecimal> getSpread(String symbol) {
        return getLatestPrice(symbol).map(price -> 
            price.askPrice().subtract(price.bidPrice())
        );
    }
    
    public boolean isPriceAvailable(String symbol) {
        return getLatestPrice(symbol).isPresent();
    }
    

    public Optional<Long> getPriceAge(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return Optional.empty();
        }
        return latestPriceStore.getPriceAge(symbol.trim().toUpperCase());
    }
    
    public MarketPrice getPriceForExecution(String symbol) throws InvalidSymbolException {
        if (symbol == null || symbol.trim().isEmpty()) {
            logger.error("Invalid symbol provided to getPriceForExecution");
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
        
        Optional<MarketPrice> price = getLatestPrice(symbol);
        
        if (price.isEmpty()) {
            String normalizedSymbol = symbol.trim().toUpperCase();
            logger.error("No fresh price available for execution on symbol: {}", normalizedSymbol);
            throw new InvalidSymbolException("No fresh price available for symbol: " + normalizedSymbol);
        }
        
        return price.get();
    }
}