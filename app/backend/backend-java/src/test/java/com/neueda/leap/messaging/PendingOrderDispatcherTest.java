package com.neueda.leap.messaging;

import com.neueda.leap.enums.*;
import com.neueda.leap.models.*;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.services.MarketPriceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PendingOrderDispatcherTest {
    OrderRepository orders = mock(OrderRepository.class);
    MarketPriceService prices = mock(MarketPriceService.class);
    OrderEventPublisher publisher = mock(OrderEventPublisher.class);
    PendingOrderDispatcher dispatcher = new PendingOrderDispatcher(orders, prices, publisher);
    Order order;
    @BeforeEach void setup() {
        var account = new Account("ACC-0001", "Jay", "jay@example.com", null, new BigDecimal("1000.00"), AccountStatus.ACTIVE);
        var instrument = new Instrument("AAPL", "Apple", "EQUITY", "USD", true);
        order = new Order(account, instrument, 1, new BigDecimal("100.00"), OrderSide.BUY, "key");
        order.setStatus(OrderStatus.PENDING);
        when(orders.findByStatus(OrderStatus.PENDING)).thenReturn(List.of(order));
    }
    void quote(String bid, String ask) { when(prices.getLatestPrice("AAPL")).thenReturn(Optional.of(new MarketPrice("AAPL", new BigDecimal(bid), new BigDecimal(ask), BigDecimal.ONE, Instant.now()))); }
    @Test void nonMarketableBuyWaitsUntilAskReachesLimit() {
        quote("100", "101"); dispatcher.dispatchPendingOrders(); verifyNoInteractions(publisher);
        quote("99", "100"); dispatcher.dispatchPendingOrders();
        verify(publisher).publish(argThat(e -> e.price().compareTo(new BigDecimal("100")) == 0));
    }
    @Test void missingQuoteKeepsOrderPending() { dispatcher.dispatchPendingOrders(); verifyNoInteractions(publisher); }
    @Test void marketDispatchesAtQuoteAndAvoidsImmediateDuplicate() {
        order.setOrderType(OrderType.MARKET); quote("110", "111");
        dispatcher.dispatchPendingOrders(); dispatcher.dispatchPendingOrders();
        verify(publisher, times(1)).publish(argThat(e -> e.price().compareTo(new BigDecimal("111")) == 0));
    }
    @Test void pendingOrderCanBeRedispatchedAfterRestart() {
        quote("99", "100"); dispatcher.dispatchPendingOrders();
        new PendingOrderDispatcher(orders, prices, publisher).dispatchPendingOrders();
        verify(publisher, times(2)).publish(any());
    }
    @Test void sellWaitsForBidToReachLimit() {
        org.springframework.test.util.ReflectionTestUtils.setField(order, "side", OrderSide.SELL);
        quote("99", "100"); dispatcher.dispatchPendingOrders(); verifyNoInteractions(publisher);
        quote("100", "101"); dispatcher.dispatchPendingOrders(); verify(publisher).publish(any());
    }
}
