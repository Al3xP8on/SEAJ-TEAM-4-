package com.neueda.leap.messaging;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.neueda.leap.models.*;
import com.neueda.leap.enums.*;
import com.neueda.leap.repositories.OrderRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;

class PendingOrderRepublisherTest {
    private final OrderRepository repository = mock(OrderRepository.class);
    private final TradeEventPublisher publisher = mock(TradeEventPublisher.class);
    private final Duration delay = Duration.ofMinutes(5);
    private final PendingOrderRepublisher republisher = new PendingOrderRepublisher(repository, publisher, delay);
    private Order order() {
        Order order = mock(Order.class);
        Account account = mock(Account.class);
        Instrument instrument = mock(Instrument.class);
        when(order.getId()).thenReturn(UUID.randomUUID());
        when(order.getAccount()).thenReturn(account);
        when(account.getAccountId()).thenReturn("ACC-001");
        when(order.getInstrument()).thenReturn(instrument);
        when(instrument.getSymbol()).thenReturn("AAPL");
        when(order.getSide()).thenReturn(OrderSide.BUY);
        when(order.getPrice()).thenReturn(BigDecimal.TEN);
        when(order.getQuantity()).thenReturn(3L);
        when(order.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 1, 1, 12, 0));
        return order;
    }
    @Test void queriesNewOrdersWithConfiguredAgeAndSkipsEmptyResults() {
        LocalDateTime before = LocalDateTime.now().minus(delay);
        when(repository.findByStatusAndCreatedBefore(eq(OrderStatus.NEW), any())).thenReturn(List.of());
        republisher.republishStaleOrders();
        LocalDateTime after = LocalDateTime.now().minus(delay);
        ArgumentCaptor<LocalDateTime> threshold = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).findByStatusAndCreatedBefore(eq(OrderStatus.NEW), threshold.capture());
        assertFalse(threshold.getValue().isBefore(before));
        assertFalse(threshold.getValue().isAfter(after));
        verifyNoInteractions(publisher);
    }
    @Test void convertsOrderToInitiatedTradeEvent() {
        Order order = order();
        when(repository.findByStatusAndCreatedBefore(eq(OrderStatus.NEW), any())).thenReturn(List.of(order));
        republisher.republishStaleOrders();
        ArgumentCaptor<TradeEvent> captor = ArgumentCaptor.forClass(TradeEvent.class);
        verify(publisher).publishTradeEvent(captor.capture());
        TradeEvent event = captor.getValue();
        assertEquals(order.getId(), event.getTradeId());
        assertEquals("ACC-001", event.getAccountId());
        assertEquals("AAPL", event.getSymbol());
        assertEquals("BUY", event.getSide());
        assertEquals(BigDecimal.TEN, event.getPrice());
        assertEquals(3L, event.getQuantity());
        assertEquals("INITIATED", event.getStatus());
        assertEquals("GTC", event.getTimeInForce());
        assertEquals(order.getCreatedAt(), event.getTimestamp());
    }
    @Test void processesMultipleBatchesAndContinuesAfterPublisherFailure() {
        List<Order> orders = IntStream.range(0, 101).mapToObj(i -> order()).toList();
        when(repository.findByStatusAndCreatedBefore(eq(OrderStatus.NEW), any())).thenReturn(orders);
        doThrow(new IllegalStateException("offline")).doNothing().when(publisher).publishTradeEvent(any());
        assertDoesNotThrow(republisher::republishStaleOrders);
        verify(publisher, times(101)).publishTradeEvent(any());
    }
    @Test void containsRepositoryFailuresForNextScheduledRun() {
        when(repository.findByStatusAndCreatedBefore(eq(OrderStatus.NEW), any())).thenThrow(new IllegalStateException("offline"));
        assertDoesNotThrow(republisher::republishStaleOrders);
        verifyNoInteractions(publisher);
    }
}
