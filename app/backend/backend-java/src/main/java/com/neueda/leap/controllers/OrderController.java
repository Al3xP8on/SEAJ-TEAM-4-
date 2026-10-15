package com.neueda.leap.controllers;

import com.neueda.leap.dtos.OrderResponse;
import com.neueda.leap.dtos.OrderHistoryEntry;
import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.exceptions.OrderException;
import com.neueda.leap.exceptions.DuplicateOrderException;
import com.neueda.leap.messaging.TradeEvent;
import com.neueda.leap.messaging.TradeEventPublisher;
import com.neueda.leap.models.Account;
import com.neueda.leap.models.Order;
import com.neueda.leap.models.OrderHistory;
import com.neueda.leap.services.OrderService;
import com.neueda.leap.utils.LogMaskingUtil;
import com.neueda.leap.security.SecurityUtilsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/v1/orders")
public class OrderController {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    
    @Autowired
    private OrderService orderService;

    @Autowired
    private TradeEventPublisher tradeEventPublisher;

    @Autowired
    private SecurityUtilsService securityUtilsService;

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        logger.info("GET /v1/orders - Fetching orders");
        
        // If admin, return all orders; otherwise return only user's orders
        List<Order> orders;
        if (securityUtilsService.isAdmin()) {
            logger.info("Admin user - returning all orders");
            orders = orderService.getAllOrders();
        } else {
            Account userAccount = securityUtilsService.getAuthenticatedUserAccount();
            logger.info("Non-admin user - returning orders for account {}", LogMaskingUtil.maskAccountId(userAccount.getAccountId()));
            orders = orderService.getOrdersByAccount(userAccount.getAccountId());
        }
        
        logger.info("Found {} orders", orders.size());
        return ResponseEntity.ok(orders.stream().map(OrderResponse::from).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {
        if (logger.isInfoEnabled()) {
            logger.info("GET /v1/orders/{} - Fetching order by ID", LogMaskingUtil.maskId(id));
        }
        Optional<Order> order = orderService.getOrderById(id);
        if (order.isPresent()) {
            securityUtilsService.validateOrderOwnership(order.get());
            if (logger.isInfoEnabled()) {
                logger.info("Order found: {}", LogMaskingUtil.maskId(id));
            }
            return ResponseEntity.ok(OrderResponse.from(order.get()));
        }
        if (logger.isWarnEnabled()) {
            logger.warn("Order not found: {}", LogMaskingUtil.maskId(id));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @GetMapping("/account/{accountId}")
    public ResponseEntity<List<OrderResponse>> getOrdersByAccount(
            @PathVariable String accountId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        logger.info("GET /v1/orders/account/{} - Fetching orders for account", LogMaskingUtil.maskAccountId(accountId));
        
        // Validate user owns this account (unless user is admin)
        if (!securityUtilsService.isAdmin()) {
            securityUtilsService.validateAccountOwnership(accountId);
        }
        
        List<Order> orders = orderService.getOrdersByAccount(accountId);
        
        // Filter by status if provided
        if (status != null) {
            logger.info("Filtering orders by status: {}", LogMaskingUtil.sanitize(status));
            orders = orders.stream()
                .filter(o -> o.getStatus() == status)
                .collect(java.util.stream.Collectors.toList());
        }
        
        logger.info("Found {} orders for account {}", orders.size(), LogMaskingUtil.maskAccountId(accountId));
        return ResponseEntity.ok(orders.stream().map(OrderResponse::from).toList());
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody PlaceOrderRequest request) throws DuplicateOrderException {
        logger.info("POST /v1/orders - Creating order for account {} with symbol {}", LogMaskingUtil.maskAccountId(request.getAccountId()), request.getSymbol());
        
        // SECURITY: Validate that user is authorized to create orders for the requested account
        securityUtilsService.validateAccountOwnership(request.getAccountId());
        
        Order order = orderService.placeOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/execute")
    public ResponseEntity<OrderResponse> executeOrder(@PathVariable UUID id) {
        if (logger.isInfoEnabled()) {
            logger.info("PUT /v1/orders/{}/execute - Executing order", LogMaskingUtil.maskId(id));
        }
        Optional<Order> existing = orderService.getOrderById(id);
        if (existing.isEmpty()) return ResponseEntity.notFound().build();
        securityUtilsService.validateOrderOwnership(existing.get());
        try {
            Order order = orderService.executeOrder(id);
            if (logger.isInfoEnabled()) {
                logger.info("Order executed: {}", LogMaskingUtil.maskId(id));
            }
            return ResponseEntity.ok(OrderResponse.from(order));
        } catch (OrderException e) {
            logger.error("Failed to execute order {}: {}", LogMaskingUtil.maskId(id), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable UUID id) {
        if (logger.isInfoEnabled()) {
            logger.info("PUT /v1/orders/{}/cancel - Cancelling order", LogMaskingUtil.maskId(id));
        }
        Optional<Order> existing = orderService.getOrderById(id);
        if (existing.isEmpty()) return ResponseEntity.notFound().build();
        securityUtilsService.validateOrderOwnership(existing.get());
        try {
            Order order = orderService.cancelOrder(id);
            if (logger.isInfoEnabled()) {
                logger.info("Order cancelled: {}", LogMaskingUtil.maskId(id));
            }
            // Publish CANCELLED trade event to Kafka
            try {
                TradeEvent cancelledEvent = new TradeEvent(
                    order.getId(),
                    order.getAccount().getAccountId(),
                    order.getInstrument().getSymbol(),
                    order.getSide().toString(),
                    order.getPrice(),
                    order.getQuantity(),
                    "CANCELLED",
                    "GTC",
                    LocalDateTime.now()
                );
                tradeEventPublisher.publishTradeEvent(cancelledEvent);
                if (logger.isInfoEnabled()) {
                    logger.info("Cancellation event published for order: {}", LogMaskingUtil.maskId(id));
                }
            } catch (Exception e) {
                logger.error("Failed to publish cancellation event for order {}: {}", LogMaskingUtil.maskId(id), LogMaskingUtil.sanitize(e));
            }
            return ResponseEntity.ok(OrderResponse.from(order));
        } catch (OrderException e) {
            logger.error("Failed to cancel order {}: {}", LogMaskingUtil.maskId(id), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/reject")
    public ResponseEntity<OrderResponse> rejectOrder(
            @PathVariable UUID id,
            @RequestParam String reason) {
        if (logger.isInfoEnabled()) {
            logger.info("PUT /v1/orders/{}/reject - Rejecting order with reason: {}", LogMaskingUtil.maskId(id), LogMaskingUtil.sanitize(reason));
        }
        Optional<Order> existing = orderService.getOrderById(id);
        if (existing.isEmpty()) return ResponseEntity.notFound().build();
        securityUtilsService.validateOrderOwnership(existing.get());
        try {
            Order order = orderService.rejectOrder(id, reason);
            if (logger.isInfoEnabled()) {
                logger.info("Order rejected: {}", LogMaskingUtil.maskId(id));
            }
            return ResponseEntity.ok(OrderResponse.from(order));
        } catch (OrderException e) {
            logger.error("Failed to reject order {}: {}", LogMaskingUtil.maskId(id), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<OrderHistoryEntry>> getOrderHistory(@PathVariable UUID id) {
        logger.info("GET /v1/orders/{}/history - Fetching order history", LogMaskingUtil.sanitize(id));
        Optional<Order> order = orderService.getOrderById(id);
        if (order.isPresent()) {
            // Validate user owns this order
            securityUtilsService.validateOrderOwnership(order.get());
            List<OrderHistory> history = orderService.getOrderHistory(id);
            logger.info("Found {} history records for order {}", history.size(), LogMaskingUtil.sanitize(id));
            return ResponseEntity.ok(history.stream().map(OrderHistoryEntry::from).toList());
        }
        logger.warn("Order not found for history: {}", LogMaskingUtil.sanitize(id));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}
