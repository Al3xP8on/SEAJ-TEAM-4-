package com.neueda.leap.services;

import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.enums.*;
import com.neueda.leap.exceptions.*;
import com.neueda.leap.messaging.MarketPrice;
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
class OrderPlacementTest {
    @Mock OrderRepository orders;
    @Mock AccountRepository accounts;
    @Mock InstrumentsRepository instruments;
    @Mock PositionsRepository positions;
    @Mock OrderHistoryRepository history;
    @Mock MarketPriceService prices;
    @Mock OrderSettlementService settlement;
    @Mock jakarta.persistence.EntityManager entityManager;
    @InjectMocks OrderService service;
    Account account;
    Instrument instrument;
    PlaceOrderRequest request;

    @BeforeEach void setup() {
        account = new Account("ACC-0001", "Jay", "jay@example.com", null, new BigDecimal("1000.00"), AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(account, "id", 1L);
        instrument = new Instrument("AAPL", "Apple", "EQUITY", "USD", true);
        request = new PlaceOrderRequest("ACC-0001", "AAPL", OrderSide.BUY, 2, new BigDecimal("100.00"));
        request.setIdempotencyKey("one-submission");
        lenient().when(accounts.findByAccountId("ACC-0001")).thenReturn(Optional.of(account));
        lenient().when(accounts.findByIdForUpdate(1L)).thenReturn(Optional.of(account));
        lenient().when(instruments.findBySymbol("AAPL")).thenReturn(Optional.of(instrument));
        lenient().when(orders.save(any())).thenAnswer(call -> call.getArgument(0));
    }
    @Test void limitOrderRestsPendingWithoutAQuote() throws Exception {
        Order order = service.placeOrder(request);
        assertEquals(OrderType.LIMIT, order.getOrderType());
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertEquals(new BigDecimal("100.00"), order.getPrice());
        verify(prices, never()).getLatestPrice(any());
        verify(history, times(2)).save(any());
    }
    @Test void marketUsesServerAskAndIgnoresClientPrice() throws Exception {
        request.setOrderType(OrderType.MARKET);
        request.setPrice(new BigDecimal("0.01"));
        when(prices.getLatestPrice("AAPL")).thenReturn(Optional.of(new MarketPrice("AAPL", new BigDecimal("99.90"), new BigDecimal("100.123"), new BigDecimal("100.00"), Instant.now())));
        assertEquals(new BigDecimal("100.13"), service.placeOrder(request).getPrice());
    }
    @Test void marketWithoutQuoteFailsWithoutCreatingOrder() {
        request.setOrderType(OrderType.MARKET);
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.placeOrder(request));
        verify(orders, never()).save(any());
    }
    @Test void cashReservedByPendingBuysCannotBeSpentTwice() {
        Order pending = new Order(account, instrument, 9, new BigDecimal("100.00"), OrderSide.BUY, "other");
        when(orders.findByAccountId("ACC-0001")).thenReturn(List.of(pending));
        assertThrows(OrderException.class, () -> service.placeOrder(request));
        verify(orders, never()).save(any());
    }
    @Test void cannotSellMoreThanAvailableHoldings() {
        request.setSide(OrderSide.SELL);
        when(positions.findByAccountIdAndSymbol(1L, "AAPL")).thenReturn(Optional.of(new Positions(1L, "AAPL", 1, new BigDecimal("100.00"))));
        assertThrows(OrderException.class, () -> service.placeOrder(request));
        verify(orders, never()).save(any());
    }
    @Test void pendingSellsReserveHoldings() {
        request.setSide(OrderSide.SELL);
        when(positions.findByAccountIdAndSymbol(1L, "AAPL")).thenReturn(Optional.of(new Positions(1L, "AAPL", 3, new BigDecimal("100.00"))));
        when(orders.findByAccountId("ACC-0001")).thenReturn(List.of(new Order(account, instrument, 2, new BigDecimal("100.00"), OrderSide.SELL, "other")));
        assertThrows(OrderException.class, () -> service.placeOrder(request));
    }
    @Test void retryWithSameKeyReturnsExistingOrderWithoutDuplication() throws Exception {
        Order old = new Order(account, instrument, 2, new BigDecimal("100.00"), OrderSide.BUY, "one-submission");
        when(orders.findByIdempotencyKey("one-submission")).thenReturn(Optional.of(old));
        assertSame(old, service.placeOrder(request));
        verify(orders, never()).save(any());
    }
    @Test void retryKeyCannotChangeTheOrder() {
        when(orders.findByIdempotencyKey("one-submission")).thenReturn(Optional.of(new Order(account, instrument, 3, new BigDecimal("100.00"), OrderSide.BUY, "one-submission")));
        assertThrows(DuplicateOrderException.class, () -> service.placeOrder(request));
    }
    @Test void unsupportedTimeInForceIsRejected() {
        request.setTimeInForce(TimeInForce.IOC);
        assertThrows(OrderException.class, () -> service.placeOrder(request));
        verifyNoInteractions(accounts);
    }
    @Test void limitNeedsPriceWithAtMostTwoDecimals() {
        request.setPrice(null);
        assertThrows(OrderException.class, () -> service.placeOrder(request));
        request.setPrice(new BigDecimal("100.001"));
        assertThrows(OrderException.class, () -> service.placeOrder(request));
    }
    @Test void suspendedAccountCannotPlaceOrder() {
        account.setStatus(AccountStatus.SUSPENDED);
        assertThrows(OrderException.class, () -> service.placeOrder(request));
        verify(orders, never()).save(any());
    }
}
