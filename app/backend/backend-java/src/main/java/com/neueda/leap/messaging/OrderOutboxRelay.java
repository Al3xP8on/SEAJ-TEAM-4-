package com.neueda.leap.messaging;

import com.neueda.leap.repositories.OrderOutboxRepository;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.concurrent.TimeUnit;

/** At-least-once delivery: acknowledge Kafka before marking a row published. */
@Component
public class OrderOutboxRelay {
    private final OrderOutboxRepository repository;
    private final KafkaTemplate<String, String> kafka;

    public OrderOutboxRelay(OrderOutboxRepository repository, KafkaTemplate<String, String> kafka) {
        this.repository = repository;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelayString = "${trading.orders.outbox-interval:1000}")
    @Transactional
    public void publishPending() {
        for (var event : repository.pending(PageRequest.of(0, 50))) {
            try {
                kafka.send(event.getTopic(), event.getMessageKey(), event.getPayload()).get(5, TimeUnit.SECONDS);
                event.markPublished();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception error) {
                LoggerFactory.getLogger(getClass()).warn("Order event {} is awaiting Kafka; delivery will retry", event.getEventId());
                // Keep the failed row and everything behind it in order for the next attempt.
                return;
            }
        }
    }
}
