package com.neueda.leap.controllers;

import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.exceptions.OrderException;
import com.neueda.leap.exceptions.DuplicateOrderException;
import com.neueda.leap.models.Order;
import com.neueda.leap.models.OrderHistory;
import com.neueda.leap.services.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    
    @Autowired
    private OrderService orderService;

    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders() {
        try {
            // TODO: Add @PreAuthorize("hasRole('ADMIN')") when security is integrated
            List<Order> orders = orderService.getAllOrders();
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable String id) {
        try {
            Optional<Order> order = orderService.getOrderById(id);
            if (order.isPresent()) {
                return ResponseEntity.ok(order.get());
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/account/{accountId}")
    public ResponseEntity<List<Order>> getOrdersByAccount(
            @PathVariable String accountId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        try {
            List<Order> orders = orderService.getOrdersByAccount(accountId);
            
            // Filter by status if provided
            if (status != null) {
                orders = orders.stream()
                    .filter(o -> o.getStatus() == status)
                    .collect(java.util.stream.Collectors.toList());
            }
            
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody PlaceOrderRequest request) {
        try {
            // TODO: Requires AccountRepository and InstrumentRepository implementation
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        } catch (OrderException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{id}/execute")
    public ResponseEntity<Order> executeOrder(@PathVariable String id) {
        try {
            Order order = orderService.executeOrder(id);
            return ResponseEntity.ok(order);
        } catch (OrderException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<Order> cancelOrder(@PathVariable String id) {
        try {
            Order order = orderService.cancelOrder(id);
            return ResponseEntity.ok(order);
        } catch (OrderException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<Order> rejectOrder(
            @PathVariable String id,
            @RequestParam String reason) {
        try {
            Order order = orderService.rejectOrder(id, reason);
            return ResponseEntity.ok(order);
        } catch (OrderException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<OrderHistory>> getOrderHistory(@PathVariable String id) {
        try {
            Optional<Order> order = orderService.getOrderById(id);
            if (order.isPresent()) {
                List<OrderHistory> history = orderService.getOrderHistory(id);
                return ResponseEntity.ok(history);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
