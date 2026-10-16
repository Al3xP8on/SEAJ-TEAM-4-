package com.neueda.leap.messaging;

import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.services.OrderService;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.LocalDateTime;

/** Recover legacy NEW orders through the same durable command path as new submissions. */
@Component
public class PendingOrderRepublisher {
    private final OrderRepository orders;
    private final OrderService service;
    private final Duration age;
    public PendingOrderRepublisher(OrderRepository orders, OrderService service,
            @Value("${trading.orders.republish-after:PT5M}") Duration age) {
        this.orders = orders;
        this.service = service;
        this.age = age;
    }
    @Scheduled(fixedDelayString = "${trading.orders.republish-interval:PT1M}",
            initialDelayString = "${trading.orders.republish-interval:PT1M}")
    public void republishStaleOrders() {
        try {
            for (var order : orders.findByStatusAndCreatedBefore(OrderStatus.NEW, LocalDateTime.now().minus(age))) {
                try { service.executeOrder(order.getId()); }
                catch (Exception error) {
                    LoggerFactory.getLogger(getClass()).warn("Could not recover order {}", order.getId());
                }
            }
        } catch (Exception error) {
            LoggerFactory.getLogger(getClass()).warn("Legacy order recovery will retry");
        }
    }
}
