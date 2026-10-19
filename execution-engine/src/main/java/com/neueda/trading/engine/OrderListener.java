package com.neueda.trading.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.trading.enums.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Single local engine: Kafka commands and quotes are serialised against the resting book. */
@Component
public class OrderListener {
    // Logs each decision once, linked to the order's trace by the OpenTelemetry agent. Account ids are
    // left out, matching how the trade API masks them; quotes aren't logged, as they arrive constantly.
    private static final Logger log = LoggerFactory.getLogger(OrderListener.class);
    private final LiveMarket market;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String executionsTopic;
    private final String lifecycleTopic;
    private final Map<UUID, OrderEvent> pending = new HashMap<>();
    private final Map<String, Set<UUID>> bySymbol = new HashMap<>();
    private final Map<UUID, String> waitingReasons = new HashMap<>();
    // Bounded replay cache. Settlement also enforces durable order-level idempotency.
    private final Map<UUID, ExecutionEvent> completed = new LinkedHashMap<>() {
        @Override protected boolean removeEldestEntry(Map.Entry<UUID, ExecutionEvent> entry) {
            return size() > 10_000;
        }
    };

    public OrderListener(LiveMarket market, KafkaTemplate<String, String> kafka, ObjectMapper mapper,
            @Value("${engine.topics.executions}") String executionsTopic,
            @Value("${engine.topics.order-lifecycle:order-lifecycle}") String lifecycleTopic) {
        this.market = market;
        this.kafka = kafka;
        this.mapper = mapper;
        this.executionsTopic = executionsTopic;
        this.lifecycleTopic = lifecycleTopic;
    }

    @KafkaListener(topics = "${engine.topics.orders}", groupId = "${spring.kafka.consumer.group-id}",
            concurrency = "${engine.topics.partitions}")
    public synchronized void onOrder(String message) throws JsonProcessingException {
        OrderEvent order = mapper.readValue(message, OrderEvent.class);
        if (!order.action().equals("SUBMIT")) {
            boolean wasResting = pending.containsKey(order.orderId());
            remove(order);
            completed.put(order.orderId(), null);
            log.info("Order {} orderId={} symbol={} wasResting={}", order.action().toLowerCase(Locale.ROOT),
                    order.orderId(), safe(order.symbol()), wasResting);
            return;
        }
        if (completed.containsKey(order.orderId())) {
            var fill = completed.get(order.orderId());
            log.debug("Duplicate order command orderId={} alreadyFilled={}", order.orderId(), fill != null);
            if (fill != null) report(order, fill);
            return;
        }
        if (!pending.containsKey(order.orderId())) {
            log.info("Order received orderId={} symbol={} side={} type={} quantity={} limitPrice={}",
                    order.orderId(), safe(order.symbol()), order.side(), order.orderType(), order.quantity(),
                    order.orderType().equals("LIMIT") ? order.price() : null);
            emit(order, EventType.ORDER_RECEIVED, null, Map.of(), UUID.randomUUID());
            pending.put(order.orderId(), order);
            bySymbol.computeIfAbsent(order.symbol(), ignored -> new LinkedHashSet<>()).add(order.orderId());
        }
        work(order);
    }

    @KafkaListener(topics = "${engine.topics.market-data:market-data}",
            groupId = "${spring.kafka.consumer.group-id}-quotes")
    public synchronized void onMarketData(String message) throws JsonProcessingException {
        var event = mapper.readTree(message);
        if (!"PRICE_UPDATE".equals(event.path("eventType").asText())) return;
        var data = event.path("payload");
        String source = data.path("source").asText();
        if (!Set.of("ALPACA_IEX", "ALPACA_SIP").contains(source)) return;
        String symbol = data.path("symbol").asText();
        market.update(new LiveMarket.Quote(UUID.fromString(event.path("eventId").asText()), source, symbol,
                decimal(data.path("bidPrice")), decimal(data.path("askPrice")),
                Instant.parse(data.path("priceTimeStamp").asText())));
        for (UUID id : new ArrayList<>(bySymbol.getOrDefault(symbol, Set.of()))) {
            OrderEvent order = pending.get(id);
            if (order != null) work(order);
        }
    }

    private void work(OrderEvent order) {
        ExecutionEvent cached = completed.get(order.orderId());
        if (cached != null) { report(order, cached); return; }
        LiveMarket.Decision decision = market.evaluate(order);
        if (decision.fill() == null) {
            if (!Objects.equals(waitingReasons.get(order.orderId()), decision.reason())) {
                Map<String, Object> details = new LinkedHashMap<>();
                if (decision.quote() != null) {
                    details.put("quoteTimestamp", decision.quote().timestamp());
                    details.put("bidPrice", decision.quote().bid());
                    details.put("askPrice", decision.quote().ask());
                }
                log.info("Order waiting orderId={} symbol={} reason={} bid={} ask={}", order.orderId(),
                        safe(order.symbol()), decision.reason(), details.get("bidPrice"), details.get("askPrice"));
                emit(order, EventType.ORDER_WAITING, decision.reason(), details, UUID.randomUUID());
                waitingReasons.put(order.orderId(), decision.reason());
            }
            return;
        }
        completed.put(order.orderId(), decision.fill());
        ExecutionEvent fill = decision.fill();
        log.info("Order filled orderId={} executionId={} symbol={} side={} quantity={} price={} venue={}",
                order.orderId(), fill.executionId(), safe(order.symbol()), order.side(), fill.quantity(),
                fill.price(), fill.venue());
        report(order, fill);
    }

    private void report(OrderEvent order, ExecutionEvent fill) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("executionId", fill.executionId());
        details.put("executionPrice", fill.price());
        details.put("quoteEventId", fill.quoteEventId());
        details.put("quoteTimestamp", fill.quoteTimestamp());
        details.put("bidPrice", fill.bidPrice());
        details.put("askPrice", fill.askPrice());
        details.put("venue", fill.venue());
        details.put("executedOn", fill.executedOn());
        details.put("executionMode", "LOCAL_MATCHING");
        // Publish the match before the report, so ORDER_FILLED can never overtake it in the audit topic.
        emit(order, EventType.TRADE_EXECUTED, null, details, fill.executionId());
        send(executionsTopic, fill.accountId(), fill);
        remove(order);
    }

    private void remove(OrderEvent order) {
        pending.remove(order.orderId());
        waitingReasons.remove(order.orderId());
        Set<UUID> ids = bySymbol.get(order.symbol());
        if (ids != null) {
            ids.remove(order.orderId());
            if (ids.isEmpty()) bySymbol.remove(order.symbol());
        }
    }

    private void emit(OrderEvent order, EventType type, String reason, Map<String, Object> details, UUID id) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.orderId());
        payload.put("accountId", order.accountId());
        payload.put("symbol", order.symbol());
        payload.put("side", order.side());
        payload.put("quantity", order.quantity());
        payload.put("orderType", order.orderType());
        payload.put("price", order.price());
        payload.put("reason", reason);
        payload.putAll(details);
        Instant timestamp = details.get("executedOn") instanceof Instant time ? time : Instant.now();
        send(lifecycleTopic, order.orderId().toString(), new EventEnvelope(id, type, timestamp, 1, payload));
    }

    private void send(String topic, String key, Object value) {
        try {
            kafka.send(topic, key, mapper.writeValueAsString(value)).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka delivery interrupted", error);
        } catch (Exception error) {
            // The error handler logs the failure with its stack trace and retries; this adds which topic
            throw new IllegalStateException("Kafka delivery to " + topic + " failed; order will retry", error);
        }
    }

    /** Symbols come from Kafka messages; strip line breaks so they can't forge extra log lines */
    private static String safe(String value) {
        return value == null ? null : value.replaceAll("[\\r\\n\\t]", "_");
    }
    private BigDecimal decimal(com.fasterxml.jackson.databind.JsonNode value) {
        return value.isNumber() || value.isTextual() ? new BigDecimal(value.asText()) : null;
    }
}
