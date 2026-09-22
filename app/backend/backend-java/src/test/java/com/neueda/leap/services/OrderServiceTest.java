package com.neueda.leap.services;

import com.neueda.leap.models.Account;
import com.neueda.leap.models.Instrument;
import com.neueda.leap.models.Order;
import com.neueda.leap.models.OrderHistory;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.exceptions.OrderException;
import com.neueda.leap.exceptions.DuplicateOrderException;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.repositories.OrderHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;
import java.util.Arrays;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("OrderService Tests")
public class OrderServiceTest {

    private OrderService orderService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderHistoryRepository orderHistoryRepository;

    private Account account;
    private Instrument instrument;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orderService = new OrderService(orderRepository, orderHistoryRepository);
        account = new Account("ACC-001", "Test Account", new BigDecimal("50000.00"), AccountStatus.ACTIVE);
        instrument = new Instrument("AAPL", "Apple Inc.", "EQUITY", "USD", true);
    }

    @Test
    @DisplayName("Should create a new order successfully")
    void testCreateOrderSuccess() throws DuplicateOrderException {
        String idempotencyKey = "order-001";
        Order expectedOrder = new Order(account, instrument, 100, new BigDecimal("150.50"), OrderSide.BUY, idempotencyKey);

        when(orderRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class))).thenReturn(expectedOrder);

        Order createdOrder = orderService.createOrder(account, instrument, 100, new BigDecimal("150.50"), OrderSide.BUY, idempotencyKey);

        assertNotNull(createdOrder);
        assertEquals(expectedOrder.getId(), createdOrder.getId());
        assertEquals(OrderStatus.NEW, createdOrder.getStatus());
        verify(orderRepository).findByIdempotencyKey(idempotencyKey);
        verify(orderRepository).save(any(Order.class));
        verify(orderHistoryRepository).save(any(OrderHistory.class));
    }

    @Test
    @DisplayName("Should throw DuplicateOrderException when idempotency key exists")
    void testCreateOrderDuplicate() {
        String idempotencyKey = "order-001";
        Order existingOrder = new Order(account, instrument, 100, new BigDecimal("150.50"), OrderSide.BUY, idempotencyKey);

        when(orderRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingOrder));

        try {
            orderService.createOrder(account, instrument, 100, new BigDecimal("150.50"), OrderSide.BUY, idempotencyKey);
            fail("Expected DuplicateOrderException to be thrown");
        } catch (DuplicateOrderException e) {
            // Expected exception
        }

        verify(orderRepository).findByIdempotencyKey(idempotencyKey);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Should execute an order successfully")
    void testExecuteOrderSuccess() {
        String orderId = "550e8400-e29b-41d4-a716-446655440000";
        Order order = new Order(orderId, account, instrument, 100, new BigDecimal("150.50"), OrderSide.BUY, "idem-key", OrderStatus.NEW, LocalDateTime.now());
        Order executedOrder = new Order(orderId, account, instrument, 100, new BigDecimal("150.50"), OrderSide.BUY, "idem-key", OrderStatus.EXECUTED, LocalDateTime.now());

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(executedOrder);

        Order result = orderService.executeOrder(orderId);

        assertNotNull(result);
        assertEquals(OrderStatus.EXECUTED, result.getStatus());
        verify(orderRepository).findById(orderId);
        verify(orderRepository).save(any(Order.class));
        verify(orderHistoryRepository).save(any(OrderHistory.class));
    }

    @Test
    @DisplayName("Should throw OrderException when executing non-existent order")
    void testExecuteOrderNotFound() {
        String orderId = "non-existent-order";
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThrows(OrderException.class, () -> orderService.executeOrder(orderId));
        verify(orderRepository).findById(orderId);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Should cancel an order successfully")
    void testCancelOrderSuccess() {
        String orderId = "550e8400-e29b-41d4-a716-446655440001";
        Order order = new Order(orderId, account, instrument, 100, new BigDecimal("150.50"), OrderSide.BUY, "idem-key", OrderStatus.NEW, LocalDateTime.now());
        Order cancelledOrder = new Order(orderId, account, instrument, 100, new BigDecimal("150.50"), OrderSide.BUY, "idem-key", OrderStatus.CANCELLED, LocalDateTime.now());

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(cancelledOrder);

        Order result = orderService.cancelOrder(orderId);

        assertNotNull(result);
        assertEquals(OrderStatus.CANCELLED, result.getStatus());
        verify(orderRepository).findById(orderId);
        verify(orderRepository).save(any(Order.class));
        verify(orderHistoryRepository).save(any(OrderHistory.class));
    }

    @Test
    @DisplayName("Should throw OrderException when cancelling non-existent order")
    void testCancelOrderNotFound() {
        String orderId = "non-existent-order";
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThrows(OrderException.class, () -> orderService.cancelOrder(orderId));
        verify(orderRepository).findById(orderId);
    }

    @Test
    @DisplayName("Should reject an order successfully")
    void testRejectOrderSuccess() {
        String orderId = "550e8400-e29b-41d4-a716-446655440002";
        Order order = new Order(orderId, account, instrument, 100, new BigDecimal("150.50"), OrderSide.BUY, "idem-key", OrderStatus.NEW, LocalDateTime.now());
        Order rejectedOrder = new Order(orderId, account, instrument, 100, new BigDecimal("150.50"), OrderSide.BUY, "idem-key", OrderStatus.REJECTED, LocalDateTime.now());

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(rejectedOrder);

        Order result = orderService.rejectOrder(orderId, "Insufficient funds");

        assertNotNull(result);
        assertEquals(OrderStatus.REJECTED, result.getStatus());
        verify(orderRepository).findById(orderId);
        verify(orderRepository).save(any(Order.class));
        verify(orderHistoryRepository).save(any(OrderHistory.class));
    }

    @Test
    @DisplayName("Should retrieve an order by ID")
    void testGetOrderById() {
        String orderId = "550e8400-e29b-41d4-a716-446655440003";
        Order order = new Order(orderId, account, instrument, 100, new BigDecimal("150.50"), OrderSide.BUY, "idem-key", OrderStatus.NEW, LocalDateTime.now());

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        Optional<Order> result = orderService.getOrderById(orderId);

        assertTrue(result.isPresent());
        assertEquals(orderId, result.get().getId());
        verify(orderRepository).findById(orderId);
    }

    @Test
    @DisplayName("Should return empty Optional when order not found")
    void testGetOrderByIdNotFound() {
        String orderId = "non-existent-order";
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        Optional<Order> result = orderService.getOrderById(orderId);

        assertFalse(result.isPresent());
        verify(orderRepository).findById(orderId);
    }

    @Test
    @DisplayName("Should retrieve all orders for an account")
    void testGetOrdersByAccount() {
        String accountId = "ACC-001";
        
        Order order1 = new Order(account, instrument, 100, new BigDecimal("150.50"), OrderSide.BUY, "idem-key-1");
        Order order2 = new Order(account, instrument, 50, new BigDecimal("155.00"), OrderSide.SELL, "idem-key-2");
        List<Order> orders = Arrays.asList(order1, order2);

        when(orderRepository.findByAccountId(accountId)).thenReturn(orders);

        List<Order> result = orderService.getOrdersByAccount(accountId);

        assertNotNull(result);
        assertEquals(2, result.size());
        verify(orderRepository).findByAccountId(accountId);
    }

    @Test
    @DisplayName("Should retrieve all orders with a specific status")
    void testGetOrdersByStatus() {
        Order order1 = new Order(account, instrument, 100, new BigDecimal("150.50"), OrderSide.BUY, "idem-key-1");
        Order order2 = new Order(account, instrument, 50, new BigDecimal("155.00"), OrderSide.BUY, "idem-key-2");
        List<Order> orders = Arrays.asList(order1, order2);

        when(orderRepository.findByStatus(OrderStatus.NEW)).thenReturn(orders);

        List<Order> result = orderService.getOrdersByStatus(OrderStatus.NEW);

        assertNotNull(result);
        assertEquals(2, result.size());
        verify(orderRepository).findByStatus(OrderStatus.NEW);
    }

    @Test
    @DisplayName("Should retrieve pending orders for an account")
    void testGetPendingOrdersByAccount() {
        String accountId = "ACC-001";
        
        Order order = new Order(account, instrument, 100, new BigDecimal("150.50"), OrderSide.BUY, "idem-key");
        List<Order> orders = Arrays.asList(order);

        when(orderRepository.findByAccountIdAndStatus(accountId, OrderStatus.NEW)).thenReturn(orders);

        List<Order> result = orderService.getPendingOrdersByAccount(accountId);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(orderRepository).findByAccountIdAndStatus(accountId, OrderStatus.NEW);
    }

    @Test
    @DisplayName("Should retrieve order history for an order")
    void testGetOrderHistory() {
        String orderId = "550e8400-e29b-41d4-a716-446655440004";
        List<OrderHistory> historyRecords = Arrays.asList(
            new OrderHistory(orderId, OrderStatus.NEW),
            new OrderHistory(orderId, OrderStatus.EXECUTED)
        );

        when(orderHistoryRepository.findByOrderId(orderId)).thenReturn(historyRecords);

        List<OrderHistory> result = orderService.getOrderHistory(orderId);

        assertNotNull(result);
        assertEquals(2, result.size());
        verify(orderHistoryRepository).findByOrderId(orderId);
    }

    @Test
    @DisplayName("Should validate if order is valid for execution")
    void testIsOrderValidForExecution() {
        String orderId = "550e8400-e29b-41d4-a716-446655440005";
        Order order = new Order(orderId, account, instrument, 100, new BigDecimal("150.50"), OrderSide.BUY, "idem-key", OrderStatus.NEW, LocalDateTime.now());

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        boolean result = orderService.isOrderValidForExecution(orderId);

        assertTrue(result);
        verify(orderRepository).findById(orderId);
    }

    @Test
    @DisplayName("Should return false when validating non-existent order")
    void testIsOrderValidForExecutionNotFound() {
        String orderId = "non-existent-order";
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        boolean result = orderService.isOrderValidForExecution(orderId);

        assertFalse(result);
        verify(orderRepository).findById(orderId);
    }

    @Test
    @DisplayName("Should calculate total order value")
    void testGetOrderTotalValue() {
        String orderId = "550e8400-e29b-41d4-a716-446655440006";
        BigDecimal price = new BigDecimal("150.50");
        long quantity = 100;
        Order order = new Order(orderId, account, instrument, quantity, price, OrderSide.BUY, "idem-key", OrderStatus.NEW, LocalDateTime.now());

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        BigDecimal result = orderService.getOrderTotalValue(orderId);

        assertNotNull(result);
        assertEquals(new BigDecimal("15050.00"), result);
        verify(orderRepository).findById(orderId);
    }

    @Test
    @DisplayName("Should throw OrderException when calculating value for non-existent order")
    void testGetOrderTotalValueNotFound() {
        String orderId = "non-existent-order";
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThrows(OrderException.class, () -> orderService.getOrderTotalValue(orderId));
        verify(orderRepository).findById(orderId);
    }

    @Test
    @DisplayName("Should retrieve executed orders for an account")
    void testGetExecutedOrdersByAccount() {
        String accountId = "ACC-001";
        
        Order order = new Order(account, instrument, 100, new BigDecimal("150.50"), OrderSide.BUY, "idem-key");
        List<Order> orders = Arrays.asList(order);

        when(orderRepository.findByAccountIdAndStatus(accountId, OrderStatus.EXECUTED)).thenReturn(orders);

        List<Order> result = orderService.getExecutedOrdersByAccount(accountId);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(orderRepository).findByAccountIdAndStatus(accountId, OrderStatus.EXECUTED);
    }

    @Test
    @DisplayName("Should retrieve cancelled orders for an account")
    void testGetCancelledOrdersByAccount() {
        String accountId = "ACC-001";
        
        Order order = new Order(account, instrument, 100, new BigDecimal("150.50"), OrderSide.BUY, "idem-key");
        List<Order> orders = Arrays.asList(order);

        when(orderRepository.findByAccountIdAndStatus(accountId, OrderStatus.CANCELLED)).thenReturn(orders);

        List<Order> result = orderService.getCancelledOrdersByAccount(accountId);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(orderRepository).findByAccountIdAndStatus(accountId, OrderStatus.CANCELLED);
    }
}
