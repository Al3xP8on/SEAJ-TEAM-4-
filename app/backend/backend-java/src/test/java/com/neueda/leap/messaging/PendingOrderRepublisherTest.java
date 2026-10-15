package com.neueda.leap.messaging;

import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.models.Order;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.services.OrderService;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class PendingOrderRepublisherTest {
    OrderRepository orders = mock(OrderRepository.class);
    OrderService service = mock(OrderService.class);
    PendingOrderRepublisher recovery = new PendingOrderRepublisher(orders, service, Duration.ofMinutes(5));
    @Test void legacyNewOrdersRecoverThroughTheSameDurableCommandPath() {
        Order order = mock(Order.class);
        UUID id = UUID.randomUUID();
        when(order.getId()).thenReturn(id);
        when(orders.findByStatusAndCreatedBefore(eq(OrderStatus.NEW), any())).thenReturn(List.of(order));
        recovery.republishStaleOrders();
        verify(service).executeOrder(id);
    }
    @Test void containsDatabaseFailuresForTheNextScheduledAttempt() {
        when(orders.findByStatusAndCreatedBefore(eq(OrderStatus.NEW), any())).thenThrow(new IllegalStateException());
        assertDoesNotThrow(recovery::republishStaleOrders);
        verifyNoInteractions(service);
    }
}
