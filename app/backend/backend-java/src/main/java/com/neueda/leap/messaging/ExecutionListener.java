package com.neueda.leap.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.models.Account;
import com.neueda.leap.models.Order;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Reads fills from the {@code executions} topic from the execution-engine.
 * Updates order status to FILLED and adjusts account cash balance (debit for BUY, credit for SELL).
 * A message that can't be settled is sent to the executions dead-letter topic.
 */
@Component
public class ExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(ExecutionListener.class);

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private TradeEventPublisher tradeEventPublisher;

    @Autowired
    private com.neueda.leap.services.OrderSettlementService settlement;

    @KafkaListener(topics = "${trading.kafka.topics.executions}", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onExecution(String message) throws JsonProcessingException {
        ExecutionEvent event = objectMapper.readValue(message, ExecutionEvent.class);
        settle(event);
    }

    private void settle(ExecutionEvent event) {
        if (!settlement.settle(event)) return;
        Order order = orderRepository.findById(event.orderId()).orElseThrow();
        Account account = accountRepository.findByAccountId(event.accountId()).orElseThrow();

        log.info("Execution settled: Order FILLED | {} {} @ {} | Venue: {}",
                event.side(), event.quantity(), event.price(), event.venue());
        
        try {
            TradeEvent executedEvent = new TradeEvent(
                event.orderId(),
                event.accountId(),
                order.getInstrument().getSymbol(),
                event.side().toString(),
                event.price(),
                event.quantity(),
                "EXECUTED",
                "GTC",
                LocalDateTime.now()
            );
            tradeEventPublisher.publishTradeEvent(executedEvent);
        } catch (Exception e) {
            log.error("Failed to publish TRADE_EXECUTED event: {}", e.getMessage(), e);
        }
        
        try {
            TradeEvent filledEvent = new TradeEvent(
                event.orderId(),
                event.accountId(),
                order.getInstrument().getSymbol(),
                event.side().toString(),
                event.price(),
                event.quantity(),
                "FILLED",
                "GTC",
                LocalDateTime.now()
            );
            tradeEventPublisher.publishTradeEvent(filledEvent);
        } catch (Exception e) {
            log.error("Failed to publish ORDER_FILLED event: {}", e.getMessage(), e);
        }
    }
}
