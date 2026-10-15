package com.neueda.leap.services;

import com.neueda.leap.enums.*;
import com.neueda.leap.messaging.ExecutionEvent;
import com.neueda.leap.messaging.PoisonMessageException;
import com.neueda.leap.observability.OrderTelemetry;
import com.neueda.leap.models.*;
import com.neueda.leap.repositories.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

/** Serializes cancellation/fill races and settles cash, positions and status in one transaction. */
@Service
public class OrderSettlementService {
    private final OrderRepository orders;
    private final AccountRepository accounts;
    private final PositionsRepository positions;
    private final OrderHistoryRepository history;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    @org.springframework.beans.factory.annotation.Autowired private com.neueda.leap.messaging.OrderEventOutbox outbox;

    public OrderSettlementService(OrderRepository orders, AccountRepository accounts,
                                  PositionsRepository positions, OrderHistoryRepository history) {
        this.orders = orders;
        this.accounts = accounts;
        this.positions = positions;
        this.history = history;
    }

    @Transactional
    public boolean settle(ExecutionEvent event) {
        Order order = orders.findByIdForUpdate(event.orderId())
            .orElseThrow(() -> new PoisonMessageException("Order not found for execution"));
        if (!order.getStatus().canBeCancelled()) return false;
        validateQuote(event);
        if (!order.getAccount().getAccountId().equals(event.accountId())
            || !order.getInstrument().getSymbol().equals(event.symbol())
            || order.getSide() != event.side() || order.getQuantity() != event.quantity()
            || event.price() == null || event.price().signum() <= 0 || event.price().scale() > 2) {
            throw new PoisonMessageException("Execution does not match the order");
        }
        if (order.getOrderType() == OrderType.LIMIT
            && (order.getSide() == OrderSide.BUY ? event.price().compareTo(order.getPrice()) > 0
                                               : event.price().compareTo(order.getPrice()) < 0)) return false;
        Account account = accounts.findByIdForUpdate(order.getAccount().getId())
            .orElseThrow(() -> new PoisonMessageException("Account not found for execution"));
        entityManager.refresh(account, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        var held = positions.findByAccountIdAndSymbol(account.getId(), event.symbol());
        BigDecimal amount = event.price().multiply(BigDecimal.valueOf(event.quantity()));
        int availableHoldings = held.map(Positions::getQuantity).orElse(0);
        if (!account.isActive()) {
            return reject(order, "Account is " + account.getStatus() + ". Only ACTIVE accounts can execute orders.");
        }
        if (!order.getInstrument().isTradable()) {
            return reject(order, "Instrument " + event.symbol() + " is not tradable.");
        }
        if (event.side() == OrderSide.BUY && account.getCashBalance().compareTo(amount) < 0) {
            return reject(order, String.format(java.util.Locale.ROOT,
                "Insufficient cash at execution: required $%.2f, available $%.2f.", amount, account.getCashBalance()));
        }
        if (event.side() == OrderSide.BUY && (long) availableHoldings + event.quantity() > Integer.MAX_VALUE) {
            return reject(order, "Position quantity limit exceeded for " + event.symbol()
                + ": the resulting holding would exceed " + Integer.MAX_VALUE + " units.");
        }
        if (event.side() == OrderSide.SELL && availableHoldings < event.quantity()) {
            return reject(order, "Insufficient holdings at execution: requested " + event.quantity()
                + " " + event.symbol() + " units, available " + availableHoldings + ".");
        }
        Positions position;
        if (held.isPresent()) {
            position = held.get();
            if (event.side() == OrderSide.SELL) {
                BigDecimal profit = event.price().subtract(position.getAverageCost()).multiply(BigDecimal.valueOf(event.quantity()));
                position.setRealisedPnL((position.getRealisedPnL() == null ? BigDecimal.ZERO : position.getRealisedPnL()).add(profit));
            }
            try { position.apply(event.side() == OrderSide.BUY ? event.quantity() : -event.quantity(), event.price()); }
            catch (com.neueda.leap.exceptions.InsufficientHoldingsException e) { throw new PoisonMessageException("Insufficient holdings", e); }
            if (event.side() == OrderSide.BUY) {
                position.setStatus(PositionStatus.OPEN);
                position.setClosedAt(null);
            } else if (position.getQuantity() > 0) position.setStatus(PositionStatus.PARTIALLY_CLOSED);
        } else position = new Positions(account.getId(), event.symbol(), event.quantity(), event.price());
        if (event.side() == OrderSide.BUY) account.debit(amount);
        else account.credit(amount);
        positions.save(position);
        accounts.save(account);
        order.setExecutionPrice(event.price());
        order.setStatus(OrderStatus.FILLED);
        orders.save(order);
        history.save(new OrderHistory(order.getId(), OrderStatus.FILLED));
        OrderTelemetry.filled(order);
        var details = new java.util.LinkedHashMap<String, Object>();
        details.put("executionId", event.executionId());
        details.put("executionPrice", event.price());
        details.put("venue", event.venue());
        details.put("executedOn", event.executedOn());
        details.put("quoteTimestamp", event.quoteTimestamp());
        details.put("quoteEventId", event.quoteEventId());
        details.put("bidPrice", event.bidPrice());
        details.put("askPrice", event.askPrice());
        outbox.lifecycle(order, com.neueda.leap.messaging.EventType.ORDER_FILLED, null, details);
        outbox.lifecycle(order, com.neueda.leap.messaging.EventType.PORTFOLIO_UPDATED, null,
            java.util.Map.of("executionId", event.executionId(), "cashBalance", account.getCashBalance(),
                "positionQuantity", position.getQuantity(), "averageCost", position.getAverageCost()));
        return true;
    }

    private boolean reject(Order order, String reason) {
        order.setStatus(OrderStatus.REJECTED);
        orders.save(order);
        OrderHistory entry = new OrderHistory(order.getId(), OrderStatus.REJECTED);
        entry.setReason(reason);
        history.save(entry);
        OrderTelemetry.rejected(order, OrderTelemetry.RejectStage.EXECUTION);
        outbox.lifecycle(order, com.neueda.leap.messaging.EventType.ORDER_REJECTED, reason);
        outbox.command(order, "REJECT");
        return false;
    }

    private void validateQuote(ExecutionEvent event) {
        if (event.venue() == null || !java.util.Set.of("ALPACA_IEX", "ALPACA_SIP").contains(event.venue())
            || event.executedOn() == null || event.quoteTimestamp() == null || event.quoteEventId() == null
            || event.bidPrice() == null || event.askPrice() == null || event.bidPrice().signum() <= 0
            || event.askPrice().compareTo(event.bidPrice()) < 0
            || event.quoteTimestamp().isAfter(event.executedOn().plusSeconds(5))
            || java.time.Duration.between(event.quoteTimestamp(), event.executedOn()).compareTo(java.time.Duration.ofSeconds(60)) > 0) {
            throw new PoisonMessageException("Execution must reference a fresh Alpaca quote");
        }
        BigDecimal expected = (event.side() == OrderSide.BUY ? event.askPrice() : event.bidPrice())
            .setScale(2, event.side() == OrderSide.BUY ? java.math.RoundingMode.CEILING : java.math.RoundingMode.FLOOR);
        if (event.price() == null || expected.compareTo(event.price()) != 0)
            throw new PoisonMessageException("Execution price does not match the Alpaca quote");
    }
}
