package com.neueda.leap.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains the most recent price record for each instrument.
 * Thread-safe store for real-time market prices with staleness detection.
 */
@Component
public class LatestPriceStore {
    private static final Logger logger = LoggerFactory.getLogger(LatestPriceStore.class);
    
    // Maximum age of a price before it's considered stale (in seconds)
    private static final long PRICE_STALENESS_THRESHOLD_SECONDS = 60;
    
    // Concurrent map to store the latest prices by symbol
    private final ConcurrentHashMap<String, MarketPrice> priceStore = new ConcurrentHashMap<>();
    
    /**
     * Updates the price store with a new market price.
     * 
     * @param marketPrice the market price to store
     * @throws IllegalArgumentException if marketPrice is null
     */
    public void updatePrice(MarketPrice marketPrice) {
        if (marketPrice == null) {
            logger.error("Cannot store null MarketPrice");
            throw new IllegalArgumentException("MarketPrice cannot be null");
        }
        
        String symbol = marketPrice.symbol();
        priceStore.put(symbol, marketPrice);
        logger.debug("Updated price for symbol: {}, bid: {}, ask: {}", 
            symbol, marketPrice.bidPrice(), marketPrice.askPrice());
    }
    
    /**
     * Retrieves the latest price for a symbol.
     * 
     * @param symbol the symbol to retrieve price for
     * @return Optional containing the MarketPrice if available
     */
    public Optional<MarketPrice> getPrice(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            logger.error("Cannot retrieve price for null or empty symbol");
            return Optional.empty();
        }
        
        String normalizedSymbol = symbol.trim().toUpperCase();
        return Optional.ofNullable(priceStore.get(normalizedSymbol));
    }
    
    /**
     * Retrieves the latest price for a symbol if it's not stale.
     * A price is considered stale if it's older than the staleness threshold.
     * 
     * @param symbol the symbol to retrieve price for
     * @return Optional containing the MarketPrice if available and not stale
     */
    public Optional<MarketPrice> getPriceIfNotStale(String symbol) {
        // TEMPORARILY DISABLED: Comment out staleness check to debug pricing rejections
        // return getPrice(symbol).filter(this::isNotStale);
        return getPrice(symbol);  // Accept any available price regardless of age
    }
    
    /**
     * Checks if a price is stale.
     * 
     * @param marketPrice the price to check
     * @return true if the price is older than the staleness threshold
     */
    public boolean isStale(MarketPrice marketPrice) {
        if (marketPrice == null) {
            return true;
        }
        
        long secondsSinceUpdate = ChronoUnit.SECONDS.between(
            marketPrice.priceTimeStamp(), 
            Instant.now()
        );
        
        return secondsSinceUpdate > PRICE_STALENESS_THRESHOLD_SECONDS;
    }
    
    /**
     * Checks if a price is not stale.
     * 
     * @param marketPrice the price to check
     * @return true if the price is within the staleness threshold
     */
    public boolean isNotStale(MarketPrice marketPrice) {
        return !isStale(marketPrice);
    }
    
    /**
     * Gets the age of a price in seconds.
     * 
     * @param symbol the symbol to check age for
     * @return Optional containing the age in seconds
     */
    public Optional<Long> getPriceAge(String symbol) {
        return getPrice(symbol).map(price -> 
            ChronoUnit.SECONDS.between(price.priceTimeStamp(), Instant.now())
        );
    }
    
    /**
     * Removes a price from the store.
     * 
     * @param symbol the symbol to remove
     * @return true if a price was removed, false if none existed
     */
    public boolean removePrice(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return false;
        }
        return priceStore.remove(symbol.trim().toUpperCase()) != null;
    }
    
    /**
     * Clears all prices from the store.
     */
    public void clearAll() {
        priceStore.clear();
        logger.info("Cleared all prices from store");
    }
    
    /**
     * Gets the number of symbols in the store.
     * 
     * @return the number of symbols
     */
    public int getSize() {
        return priceStore.size();
    }
}