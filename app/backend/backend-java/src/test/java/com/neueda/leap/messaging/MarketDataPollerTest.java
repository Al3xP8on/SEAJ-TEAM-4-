package com.neueda.leap.messaging;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;

class MarketDataPollerTest {
    private final ExternalMarketDataClient client = mock(ExternalMarketDataClient.class);
    private final MarketDataProducer producer = mock(MarketDataProducer.class);
    private final MarketDataPoller poller = new MarketDataPoller(client, producer);
    @Test void skipsPollingWithoutConfiguredSymbols() {
        poller.pollMarketData();
        ReflectionTestUtils.setField(poller, "symbols", "");
        poller.pollMarketData();
        verifyNoInteractions(client, producer);
    }
    @Test void trimsSymbolsAndPublishesCompletePriceEnvelope() {
        ReflectionTestUtils.setField(poller, "symbols", " AAPL ");
        MarketPrice price = new MarketPrice("AAPL", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, Instant.now());
        when(client.getLatestMarketPrice("AAPL")).thenReturn(price);
        poller.pollMarketData();
        verify(producer).publishMarketData(argThat(event -> event.eventId() != null && event.timestamp() != null
                && event.version() == 1 && event.eventType() == EventType.PRICE_UPDATE
                && event.payload().get("symbol").equals("AAPL")
                && event.payload().get("bidPrice").equals(price.bidPrice())
                && event.payload().get("askPrice").equals(price.askPrice())
                && event.payload().get("lastPrice").equals(price.lastPrice())
                && event.payload().get("priceTimeStamp").equals(price.priceTimeStamp())));
    }
    @Test void continuesPollingAfterOneSymbolFails() {
        ReflectionTestUtils.setField(poller, "symbols", "AAPL,MSFT");
        when(client.getLatestMarketPrice("AAPL")).thenThrow(new IllegalStateException("offline"));
        when(client.getLatestMarketPrice("MSFT")).thenReturn(new MarketPrice("MSFT", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, Instant.now()));
        assertDoesNotThrow(poller::pollMarketData);
        verify(producer).publishMarketData(argThat(event -> event.payload().get("symbol").equals("MSFT")));
    }
}
