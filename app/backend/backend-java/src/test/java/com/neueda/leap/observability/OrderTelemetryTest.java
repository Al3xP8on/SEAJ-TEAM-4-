package com.neueda.leap.observability;

import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.enums.OrderType;
import com.neueda.leap.models.Account;
import com.neueda.leap.models.Instrument;
import com.neueda.leap.models.Order;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.PointData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OrderTelemetryTest {

    private InMemoryMetricReader metrics;
    private InMemorySpanExporter spans;
    private OpenTelemetrySdk sdk;

    @BeforeEach
    void setUp() {
        // A fresh SDK per test so cumulative counters start from zero
        metrics = InMemoryMetricReader.create();
        spans = InMemorySpanExporter.create();
        sdk = OpenTelemetrySdk.builder()
                .setMeterProvider(SdkMeterProvider.builder().registerMetricReader(metrics).build())
                .setTracerProvider(SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(spans)).build())
                .build();
        OrderTelemetry.use(sdk);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        sdk.close();
    }

    @Test
    void countsPlacedOrdersBySideAndType() {
        OrderTelemetry.placed(order(OrderSide.BUY, OrderType.MARKET, LocalDateTime.now()));
        OrderTelemetry.placed(order(OrderSide.BUY, OrderType.MARKET, LocalDateTime.now()));
        OrderTelemetry.placed(order(OrderSide.SELL, OrderType.LIMIT, LocalDateTime.now()));

        assertThat(sum("seaj.orders.placed", Attributes.of(key("order.side"), "BUY", key("order.type"), "MARKET"))).isEqualTo(2);
        assertThat(sum("seaj.orders.placed", Attributes.of(key("order.side"), "SELL", key("order.type"), "LIMIT"))).isEqualTo(1);
    }

    @Test
    void recordsFillOutcomeTimeToFillAndTradedValue() {
        Order order = order(OrderSide.BUY, OrderType.LIMIT, LocalDateTime.now().minusSeconds(3));
        order.setExecutionPrice(new BigDecimal("150.25"));

        OrderTelemetry.filled(order);

        assertThat(sum("seaj.orders.completed", Attributes.of(
                key("order.side"), "BUY", key("order.type"), "LIMIT", key("order.status"), "FILLED"))).isEqualTo(1);
        HistogramPointData fill = (HistogramPointData) point("seaj.order.fill.duration",
                Attributes.of(key("order.side"), "BUY", key("order.type"), "LIMIT"));
        assertThat(fill.getCount()).isEqualTo(1);
        assertThat(fill.getSum()).isBetween(3.0, 10.0);
        // 10 x 150.25
        assertThat(doubleSum("seaj.orders.traded.value", Attributes.of(key("order.side"), "BUY"))).isEqualTo(1502.5);
    }

    @Test
    void labelsRejectionsWithTheStageAndCountsCancellations() {
        OrderTelemetry.rejected(order(OrderSide.SELL, OrderType.MARKET, LocalDateTime.now()), OrderTelemetry.RejectStage.PRICING);
        OrderTelemetry.cancelled(order(OrderSide.BUY, OrderType.LIMIT, LocalDateTime.now()));

        assertThat(sum("seaj.orders.completed", Attributes.of(key("order.side"), "SELL", key("order.type"), "MARKET",
                key("order.status"), "REJECTED", key("reject.stage"), "pricing"))).isEqualTo(1);
        assertThat(sum("seaj.orders.completed", Attributes.of(key("order.side"), "BUY", key("order.type"), "LIMIT",
                key("order.status"), "CANCELLED"))).isEqualTo(1);
    }

    @Test
    void waitsForTheTransactionToCommitBeforeCounting() {
        TransactionSynchronizationManager.initSynchronization();

        OrderTelemetry.placed(order(OrderSide.BUY, OrderType.MARKET, LocalDateTime.now()));
        assertThat(metrics.collectAllMetrics()).isEmpty();

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(sum("seaj.orders.placed", Attributes.of(key("order.side"), "BUY", key("order.type"), "MARKET"))).isEqualTo(1);
    }

    @Test
    void tagsTheCurrentSpanSoTheOrderCanBeFoundInTraces() {
        Order order = order(OrderSide.SELL, OrderType.LIMIT, LocalDateTime.now());
        var span = sdk.getTracer("test").spanBuilder("POST /api/v1/orders").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            OrderTelemetry.placed(order);
        } finally {
            span.end();
        }

        SpanData data = spans.getFinishedSpanItems().get(0);
        assertThat(data.getAttributes().get(key("order.id"))).isEqualTo(order.getId().toString());
        assertThat(data.getAttributes().get(key("instrument.symbol"))).isEqualTo("AAPL");
        assertThat(data.getAttributes().get(key("order.side"))).isEqualTo("SELL");
    }

    private static Order order(OrderSide side, OrderType type, LocalDateTime createdAt) {
        Order order = new Order(UUID.randomUUID(), mock(Account.class), new Instrument("AAPL", "Apple Inc.", "EQUITY", "USD", true),
                10, new BigDecimal("150.00"), side, "key-" + UUID.randomUUID(), OrderStatus.PENDING, createdAt);
        order.setOrderType(type);
        return order;
    }

    private static AttributeKey<String> key(String name) {
        return AttributeKey.stringKey(name);
    }

    private long sum(String metric, Attributes attributes) {
        return ((io.opentelemetry.sdk.metrics.data.LongPointData) point(metric, attributes)).getValue();
    }

    private double doubleSum(String metric, Attributes attributes) {
        return ((io.opentelemetry.sdk.metrics.data.DoublePointData) point(metric, attributes)).getValue();
    }

    private PointData point(String metric, Attributes attributes) {
        Collection<MetricData> collected = metrics.collectAllMetrics();
        return collected.stream()
                .filter(m -> m.getName().equals(metric))
                .flatMap(m -> m.getData().getPoints().stream())
                .filter(p -> p.getAttributes().equals(attributes))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No " + metric + " point with " + attributes + " in " + collected));
    }
}
