package com.neueda.leap.services;

import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.messaging.LatestPriceStore;
import com.neueda.leap.messaging.MarketPrice;
import com.neueda.leap.models.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Validates order pricing against live market data and determines execution status.
 * 
 * Encapsulates the pricing validation workflow:
 * 1. Checks if instrument is tradeable
 * 2. Retrieves live market quote
 * 3. Applies pricing decision rule (pure function)
 * 4. Determines resulting order status
 * 
 * Reusable across consumers and other order processing workflows.
 */
@Service
public class OrderPricingValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderPricingValidator.class);
    private static final int MID_PRICE_SCALE = 2;
    
    private final LatestPriceStore latestPriceStore;
    
    public OrderPricingValidator(LatestPriceStore latestPriceStore) {
        this.latestPriceStore = latestPriceStore;
    }
    
    /**
     * Validates an order's pricing against current market conditions.
     * 
     * @param order the order to validate
     * @return PricingValidationResult containing decision and new status
     */
    public PricingValidationResult validateOrderPricing(Order order) {
        // Check if instrument is tradeable
        if (!order.getInstrument().isTradable()) {
            String reason = "Instrument not tradeable: " + order.getInstrument().getSymbol();
            logger.warn(reason);
            return new PricingValidationResult(OrderStatus.REJECTED, reason);
        }
        
        // Get live market quote
        Optional<MarketPrice> priceOpt = latestPriceStore.getPriceIfNotStale(order.getInstrument().getSymbol());
        
        if (priceOpt.isEmpty()) {
            String reason = "No live quote available for symbol: " + order.getInstrument().getSymbol();
            logger.warn(reason);
            return new PricingValidationResult(OrderStatus.REJECTED, reason);
        }
        
        MarketPrice marketPrice = priceOpt.get();
        
        // Calculate mid-price for pricing decision
        BigDecimal quotePrice = calculateMidPrice(marketPrice);
        
        logger.debug("Market quote - Symbol: {} | Bid: {} | Ask: {} | Mid: {}",
                order.getInstrument().getSymbol(),
                marketPrice.bidPrice(),
                marketPrice.askPrice(),
                quotePrice);
        
        // Apply pricing decision rule (pure function)
        PricingDecision decision = PricingDecision.decide(
                order.getPrice(),
                order.getSide().toString(),
                quotePrice,
                true  // Already validated tradeability above
        );
        
        // Map decision to order status
        OrderStatus newStatus = decision.canExecute() ? OrderStatus.PENDING : OrderStatus.REJECTED;
        
        return new PricingValidationResult(newStatus, decision.reason());
    }
    
    private BigDecimal calculateMidPrice(MarketPrice marketPrice) {
        return marketPrice.bidPrice()
                .add(marketPrice.askPrice())
                .divide(BigDecimal.valueOf(2), MID_PRICE_SCALE, RoundingMode.HALF_EVEN);
    }
    
    /**
     * Result of order pricing validation.
     * 
     * @param newStatus the resulting order status (PENDING if approved, REJECTED if not)
     * @param reason human-readable reason for the decision
     */
    public record PricingValidationResult(OrderStatus newStatus, String reason) {}
}
