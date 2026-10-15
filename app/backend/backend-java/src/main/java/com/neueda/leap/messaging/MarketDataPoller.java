package com.neueda.leap.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class MarketDataPoller {
    private static final Logger logger = LoggerFactory.getLogger(MarketDataPoller.class);
    
    private final ExternalMarketDataClient marketDataClient;
    private final MarketDataProducer marketDataProducer;
    private final com.neueda.leap.services.AlpacaMarketDataService alpaca;
    private final EventType eventType = EventType.PRICE_UPDATE;
    
    @org.springframework.beans.factory.annotation.Autowired
    private com.neueda.leap.repositories.InstrumentsRepository instruments;

    @Value("${market.data.poll.symbols:*}")
    private String symbols;
    
    public MarketDataPoller(ExternalMarketDataClient marketDataClient, 
                           MarketDataProducer marketDataProducer,
                           com.neueda.leap.services.AlpacaMarketDataService alpaca) {
        this.marketDataClient = marketDataClient;
        this.marketDataProducer = marketDataProducer;
        this.alpaca = alpaca;
    }
    
    @Scheduled(fixedDelayString = "${market.data.poll.interval:10000}")
    public void pollMarketData() {
        if (symbols == null || symbols.isEmpty()) {
            logger.warn("No symbols configured for market data polling");
            return;
        }
        
        if (alpaca.isConfigured()) {
            var data = alpaca.overview();
            if (!data.message().isEmpty()) {
                logger.warn("Alpaca market data: {}", data.message());
                return;
            }
            var requested = java.util.Arrays.stream(symbols.split(",")).map(s -> s.trim().toUpperCase(java.util.Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
            for (var snapshot : data.snapshots()) {
                if ((requested.contains("*") || requested.contains(snapshot.symbol())) && snapshot.quote() != null) {
                    marketDataProducer.publishMarketData(createEventEnvelope(MarketDataPayload.fromMarketPrice(snapshot.quote())));
                }
            }
            return;
        }

        String[] symbolArray = symbols.trim().equals("*")
            ? instruments.findByTradable(true).stream().map(com.neueda.leap.models.Instrument::getSymbol).toArray(String[]::new)
            : symbols.split(",");
        
        for (String symbol : symbolArray) {
            try {
                pollAndPublishPrice(symbol.trim());
            } catch (Exception e) {
                logger.error("Error polling market data for symbol: {}", symbol.trim(), e);
            }
        }
    }
    
    private void pollAndPublishPrice(String symbol) {
        try {
            MarketPrice marketPrice = marketDataClient.getLatestMarketPrice(symbol);
            
            MarketDataPayload payload = MarketDataPayload.fromMarketPrice(marketPrice);
            
            EventEnvelope envelope = createEventEnvelope(payload);
            
            marketDataProducer.publishMarketData(envelope);
            
            logger.debug("Successfully polled and published market data for symbol: {}", symbol);
            
        } catch (Exception e) {
            logger.error("Failed to poll market data for symbol: {}", symbol, e);
            throw new RuntimeException("Failed to poll market data for " + symbol, e);
        }
    }
    
    private EventEnvelope createEventEnvelope(MarketDataPayload payload) {
        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("symbol", payload.symbol());
        payloadMap.put("bidPrice", payload.bidPrice());
        payloadMap.put("askPrice", payload.askPrice());
        payloadMap.put("lastPrice", payload.lastPrice());
        payloadMap.put("priceTimeStamp", payload.priceTimeStamp().toString());
        
        return new EventEnvelope(
            UUID.randomUUID(),
            eventType,
            Instant.now(),
            1,
            payloadMap
        );
    }
}
