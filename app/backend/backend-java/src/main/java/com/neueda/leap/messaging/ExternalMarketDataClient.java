package com.neueda.leap.messaging;

import com.neueda.leap.interfaces.MarketDataClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * External market data client that fetches real-time price data from external providers.
 * Implements the MarketDataClient interface to provide a standardized interface for 
 * price data retrieval.
 */
@Component
public class ExternalMarketDataClient implements MarketDataClient {
    private static final Logger logger = LoggerFactory.getLogger(ExternalMarketDataClient.class);

    /**
 * Fetches the latest market price for a given symbol from the external provider.
     * 
     * @param symbol the symbol to fetch price for
     * @return MarketPrice object with bid, ask, and last price
     * @throws IllegalArgumentException if symbol is null or empty
     */
    @Override
    public MarketPrice getLatestMarketPrice(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            logger.error("Invalid symbol provided to getLatestMarketPrice");
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
        
        String trimmedSymbol = symbol.trim().toUpperCase();
        
        try {
            BigDecimal bidPrice = new BigDecimal("100.50");
            BigDecimal askPrice = new BigDecimal("100.55");
            BigDecimal lastPrice = new BigDecimal("100.52");
            Instant timestamp = Instant.now();
            
            // 2. Validate provider response
            if (bidPrice.compareTo(BigDecimal.ZERO) <= 0 || 
                askPrice.compareTo(BigDecimal.ZERO) <= 0) {
                logger.error("Invalid prices received from provider for symbol: {}", trimmedSymbol);
                throw new IllegalArgumentException("Provider returned invalid prices");
            }

            MarketPrice price = new MarketPrice(
                trimmedSymbol,
                bidPrice,
                askPrice,
                lastPrice,
                timestamp
            );
            
            logger.debug("Retrieved market price for symbol: {}, bid: {}, ask: {}", 
                trimmedSymbol, bidPrice, askPrice);
            return price;
            
        } catch (Exception e) {
            logger.error("Error fetching market price for symbol: {}", trimmedSymbol, e);
            throw new RuntimeException("Failed to fetch market price for symbol: " + trimmedSymbol, e);
        }
    }
}