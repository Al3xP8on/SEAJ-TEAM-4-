package com.neueda.leap.messaging;

import com.neueda.leap.services.AlpacaMarketDataClient;
import com.neueda.leap.services.AlpacaMarketDataService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import static org.mockito.Mockito.*;

class MarketDataPollerTest {
    final MarketDataProducer producer = mock(MarketDataProducer.class);
    final AlpacaMarketDataService alpaca = mock(AlpacaMarketDataService.class);
    final MarketDataPoller poller = new MarketDataPoller(producer, alpaca);
    @Test void noCredentialsNeverProducesDemoExecutionQuotes() {
        ReflectionTestUtils.setField(poller, "symbols", "*");
        poller.pollMarketData();
        verifyNoInteractions(producer);
        verify(alpaca, never()).overview();
    }
    @Test void publishesOriginalAlpacaQuoteTimeAndSourceAndFiltersSymbols() {
        ReflectionTestUtils.setField(poller, "symbols", " aapl ");
        when(alpaca.isConfigured()).thenReturn(true);
        Instant timestamp = Instant.now().minusSeconds(20);
        MarketPrice quote = new MarketPrice("AAPL", new BigDecimal("100.10"), new BigDecimal("100.20"), BigDecimal.TEN, timestamp);
        var aapl = new AlpacaMarketDataClient.Snapshot("AAPL", BigDecimal.TEN, timestamp, null, null, null, quote);
        var msft = new AlpacaMarketDataClient.Snapshot("MSFT", BigDecimal.TEN, timestamp, null, null, null,
            new MarketPrice("MSFT", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, timestamp));
        when(alpaca.overview()).thenReturn(new AlpacaMarketDataService.Overview(true, "iex", Instant.now(), "", List.of(aapl, msft), List.of()));
        poller.pollMarketData();
        verify(producer).publishMarketData(argThat(e -> e.payload().get("source").equals("ALPACA_IEX")
            && e.payload().get("symbol").equals("AAPL") && e.payload().get("priceTimeStamp").equals(timestamp.toString())));
    }
    @Test void upstreamOutagePublishesNoFabricatedFallback() {
        ReflectionTestUtils.setField(poller, "symbols", "*");
        when(alpaca.isConfigured()).thenReturn(true);
        when(alpaca.overview()).thenReturn(new AlpacaMarketDataService.Overview(true, "iex", null, "offline", List.of(), List.of()));
        poller.pollMarketData();
        verifyNoInteractions(producer);
    }
}
