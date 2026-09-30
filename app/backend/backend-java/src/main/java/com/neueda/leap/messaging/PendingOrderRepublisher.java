package com.neueda.leap.messaging;


//import com.neueda.trading.app.persistence.OrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Safety net for the gap between committing an order and publishing it:
 * any order still NEW after {@code trading.orders.republish-after} is sent
 * to the {@code orders} topic again (e.g. Kafka was down when it was
 * accepted, or the engine lost it). The {@code orders} table itself acts as
 * the outbox. Duplicates are harmless because settlement only applies a
 * fill to an order that is still NEW.
 */
@Component
public class PendingOrderRepublisher {

    private static final Logger log = LoggerFactory.getLogger(PendingOrderRepublisher.class);
    private static final int BATCH_SIZE = 100;

    private final OrderMapper orderMapper;
    private final OrderEventPublisher publisher;
    private final Duration republishAfter;

    public PendingOrderRepublisher(OrderMapper orderMapper, OrderEventPublisher publisher,
                                   @Value("${trading.orders.republish-after}") Duration republishAfter) {
        this.orderMapper = orderMapper;
        this.publisher = publisher;
        this.republishAfter = republishAfter;
    }

    @Scheduled(fixedDelayString = "${trading.orders.republish-interval}",
            initialDelayString = "${trading.orders.republish-interval}")
    public void republishStaleOrders() {
        List<OrderEvent> stale = orderMapper.findPendingOlderThan(republishAfter.toSeconds(), BATCH_SIZE);
        if (!stale.isEmpty()) {
            log.info("Republishing {} order(s) still NEW after {}", stale.size(), republishAfter);
            stale.forEach(publisher::publish);
        }
    }
}
