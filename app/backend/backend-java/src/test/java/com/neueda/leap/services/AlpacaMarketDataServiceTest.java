package com.neueda.leap.services;

import com.neueda.leap.models.Instrument;
import com.neueda.leap.repositories.InstrumentsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AlpacaMarketDataServiceTest {
    private final AlpacaMarketDataClient client = mock(AlpacaMarketDataClient.class);
    private final InstrumentsRepository instruments = mock(InstrumentsRepository.class);
    private final Clock clock = mock(Clock.class);
    private final AlpacaMarketDataService service = new AlpacaMarketDataService(client, instruments, clock);
    private final Instant now = Instant.parse("2026-10-14T15:00:00Z");
    private final Instrument apple = new Instrument("AAPL", "Apple", "Equity", "USD", true);
    private final Instrument synthetic = new Instrument("UST10", "US Treasury", "Bond", "USD", true);
    private final AlpacaMarketDataClient.Snapshot snapshot = new AlpacaMarketDataClient.Snapshot("AAPL", BigDecimal.TEN, now, BigDecimal.ONE, BigDecimal.TEN, null, null);

    @BeforeEach void setup() {
        when(client.isConfigured()).thenReturn(true);
        when(client.feed()).thenReturn("iex");
        when(clock.instant()).thenReturn(now);
        when(instruments.findByTradable(true)).thenReturn(List.of(apple, synthetic));
        when(instruments.findBySymbol("AAPL")).thenReturn(Optional.of(apple));
        when(instruments.findBySymbol("UST10")).thenReturn(Optional.of(synthetic));
        when(client.snapshots(List.of("AAPL"))).thenReturn(List.of(snapshot));
    }
    @Test void batchesSupportedSymbolsAndSharesSnapshotCacheAcrossRequests() {
        assertEquals(List.of(snapshot), service.overview().snapshots());
        assertEquals(List.of("UST10"), service.overview().unsupportedSymbols());
        verify(client, times(1)).snapshots(List.of("AAPL"));
        when(clock.instant()).thenReturn(now.plusSeconds(9));
        service.overview();
        verify(client, times(1)).snapshots(List.of("AAPL"));
        when(clock.instant()).thenReturn(now.plusSeconds(10));
        service.overview();
        verify(client, times(2)).snapshots(List.of("AAPL"));
    }
    @Test void returnsConfigurationStateWithoutCallingAlpaca() {
        when(client.isConfigured()).thenReturn(false);
        var result = service.overview();
        assertFalse(result.configured());
        assertTrue(result.message().contains("not connected"));
        verify(client, never()).snapshots(any());
        verifyNoInteractions(instruments);
    }
    @Test void preservesTheLastGoodPricesAndTimestampDuringAnOutage() {
        var original = service.overview();
        when(clock.instant()).thenReturn(now.plusSeconds(11));
        when(client.snapshots(any())).thenThrow(new AlpacaMarketDataClient.DataUnavailable("Alpaca offline"));
        var failed = service.overview();
        assertEquals(original.snapshots(), failed.snapshots());
        assertEquals(original.updatedAt(), failed.updatedAt());
        assertEquals("Alpaca offline", failed.message());
        service.overview();
        verify(client, times(2)).snapshots(any());
    }
    @Test void reportsAnInitialOutageWithoutFabricatedPrices() {
        when(client.snapshots(any())).thenThrow(new AlpacaMarketDataClient.DataUnavailable("Alpaca offline"));
        var result = service.overview();
        assertNull(result.updatedAt());
        assertTrue(result.snapshots().isEmpty());
        assertEquals("Alpaca offline", result.message());
    }
    @Test void sharesChartCacheForOneMinuteAndSeparatesTimeframes() {
        var bar = new AlpacaMarketDataClient.Bar(now, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN, 100);
        when(client.bars("AAPL", "1Min", now)).thenReturn(List.of(bar));
        when(client.bars("AAPL", "1Day", now)).thenReturn(List.of(bar));
        assertEquals(List.of(bar), service.bars(" aapl ", "1Min").bars());
        service.bars("AAPL", "1Min");
        service.bars("AAPL", "1Day");
        verify(client, times(1)).bars("AAPL", "1Min", now);
        verify(client, times(1)).bars("AAPL", "1Day", now);
        when(clock.instant()).thenReturn(now.plusSeconds(60));
        when(client.bars("AAPL", "1Min", now.plusSeconds(60))).thenReturn(List.of(bar));
        service.bars("AAPL", "1Min");
        verify(client).bars("AAPL", "1Min", now.plusSeconds(60));
    }
    @Test void explainsUnsupportedInstrumentsAndRejectsUnknownSymbolsOrTimeframes() {
        assertTrue(service.bars("UST10", "1Min").message().contains("unavailable"));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> service.bars("MISSING", "1Min")).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class, () -> service.bars("AAPL", "bad")).getStatusCode());
        verify(client, never()).bars(any(), any(), any());
    }
    @Test void reusesDailyHistoryForFiveMinutes() {
        var bar = new AlpacaMarketDataClient.Bar(now, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN, 100);
        when(client.bars(eq("AAPL"), eq("1Day"), any())).thenReturn(List.of(bar));
        service.bars("AAPL", "1Day");
        when(clock.instant()).thenReturn(now.plusSeconds(299));
        assertEquals(List.of(bar), service.bars("AAPL", "1Day").bars());
        verify(client, times(1)).bars(eq("AAPL"), eq("1Day"), any());
        when(clock.instant()).thenReturn(now.plusSeconds(300));
        service.bars("AAPL", "1Day");
        verify(client, times(2)).bars(eq("AAPL"), eq("1Day"), any());
    }
    @Test void returnsAnUnavailableResponseForFailedCharts() {
        when(client.bars(any(), any(), any())).thenThrow(new AlpacaMarketDataClient.DataUnavailable("Alpaca offline"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class, () -> service.bars("AAPL", "1Min")).getStatusCode());
    }
    @Test void sharesSixHourHistoryForFiveMinutesWithoutReusingDailyBars() {
        var weekly = new AlpacaMarketDataClient.Bar(now, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN, 100);
        when(client.bars(eq("AAPL"), eq("6Hour"), any())).thenReturn(List.of(weekly));
        when(client.bars(eq("AAPL"), eq("1Day"), any())).thenReturn(List.of());
        assertEquals(List.of(weekly), service.bars("AAPL", "6Hour").bars());
        assertTrue(service.bars("AAPL", "1Day").bars().isEmpty());
        when(clock.instant()).thenReturn(now.plusSeconds(299));
        assertEquals(List.of(weekly), service.bars("AAPL", "6Hour").bars());
        verify(client, times(1)).bars(eq("AAPL"), eq("6Hour"), any());
        when(clock.instant()).thenReturn(now.plusSeconds(300));
        service.bars("AAPL", "6Hour");
        verify(client, times(2)).bars(eq("AAPL"), eq("6Hour"), any());
    }
}
