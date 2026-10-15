package com.neueda.leap.messaging;

import com.neueda.leap.enums.*;
import com.neueda.leap.models.Order;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.services.MarketPriceService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/** Resting limit orders wait for a marketable quote. Unsettled sends are retried after 30s. */
@Component
public class PendingOrderDispatcher {
    private final OrderRepository orders;
    private final MarketPriceService prices;
    private final OrderEventPublisher publisher;
    private final ConcurrentHashMap<UUID, Instant> sent = new ConcurrentHashMap<>();

    public PendingOrderDispatcher(OrderRepository orders, MarketPriceService prices, OrderEventPublisher publisher) {
        this.orders = orders;
        this.prices = prices;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${trading.orders.dispatch-interval:2000}")
    public void dispatchPendingOrders() {
        var pending = orders.findByStatus(OrderStatus.PENDING);
        var ids = pending.stream().map(Order::getId).collect(java.util.stream.Collectors.toSet());
        sent.keySet().retainAll(ids);
        for (Order order : pending) {
            try {
                Instant last = sent.get(order.getId());
                if (last != null && last.isAfter(Instant.now().minusSeconds(30))) continue;
                var quote = prices.getLatestPrice(order.getInstrument().getSymbol());
                if (quote.isEmpty()) continue;
                BigDecimal price = (order.getSide() == OrderSide.BUY ? quote.get().askPrice() : quote.get().bidPrice())
                    .setScale(2, order.getSide() == OrderSide.BUY ? java.math.RoundingMode.CEILING : java.math.RoundingMode.FLOOR);
                if (price.signum() <= 0) continue;
                if (order.getOrderType() == OrderType.LIMIT
                    && (order.getSide() == OrderSide.BUY ? price.compareTo(order.getPrice()) > 0
                                                        : price.compareTo(order.getPrice()) < 0)) continue;
                publisher.publish(new OrderEvent(order.getId(), order.getAccount().getAccountId(),
                    order.getInstrument().getSymbol(), order.getSide(), Math.toIntExact(order.getQuantity()),
                    price, Instant.now()));
                sent.put(order.getId(), Instant.now());
            } catch (Exception e) {
                LoggerFactory.getLogger(getClass()).warn("Could not dispatch order {}", order.getId(), e);
            }
        }
    }
}
