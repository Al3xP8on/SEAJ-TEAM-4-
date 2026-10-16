package com.neueda.leap.messaging;

import com.neueda.leap.services.AlpacaMarketDataService;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;

/** One shared Alpaca request every ten seconds; all executable quotes travel through Kafka. */
@Component
public class MarketDataPoller {
    private final MarketDataProducer producer;
    private final AlpacaMarketDataService alpaca;
    @Value("${market.data.poll.symbols:*}") private String symbols;

    public MarketDataPoller(MarketDataProducer producer, AlpacaMarketDataService alpaca) {
        this.producer = producer;
        this.alpaca = alpaca;
    }
    @Scheduled(fixedDelayString = "${market.data.poll.interval:10000}")
    public void pollMarketData() {
        if (symbols == null || symbols.isBlank() || !alpaca.isConfigured()) return;
        var data = alpaca.overview();
        if (!data.message().isEmpty()) {
            LoggerFactory.getLogger(getClass()).warn("Alpaca market data: {}", data.message());
            return;
        }
        var requested = Arrays.stream(symbols.split(",")).map(s -> s.trim().toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        for (var snapshot : data.snapshots()) {
            if (!(requested.contains("*") || requested.contains(snapshot.symbol())) || snapshot.quote() == null) continue;
            var quote = snapshot.quote();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("symbol", quote.symbol());
            payload.put("bidPrice", quote.bidPrice());
            payload.put("askPrice", quote.askPrice());
            payload.put("lastPrice", quote.lastPrice());
            payload.put("priceTimeStamp", quote.priceTimeStamp().toString());
            payload.put("source", "ALPACA_" + data.feed().toUpperCase(Locale.ROOT));
            producer.publishMarketData(new EventEnvelope(UUID.randomUUID(), EventType.PRICE_UPDATE,
                    Instant.now(), 1, payload));
        }
    }
}
