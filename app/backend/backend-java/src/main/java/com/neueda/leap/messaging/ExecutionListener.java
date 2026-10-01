package com.neueda.leap.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.models.Account;
import com.neueda.leap.models.Order;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.utils.LogMaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads fills from the {@code executions} topic from the execution-engine.
 * Updates order status to FILLED and adjusts account cash balance (debit for BUY, credit for SELL).
 * A message that can't be parsed is logged and skipped rather than retried forever.
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

    @KafkaListener(topics = "${trading.kafka.topics.executions}", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onExecution(String message) {
        ExecutionEvent event;
        try {
            event = objectMapper.readValue(message, ExecutionEvent.class);
        } catch (JsonProcessingException e) {
            log.error("Skipping unreadable execution message: {}", message, e);
            return;
        }

        settle(event);
    }

    private void settle(ExecutionEvent event) {
        try {
            Optional<Order> orderOpt = orderRepository.findById(event.orderId());
            Optional<Account> accountOpt = accountRepository.findByAccountId(event.accountId());

            if (orderOpt.isEmpty()) {
                log.warn("Order not found for execution: {}", LogMaskingUtil.maskId(event.orderId()));
                return;
            }

            if (accountOpt.isEmpty()) {
                log.warn("Account not found for execution: {}", LogMaskingUtil.maskAccountId(event.accountId()));
                return;
            }

            Order order = orderOpt.get();
            Account account = accountOpt.get();

            // Only settle if order is still NEW (at-least-once delivery safety)
            if (!order.getStatus().equals(OrderStatus.NEW)) {
                log.info("Order {} is already {}, skipping duplicate execution", LogMaskingUtil.maskId(event.orderId()), order.getStatus());
                return;
            }

            // Calculate execution amount
            BigDecimal executionAmount = event.price().multiply(BigDecimal.valueOf(event.quantity()));

            // Update cash balance based on side
            try {
                if ("BUY".equalsIgnoreCase(event.side().toString())) {
                    account.debit(executionAmount);
                } else if ("SELL".equalsIgnoreCase(event.side().toString())) {
                    account.credit(executionAmount);
                }
            } catch (IllegalArgumentException e) {
                log.warn("Cannot complete execution for order {}: {}", LogMaskingUtil.maskId(event.orderId()), e.getMessage());
                return;
            }

            accountRepository.save(account);

            // Update order status to FILLED
            order.setStatus(OrderStatus.FILLED);
            orderRepository.save(order);

            log.info("Execution settled: Order {} FILLED | {} {} @ {} | Account {} balance: ${} | Venue: {}",
                    LogMaskingUtil.maskId(event.orderId()), event.side(), event.quantity(), event.price(),
                    LogMaskingUtil.maskAccountId(event.accountId()), account.getCashBalance(), event.venue());

        } catch (Exception e) {
            log.error("Error settling execution event", e);
        }
    }
}
