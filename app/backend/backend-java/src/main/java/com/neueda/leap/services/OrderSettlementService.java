package com.neueda.leap.services;

import com.neueda.leap.enums.*;
import com.neueda.leap.messaging.ExecutionEvent;
import com.neueda.leap.messaging.PoisonMessageException;
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
        if (!account.isActive() || !order.getInstrument().isTradable()
            || (event.side() == OrderSide.BUY && (account.getCashBalance().compareTo(amount) < 0
                || (long) held.map(Positions::getQuantity).orElse(0) + event.quantity() > Integer.MAX_VALUE))
            || (event.side() == OrderSide.SELL && held.map(Positions::getQuantity).orElse(0) < event.quantity())) {
            order.setStatus(OrderStatus.REJECTED);
            orders.save(order);
            OrderHistory entry = new OrderHistory(order.getId(), OrderStatus.REJECTED);
            entry.setReason("Account inactive, instrument unavailable, or insufficient cash/holdings at execution");
            history.save(entry);
            return false;
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
        return true;
    }
}
