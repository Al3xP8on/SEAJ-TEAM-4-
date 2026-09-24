package com.neueda.leap.controllers;

import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.exceptions.OrderException;
import com.neueda.leap.exceptions.DuplicateOrderException;
import com.neueda.leap.models.Account;
import com.neueda.leap.models.Instrument;
import com.neueda.leap.models.Order;
import com.neueda.leap.models.OrderHistory;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.InstrumentsRepository;
import com.neueda.leap.services.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/v1/orders")
public class OrderController {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    
    @Autowired
    private OrderService orderService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private InstrumentsRepository instrumentsRepository;

    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders() {
        logger.info("GET /v1/orders - Fetching all orders");
        // TODO: Add @PreAuthorize("hasRole('ADMIN')") when security is integrated
        List<Order> orders = orderService.getAllOrders();
        logger.info("Found {} orders", orders.size());
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable String id) {
        logger.info("GET /v1/orders/{} - Fetching order by ID", id);
        Optional<Order> order = orderService.getOrderById(id);
        if (order.isPresent()) {
            logger.info("Order found: {}", id);
            return ResponseEntity.ok(order.get());
        }
        logger.warn("Order not found: {}", id);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @GetMapping("/account/{accountId}")
    public ResponseEntity<List<Order>> getOrdersByAccount(
            @PathVariable String accountId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        logger.info("GET /v1/orders/account/{} - Fetching orders for account", accountId);
        List<Order> orders = orderService.getOrdersByAccount(accountId);
        
        // Filter by status if provided
        if (status != null) {
            logger.info("Filtering orders by status: {}", status);
            orders = orders.stream()
                .filter(o -> o.getStatus() == status)
                .collect(java.util.stream.Collectors.toList());
        }
        
        logger.info("Found {} orders for account {}", orders.size(), accountId);
        return ResponseEntity.ok(orders);
    }

    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody PlaceOrderRequest request) {
        logger.info("POST /v1/orders - Creating order for account {} with symbol {}", request.getAccountId(), request.getSymbol());
        
        try {
            // Fetch Account
            Optional<Account> account = accountRepository.findByAccountId(request.getAccountId());
            if (account.isEmpty()) {
                logger.warn("Account not found: {}", request.getAccountId());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            // Fetch Instrument
            Optional<Instrument> instrument = instrumentsRepository.findBySymbol(request.getSymbol());
            if (instrument.isEmpty()) {
                logger.warn("Instrument not found: {}", request.getSymbol());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            // Check if instrument is tradable
            if (!instrument.get().isTradable()) {
                logger.warn("Instrument is not tradable: {}", request.getSymbol());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            // Generate idempotency key
            String idempotencyKey = UUID.randomUUID().toString();

            // Default to BUY side
            OrderSide side = OrderSide.BUY;

            // Create the order
            Order order = orderService.createOrder(
                account.get(),
                instrument.get(),
                request.getQuantity(),
                request.getPrice(),
                side,
                idempotencyKey
            );

            logger.info("Order created successfully: {}", order.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(order);
        } catch (DuplicateOrderException e) {
            logger.error("Duplicate order: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (OrderException e) {
            logger.error("Failed to create order: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Unexpected error creating order: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{id}/execute")
    public ResponseEntity<Order> executeOrder(@PathVariable String id) {
        logger.info("PUT /v1/orders/{}/execute - Executing order", id);
        try {
            Order order = orderService.executeOrder(id);
            logger.info("Order executed: {}", id);
            return ResponseEntity.ok(order);
        } catch (OrderException e) {
            logger.error("Failed to execute order {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<Order> cancelOrder(@PathVariable String id) {
        logger.info("PUT /v1/orders/{}/cancel - Cancelling order", id);
        try {
            Order order = orderService.cancelOrder(id);
            logger.info("Order cancelled: {}", id);
            return ResponseEntity.ok(order);
        } catch (OrderException e) {
            logger.error("Failed to cancel order {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<Order> rejectOrder(
            @PathVariable String id,
            @RequestParam String reason) {
        logger.info("PUT /v1/orders/{}/reject - Rejecting order with reason: {}", id, reason);
        try {
            Order order = orderService.rejectOrder(id, reason);
            logger.info("Order rejected: {}", id);
            return ResponseEntity.ok(order);
        } catch (OrderException e) {
            logger.error("Failed to reject order {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<OrderHistory>> getOrderHistory(@PathVariable String id) {
        logger.info("GET /v1/orders/{}/history - Fetching order history", id);
        Optional<Order> order = orderService.getOrderById(id);
        if (order.isPresent()) {
            List<OrderHistory> history = orderService.getOrderHistory(id);
            logger.info("Found {} history records for order {}", history.size(), id);
            return ResponseEntity.ok(history);
        }
        logger.warn("Order not found for history: {}", id);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}
