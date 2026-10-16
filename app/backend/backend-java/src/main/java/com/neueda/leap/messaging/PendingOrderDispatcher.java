package com.neueda.leap.messaging;

import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.models.Order;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.services.OrderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Recover resting orders after an engine restart. Marketability belongs to the engine. */
@Component
public class PendingOrderDispatcher {
    private final OrderRepository orders;
    private final OrderService service;
    private final ConcurrentHashMap<UUID, Instant> sent = new ConcurrentHashMap<>();

    public PendingOrderDispatcher(OrderRepository orders, OrderService service) {
        this.orders = orders;
        this.service = service;
    }
    @Scheduled(fixedDelayString = "${trading.orders.dispatch-interval:10000}")
    public void dispatchPendingOrders() {
        var pending = orders.findByStatus(OrderStatus.PENDING);
        sent.keySet().retainAll(pending.stream().map(Order::getId).collect(java.util.stream.Collectors.toSet()));
        for (Order order : pending) {
            Instant last = sent.get(order.getId());
            if (last != null && last.isAfter(Instant.now().minusSeconds(30))) continue;
            try {
                // Locks/rechecks the order, preventing a later submit after a concurrent cancellation.
                service.executeOrder(order.getId());
                sent.put(order.getId(), Instant.now());
            } catch (Exception error) {
                LoggerFactory.getLogger(getClass()).warn("Could not requeue pending order {}", order.getId());
            }
        }
    }
}
