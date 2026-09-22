package com.neueda.leap.services;

import com.neueda.leap.models.Account;
import com.neueda.leap.models.Instrument;
import com.neueda.leap.models.Order;
import com.neueda.leap.models.OrderHistory;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.exceptions.OrderException;
import com.neueda.leap.exceptions.DuplicateOrderException;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.repositories.OrderHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

// OrderService demonstrates order operations with database, API, and authentication concerns.
@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderHistoryRepository orderHistoryRepository;

    public OrderService(OrderRepository orderRepository, OrderHistoryRepository orderHistoryRepository) {
        this.orderRepository = orderRepository;
        this.orderHistoryRepository = orderHistoryRepository;
    }

    // Creates a new order with idempotency protection.
    // Checks if an order with the same idempotency key already exists to prevent duplicates.
    public Order createOrder(Account account, Instrument instrument, long quantity, BigDecimal price, OrderSide side, String idempotencyKey) throws DuplicateOrderException {
        Optional<Order> existingOrder = orderRepository.findByIdempotencyKey(idempotencyKey);
        if (existingOrder.isPresent()) {
            throw new DuplicateOrderException(
                "Order with idempotency key '" + idempotencyKey + "' already exists. Order ID: " + existingOrder.get().getId()
            );
        }

        // Create and validate the order through the domain model
        Order order = new Order(account, instrument, quantity, price, side, idempotencyKey);
        Order savedOrder = orderRepository.save(order);

        recordOrderHistory(savedOrder.getId(), OrderStatus.NEW);

        return savedOrder;
    }

    // Executes a pending order.
    // Validates the order state, processes account debits/credits, and tracks status change.
    public Order executeOrder(String orderId) {
        Order order = findOrderOrThrow(orderId);
        order.execute();

        Order executedOrder = orderRepository.save(order);
        recordOrderHistory(orderId, OrderStatus.EXECUTED);

        return executedOrder;
    }

    // Cancels a pending order and records the status change.
    public Order cancelOrder(String orderId) {
        Order order = findOrderOrThrow(orderId);
        order.cancel();

        Order cancelledOrder = orderRepository.save(order);
        recordOrderHistory(orderId, OrderStatus.CANCELLED);

        return cancelledOrder;
    }

    // Rejects an order
    // Records the rejection in order history.
    public Order rejectOrder(String orderId, String reason) {
        Order order = findOrderOrThrow(orderId);
        order.setStatus(OrderStatus.REJECTED);

        Order rejectedOrder = orderRepository.save(order);
        recordOrderHistory(orderId, OrderStatus.REJECTED);

        return rejectedOrder;
    }

    // Retrieves an order by its ID.
    // Returns an Optional that will be empty if the order does not exist.
    public Optional<Order> getOrderById(String orderId) {
        return orderRepository.findById(orderId);
    }

    // Retrieves all orders for a specific account.
    // Returns an empty list if the account has no orders.
    public List<Order> getOrdersByAccount(String accountId) {
        return orderRepository.findByAccountId(accountId);
    }

    // Retrieves all orders with a specific status.
    public List<Order> getOrdersByStatus(OrderStatus status) {
        return orderRepository.findByStatus(status);
    }

    public List<Order> getPendingOrdersByAccount(String accountId) {
        return orderRepository.findByAccountIdAndStatus(accountId, OrderStatus.NEW);
    }

    // Retrieves the order history for a specific order.
    // Shows all status transitions for a specific order.
    public List<OrderHistory> getOrderHistory(String orderId) {
        return orderHistoryRepository.findByOrderId(orderId);
    }

    public boolean isOrderValidForExecution(String orderId) {
        return orderRepository.findById(orderId)
            .map(Order::isValidForExecution)
            .orElse(false);
    }

    public BigDecimal getOrderTotalValue(String orderId) {
        return orderRepository.findById(orderId)
            .map(Order::calculateTotalValue)
            .orElseThrow(() -> new OrderException("Order not found", orderId, "ORDER_NOT_FOUND"));
    }

    public List<Order> getExecutedOrdersByAccount(String accountId) {
        return orderRepository.findByAccountIdAndStatus(accountId, OrderStatus.EXECUTED);
    }

    public List<Order> getCancelledOrdersByAccount(String accountId) {
        return orderRepository.findByAccountIdAndStatus(accountId, OrderStatus.CANCELLED);
    }

    private void recordOrderHistory(String orderId, OrderStatus status) {
        OrderHistory history = new OrderHistory(orderId, status);
        orderHistoryRepository.save(history);
    }

    private Order findOrderOrThrow(String orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderException("Order not found", orderId, "ORDER_NOT_FOUND"));
    }
}
