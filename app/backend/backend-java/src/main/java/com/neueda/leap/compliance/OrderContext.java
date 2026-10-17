package com.neueda.leap.compliance;

import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.models.Account;
import com.neueda.leap.models.Instrument;
import com.neueda.leap.models.Order;
import com.neueda.leap.models.Positions;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Everything the rules need about one order, loaded once so rules stay pure and never query the
 * database themselves.
 *
 * @param heldQuantity  shares of this instrument the account holds now
 * @param pendingOrders the account's orders that are still open (they reserve cash or shares)
 * @param positions     all of the account's positions, for portfolio value
 * @param priceOf       latest market price for a symbol, if one is available
 */
public record OrderContext(
        Account account,
        Instrument instrument,
        OrderSide side,
        long quantity,
        BigDecimal price,
        long heldQuantity,
        List<Order> pendingOrders,
        List<Positions> positions,
        Function<String, Optional<BigDecimal>> priceOf
) {
    public OrderContext {
        pendingOrders = pendingOrders == null ? List.of() : List.copyOf(pendingOrders);
        positions = positions == null ? List.of() : List.copyOf(positions);
        priceOf = priceOf == null ? symbol -> Optional.empty() : priceOf;
    }

    public String symbol() {
        return instrument.getSymbol();
    }

    /** Price × quantity of this order */
    public BigDecimal orderValue() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }

    /** Cash already promised to the account's open buy orders */
    public BigDecimal reservedCash() {
        return pendingOrders.stream().filter(o -> o.getSide() == OrderSide.BUY)
                .map(Order::calculateTotalValue).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Shares of this instrument already promised to open orders on the given side */
    public long reservedQuantity(OrderSide onSide) {
        return pendingOrders.stream()
                .filter(o -> o.getSide() == onSide && o.getInstrument().getSymbol().equals(symbol()))
                .mapToLong(Order::getQuantity).sum();
    }

    /**
     * Cash plus holdings at their latest price (average cost when no price is available).
     * This order's own instrument is valued at the order price, so the comparison is consistent.
     */
    public BigDecimal portfolioValue() {
        BigDecimal holdings = positions.stream()
                .filter(p -> p.getQuantity() > 0)
                .map(p -> unitPrice(p.getSymbol(), p.getAverageCost()).multiply(BigDecimal.valueOf(p.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return account.getCashBalance().add(holdings);
    }

    private BigDecimal unitPrice(String positionSymbol, BigDecimal fallback) {
        if (positionSymbol.equals(symbol())) return price;
        return priceOf.apply(positionSymbol).orElse(fallback);
    }
}
