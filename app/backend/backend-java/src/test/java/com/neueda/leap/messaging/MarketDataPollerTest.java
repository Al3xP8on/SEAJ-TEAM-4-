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
    private final com.neueda.leap.services.AlpacaMarketDataService alpaca = mock(com.neueda.leap.services.AlpacaMarketDataService.class);
    private final MarketDataPoller poller = new MarketDataPoller(client, producer, alpaca);
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
                && event.payload().get("priceTimeStamp").equals(price.priceTimeStamp().toString())));
    }
    @Test void continuesPollingAfterOneSymbolFails() {
        ReflectionTestUtils.setField(poller, "symbols", "AAPL,MSFT");
        when(client.getLatestMarketPrice("AAPL")).thenThrow(new IllegalStateException("offline"));
        when(client.getLatestMarketPrice("MSFT")).thenReturn(new MarketPrice("MSFT", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, Instant.now()));
        assertDoesNotThrow(poller::pollMarketData);
        verify(producer).publishMarketData(argThat(event -> event.payload().get("symbol").equals("MSFT")));
    }
    @Test void pollsAllTradableDatabaseInstrumentsWhenConfiguredWithWildcard() {
        var instruments = mock(com.neueda.leap.repositories.InstrumentsRepository.class);
        ReflectionTestUtils.setField(poller, "instruments", instruments);
        ReflectionTestUtils.setField(poller, "symbols", "*");
        when(instruments.findByTradable(true)).thenReturn(java.util.List.of(new com.neueda.leap.models.Instrument("NVDA", "Nvidia", "EQUITY", "USD", true)));
        when(client.getLatestMarketPrice("NVDA")).thenReturn(new MarketPrice("NVDA", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, Instant.now()));
        poller.pollMarketData();
        verify(producer).publishMarketData(argThat(event -> event.payload().get("symbol").equals("NVDA")));
    }
    @Test void publishesAlpacaQuotesWithTheirOriginalExchangeTime() {
        ReflectionTestUtils.setField(poller, "symbols", "AAPL");
        Instant quoteTime = Instant.now().minusSeconds(20);
        MarketPrice price = new MarketPrice("AAPL", new BigDecimal("209.31"), new BigDecimal("209.33"), new BigDecimal("209.32"), quoteTime);
        var snapshot = new com.neueda.leap.services.AlpacaMarketDataClient.Snapshot("AAPL", price.lastPrice(), quoteTime, null, null, null, price);
        when(alpaca.isConfigured()).thenReturn(true);
        when(alpaca.overview()).thenReturn(new com.neueda.leap.services.AlpacaMarketDataService.Overview(true, "iex", Instant.now(), "", java.util.List.of(snapshot), java.util.List.of()));
        poller.pollMarketData();
        verifyNoInteractions(client);
        verify(producer).publishMarketData(argThat(event -> event.payload().get("bidPrice").equals(price.bidPrice())
                && event.payload().get("priceTimeStamp").equals(quoteTime.toString())));
    }
    @Test void doesNotFallBackToSimulatedQuotesWhenAlpacaFails() {
        ReflectionTestUtils.setField(poller, "symbols", "AAPL");
        when(alpaca.isConfigured()).thenReturn(true);
        when(alpaca.overview()).thenReturn(new com.neueda.leap.services.AlpacaMarketDataService.Overview(true, "iex", null, "Alpaca unavailable", java.util.List.of(), java.util.List.of()));
        poller.pollMarketData();
        verifyNoInteractions(client, producer);
    }
    @Test void reusesTheAlpacaUniverseInsteadOfQueryingInstrumentsAgain() {
        var instruments = mock(com.neueda.leap.repositories.InstrumentsRepository.class);
        ReflectionTestUtils.setField(poller, "instruments", instruments);
        ReflectionTestUtils.setField(poller, "symbols", "*");
        when(alpaca.isConfigured()).thenReturn(true);
        var quote = new MarketPrice("AAPL", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, Instant.now());
        var snapshot = new com.neueda.leap.services.AlpacaMarketDataClient.Snapshot("AAPL", BigDecimal.ONE, Instant.now(), null, null, null, quote);
        when(alpaca.overview()).thenReturn(new com.neueda.leap.services.AlpacaMarketDataService.Overview(true, "iex", Instant.now(), "", java.util.List.of(snapshot), java.util.List.of()));
        poller.pollMarketData();
        verifyNoInteractions(instruments, client);
        verify(producer).publishMarketData(any());
    }
}
