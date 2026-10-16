package com.neueda.leap.messaging;

import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.models.Order;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.services.OrderService;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.*;

class PendingOrderDispatcherTest {
    OrderRepository orders = mock(OrderRepository.class);
    OrderService service = mock(OrderService.class);
    PendingOrderDispatcher dispatcher = new PendingOrderDispatcher(orders, service);
    @Test void recoversPendingOrdersWithoutGatingOnMarketabilityAndAvoidsImmediateDuplicates() {
        Order order = mock(Order.class);
        UUID id = UUID.randomUUID();
        when(order.getId()).thenReturn(id);
        when(orders.findByStatus(OrderStatus.PENDING)).thenReturn(List.of(order));
        dispatcher.dispatchPendingOrders();
        dispatcher.dispatchPendingOrders();
        verify(service, times(1)).executeOrder(id);
        new PendingOrderDispatcher(orders, service).dispatchPendingOrders();
        verify(service, times(2)).executeOrder(id);
    }
    @Test void retriesFailedEnqueuesWithoutWaitingForTheThrottle() {
        Order order = mock(Order.class);
        UUID id = UUID.randomUUID();
        when(order.getId()).thenReturn(id);
        when(orders.findByStatus(OrderStatus.PENDING)).thenReturn(List.of(order));
        doThrow(new IllegalStateException("db offline")).doReturn(order).when(service).executeOrder(id);
        dispatcher.dispatchPendingOrders();
        dispatcher.dispatchPendingOrders();
        verify(service, times(2)).executeOrder(id);
    }
}
