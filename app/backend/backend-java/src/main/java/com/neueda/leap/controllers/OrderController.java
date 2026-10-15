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
        List<Order> orders;
        if (securityUtilsService.isAdmin()) {
            orders = orderService.getAllOrders();
        } else {
            Account userAccount = securityUtilsService.getAuthenticatedUserAccount();
            orders = orderService.getOrdersByAccount(userAccount.getAccountId());
        }
        return ResponseEntity.ok(orders.stream().map(OrderResponse::from).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {
        logger.info("GET /v1/orders/{id} - Fetching order by ID");
        Optional<Order> order = orderService.getOrderById(id);
        if (order.isPresent()) {
            securityUtilsService.validateOrderOwnership(order.get());
            return ResponseEntity.ok(OrderResponse.from(order.get()));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @GetMapping("/account/{accountId}")
    public ResponseEntity<List<OrderResponse>> getOrdersByAccount(
            @PathVariable String accountId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        logger.info("GET /v1/orders/account/{accountId} - Fetching orders for account");

        if (!securityUtilsService.isAdmin()) {
            securityUtilsService.validateAccountOwnership(accountId);
        }

        List<Order> orders = orderService.getOrdersByAccount(accountId);

        if (status != null) {
            orders = orders.stream()
                .filter(o -> o.getStatus() == status)
                .toList();
        }

        return ResponseEntity.ok(orders.stream().map(OrderResponse::from).toList());
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody PlaceOrderRequest request) throws DuplicateOrderException {
        logger.info("POST /v1/orders - Creating order");
        securityUtilsService.validateAccountOwnership(request.getAccountId());
        Order order = orderService.placeOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/execute")
    public ResponseEntity<OrderResponse> executeOrder(@PathVariable UUID id) {
        logger.info("PUT /v1/orders/{id}/execute - Executing order");
        Optional<Order> existing = orderService.getOrderById(id);
        if (existing.isEmpty()) return ResponseEntity.notFound().build();
        securityUtilsService.validateOrderOwnership(existing.get());
        try {
            Order order = orderService.executeOrder(id);
            return ResponseEntity.ok(OrderResponse.from(order));
        } catch (OrderException e) {
            logger.error("Failed to execute order: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable UUID id) {
        logger.info("PUT /v1/orders/{id}/cancel - Cancelling order");
        Optional<Order> existing = orderService.getOrderById(id);
        if (existing.isEmpty()) return ResponseEntity.notFound().build();
        securityUtilsService.validateOrderOwnership(existing.get());
        try {
            Order order = orderService.cancelOrder(id);
            publishCancellationEvent(order, id);
            return ResponseEntity.ok(OrderResponse.from(order));
        } catch (OrderException e) {
            logger.error("Failed to cancel order: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    private void publishCancellationEvent(Order order, UUID id) {
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
        } catch (Exception e) {
            logger.error("Failed to publish cancellation event: {}", e.getMessage());
        }
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/reject")
    public ResponseEntity<OrderResponse> rejectOrder(
            @PathVariable UUID id,
            @RequestParam String reason) {
        logger.info("PUT /v1/orders/{id}/reject - Rejecting order");
        Optional<Order> existing = orderService.getOrderById(id);
        if (existing.isEmpty()) return ResponseEntity.notFound().build();
        securityUtilsService.validateOrderOwnership(existing.get());
        try {
            Order order = orderService.rejectOrder(id, reason);
            return ResponseEntity.ok(OrderResponse.from(order));
        } catch (OrderException e) {
            logger.error("Failed to reject order: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<OrderHistoryEntry>> getOrderHistory(@PathVariable UUID id) {
        logger.info("GET /v1/orders/{id}/history - Fetching order history");
        Optional<Order> order = orderService.getOrderById(id);
        if (order.isPresent()) {
            securityUtilsService.validateOrderOwnership(order.get());
            List<OrderHistory> history = orderService.getOrderHistory(id);
            return ResponseEntity.ok(history.stream().map(OrderHistoryEntry::from).toList());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}
