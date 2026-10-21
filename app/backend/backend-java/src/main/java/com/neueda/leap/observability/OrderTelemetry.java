package com.neueda.leap.observability;

import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.models.Order;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Business metrics and span attributes for the order lifecycle, feeding the Grafana dashboards.
 *
 * Static like LogMaskingUtil, so services need no new dependency. The OpenTelemetry agent supplies
 * the implementation at runtime; without it every call is a no-op.
 *
 * Metric attributes stay low-cardinality (side, type, status): an order or account id would create a
 * time series per value. Per-order detail goes on spans, which can be searched by order.id in Tempo.
 * Account ids are left off spans as well, matching how logs mask them.
 */
public final class OrderTelemetry {

    /** Where in the lifecycle an order was rejected */
    public enum RejectStage { PRICING, EXECUTION, ADMIN }

    private static final String SCOPE = "com.neueda.leap.orders";

    private static final AttributeKey<String> ORDER_ID = AttributeKey.stringKey("order.id");
    private static final AttributeKey<String> SIDE = AttributeKey.stringKey("order.side");
    private static final AttributeKey<String> TYPE = AttributeKey.stringKey("order.type");
    private static final AttributeKey<String> STATUS = AttributeKey.stringKey("order.status");
    private static final AttributeKey<String> STAGE = AttributeKey.stringKey("reject.stage");
    private static final AttributeKey<String> SYMBOL = AttributeKey.stringKey("instrument.symbol");

    // Engine fills take about a second; limit orders can wait minutes
    private static final List<Double> FILL_SECONDS_BUCKETS =
            List.of(0.1, 0.25, 0.5, 1.0, 2.0, 5.0, 10.0, 30.0, 60.0, 300.0, 1800.0);

    private static final AtomicReference<Instruments> instruments = new AtomicReference<>();

    private OrderTelemetry() {
    }

    /** A new order was accepted for processing (idempotent replays are not counted) */
    public static void placed(Order order) {
        tagSpan(order);
        Attributes attributes = Attributes.of(SIDE, side(order), TYPE, type(order));
        afterCommit(() -> instruments().placed.add(1, attributes));
    }

    /** The order filled; records the outcome, time to fill and traded value */
    public static void filled(Order order) {
        tagSpan(order);
        Attributes base = Attributes.of(SIDE, side(order), TYPE, type(order));
        Attributes outcome = base.toBuilder().put(STATUS, OrderStatus.FILLED.name()).build();
        Double seconds = order.getCreatedAt() == null ? null
                : Duration.between(order.getCreatedAt(), LocalDateTime.now()).toMillis() / 1000.0;
        double value = order.getExecutionPrice() == null ? 0
                : order.getExecutionPrice().doubleValue() * order.getQuantity();
        Attributes valueAttributes = Attributes.of(SIDE, side(order));
        afterCommit(() -> {
            Instruments i = instruments();
            i.completed.add(1, outcome);
            if (seconds != null && seconds >= 0) {
                i.fillDuration.record(seconds, base);
            }
            i.tradedValue.add(value, valueAttributes);
        });
    }

    public static void rejected(Order order, RejectStage stage) {
        tagSpan(order);
        Attributes attributes = Attributes.of(SIDE, side(order), TYPE, type(order),
                STATUS, OrderStatus.REJECTED.name(), STAGE, stage.name().toLowerCase(Locale.ROOT));
        afterCommit(() -> instruments().completed.add(1, attributes));
    }

    public static void cancelled(Order order) {
        tagSpan(order);
        Attributes attributes = Attributes.of(SIDE, side(order), TYPE, type(order),
                STATUS, OrderStatus.CANCELLED.name());
        afterCommit(() -> instruments().completed.add(1, attributes));
    }

    /** Lets tests record into an in-memory SDK; production uses the agent's global instance */
    static void use(OpenTelemetry openTelemetry) {
        instruments.set(new Instruments(openTelemetry.getMeter(SCOPE)));
    }

    private static Instruments instruments() {
        Instruments current = instruments.get();
        if (current == null) {
            Instruments created = new Instruments(GlobalOpenTelemetry.getMeter(SCOPE));
            if (instruments.compareAndSet(null, created)) {
                return created;
            }
            current = instruments.get();
        }
        return current;
    }

    /** Makes the request or Kafka span findable by order in Tempo */
    private static void tagSpan(Order order) {
        Span span = Span.current();
        if (order.getId() != null) {
            span.setAttribute(ORDER_ID, order.getId().toString());
        }
        span.setAttribute(SIDE, side(order));
        span.setAttribute(TYPE, type(order));
        if (order.getInstrument() != null) {
            span.setAttribute(SYMBOL, order.getInstrument().getSymbol());
        }
    }

    /** Counts only changes that commit, so a rolled-back fill or cancel is never recorded */
    private static void afterCommit(Runnable record) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    record.run();
                }
            });
        } else {
            record.run();
        }
    }

    private static String side(Order order) {
        return order.getSide() == null ? "UNKNOWN" : order.getSide().name();
    }

    private static String type(Order order) {
        return order.getOrderType() == null ? "UNKNOWN" : order.getOrderType().name();
    }

    private static final class Instruments {
        final LongCounter placed;
        final LongCounter completed;
        final DoubleHistogram fillDuration;
        final DoubleCounter tradedValue;

        Instruments(Meter meter) {
            placed = meter.counterBuilder("seaj.orders.placed")
                    .setDescription("Orders accepted for processing")
                    .setUnit("{order}")
                    .build();
            completed = meter.counterBuilder("seaj.orders.completed")
                    .setDescription("Orders that reached a final status: filled, rejected or cancelled")
                    .setUnit("{order}")
                    .build();
            fillDuration = meter.histogramBuilder("seaj.order.fill.duration")
                    .setDescription("Time from an order being placed to being filled")
                    .setUnit("s")
                    .setExplicitBucketBoundariesAdvice(FILL_SECONDS_BUCKETS)
                    .build();
            tradedValue = meter.counterBuilder("seaj.orders.traded.value")
                    .ofDoubles()
                    .setDescription("Notional value of filled orders (quantity x execution price)")
                    .setUnit("{USD}")
                    .build();
        }
    }
}
