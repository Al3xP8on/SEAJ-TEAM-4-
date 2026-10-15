package com.neueda.leap.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.services.OrderSettlementService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Settlement and its outbox commit together before the Kafka offset advances. */
@Component
public class ExecutionListener {
    private final ObjectMapper mapper;
    private final OrderSettlementService settlement;
    public ExecutionListener(ObjectMapper mapper, OrderSettlementService settlement) {
        this.mapper = mapper;
        this.settlement = settlement;
    }
    @KafkaListener(topics = "${trading.kafka.topics.executions}", groupId = "${spring.kafka.consumer.group-id}")
    public void onExecution(String message) throws JsonProcessingException {
        settlement.settle(mapper.readValue(message, ExecutionEvent.class));
    }
}
