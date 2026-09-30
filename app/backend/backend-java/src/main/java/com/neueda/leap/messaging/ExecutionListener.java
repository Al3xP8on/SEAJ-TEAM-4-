package com.neueda.trading.app.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.trading.app.application.OrderSettlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Reads fills from the {@code executions} topic and hands them to
 * {@link OrderSettlementService}. A message that can't be parsed is logged
 * and skipped rather than retried forever.
 */
@Component
public class ExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(ExecutionListener.class);

    private final ObjectMapper objectMapper;
    private final OrderSettlementService settlementService;

    public ExecutionListener(ObjectMapper objectMapper, OrderSettlementService settlementService) {
        this.objectMapper = objectMapper;
        this.settlementService = settlementService;
    }

    @KafkaListener(topics = "${trading.kafka.topics.executions}", groupId = "${spring.kafka.consumer.group-id}")
    public void onExecution(String message) {
        ExecutionEvent event;
        try {
            event = objectMapper.readValue(message, ExecutionEvent.class);
        } catch (JsonProcessingException e) {
            log.error("Skipping unreadable execution message: {}", message, e);
            return;
        }
        settlementService.settle(event);
    }
}
