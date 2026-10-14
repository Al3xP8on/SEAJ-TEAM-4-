package com.neueda.leap.services;

import com.neueda.leap.enums.*;
import com.neueda.leap.messaging.*;
import com.neueda.leap.models.*;
import com.neueda.leap.repositories.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class OrderSettlementTest {
    @Mock OrderRepository orders;
    @Mock AccountRepository accounts;
    @Mock PositionsRepository positions;
    @Mock OrderHistoryRepository history;
    @Mock jakarta.persistence.EntityManager entityManager;
    OrderSettlementService service;
    Account account;
    Order order;
    @BeforeEach void setup() {
        service = new OrderSettlementService(orders, accounts, positions, history);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        account = new Account("ACC-0001", "Jay", "jay@example.com", null, new BigDecimal("1000.00"), AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(account, "id", 1L);
        order = new Order(account, new Instrument("AAPL", "Apple", "EQUITY", "USD", true), 2, new BigDecimal("100.00"), OrderSide.BUY, "key");
        order.setStatus(OrderStatus.PENDING);
        lenient().when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        lenient().when(accounts.findByIdForUpdate(1L)).thenReturn(Optional.of(account));
    }
    ExecutionEvent event(String price) { return new ExecutionEvent(UUID.randomUUID(), order.getId(), "ACC-0001", "AAPL", order.getSide(), 2, new BigDecimal(price), order.getPrice(), "SIM", Instant.now()); }
    Positions savedPosition() { var capture = ArgumentCaptor.forClass(Positions.class); verify(positions).save(capture.capture()); return capture.getValue(); }
    @Test void buyCreatesHoldingAndDebitsActualFillPrice() {
        assertTrue(service.settle(event("99.50")));
        assertEquals(new BigDecimal("801.00"), account.getCashBalance());
        assertEquals(2, savedPosition().getQuantity());
        assertEquals(new BigDecimal("99.50"), order.getExecutionPrice());
        assertEquals(OrderStatus.FILLED, order.getStatus());
        verify(history).save(argThat(h -> h.getStatus() == OrderStatus.FILLED));
    }
    @Test void buyUpdatesWeightedAverageCost() {
        when(positions.findByAccountIdAndSymbol(1L, "AAPL")).thenReturn(Optional.of(new Positions(1L, "AAPL", 2, new BigDecimal("80.00"))));
        service.settle(event("100.00"));
        Positions p = savedPosition();
        assertEquals(4, p.getQuantity()); assertEquals(new BigDecimal("90.00"), p.getAverageCost());
    }
    @Test void sellReducesHoldingsAndCreditsCash() {
        ReflectionTestUtils.setField(order, "side", OrderSide.SELL);
        when(positions.findByAccountIdAndSymbol(1L, "AAPL")).thenReturn(Optional.of(new Positions(1L, "AAPL", 5, new BigDecimal("80.00"))));
        service.settle(event("101.00"));
        var p = savedPosition(); assertEquals(3, p.getQuantity()); assertEquals(PositionStatus.PARTIALLY_CLOSED, p.getStatus());
        assertEquals(new BigDecimal("1202.00"), account.getCashBalance()); assertEquals(new BigDecimal("42.00"), p.getRealisedPnL());
    }
    @Test void sellingEntirePositionClosesIt() {
        ReflectionTestUtils.setField(order, "side", OrderSide.SELL);
        when(positions.findByAccountIdAndSymbol(1L, "AAPL")).thenReturn(Optional.of(new Positions(1L, "AAPL", 2, new BigDecimal("80.00"))));
        service.settle(event("100.00"));
        var p = savedPosition(); assertEquals(0, p.getQuantity()); assertEquals(PositionStatus.CLOSED, p.getStatus()); assertNotNull(p.getClosedAt());
    }
    @Test void buyingBackIntoClosedPositionReopensIt() throws Exception {
        Positions p = new Positions(1L, "AAPL", 2, new BigDecimal("80.00")); p.apply(-2, new BigDecimal("100.00"));
        when(positions.findByAccountIdAndSymbol(1L, "AAPL")).thenReturn(Optional.of(p));
        service.settle(event("100.00")); assertEquals(PositionStatus.OPEN, p.getStatus()); assertNull(p.getClosedAt());
    }
    @Test void duplicateFillCannotDebitOrAddHoldingsTwice() {
        ExecutionEvent fill = event("100.00"); service.settle(fill); assertFalse(service.settle(fill));
        assertEquals(new BigDecimal("800.00"), account.getCashBalance()); verify(positions, times(1)).save(any()); verify(accounts, times(1)).save(any());
    }
    @Test void cancelledOrderCannotBeFilled() {
        order.cancel(); assertFalse(service.settle(event("100.00")));
        verifyNoInteractions(accounts, positions, history);
    }
    @Test void limitBuyNeverFillsAboveItsLimit() {
        assertFalse(service.settle(event("101.00"))); assertEquals(OrderStatus.PENDING, order.getStatus()); verifyNoInteractions(accounts, positions, history);
    }
    @Test void limitSellNeverFillsBelowItsLimit() {
        ReflectionTestUtils.setField(order, "side", OrderSide.SELL);
        assertFalse(service.settle(event("99.00"))); verifyNoInteractions(accounts, positions, history);
    }
    @Test void insufficientCashRejectsWithoutMutatingPortfolio() {
        order.setOrderType(OrderType.MARKET); assertFalse(service.settle(event("600.00")));
        assertEquals(OrderStatus.REJECTED, order.getStatus()); assertEquals(new BigDecimal("1000.00"), account.getCashBalance());
        verify(positions, never()).save(any()); verify(accounts, never()).save(any());
    }
    @Test void insufficientHoldingsRejectsWithoutCreditingCash() {
        ReflectionTestUtils.setField(order, "side", OrderSide.SELL);
        assertFalse(service.settle(event("100.00"))); assertEquals(OrderStatus.REJECTED, order.getStatus()); verify(accounts, never()).save(any());
    }
    @Test void mismatchedExecutionIsNotSettled() {
        var invalid = new ExecutionEvent(UUID.randomUUID(), order.getId(), "ACC-0002", "AAPL", OrderSide.BUY, 2, BigDecimal.TEN, BigDecimal.TEN, "SIM", Instant.now());
        assertThrows(PoisonMessageException.class, () -> service.settle(invalid)); verifyNoInteractions(accounts, positions, history);
    }
}
