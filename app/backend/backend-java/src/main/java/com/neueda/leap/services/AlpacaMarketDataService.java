package com.neueda.leap.services;

import com.neueda.leap.models.Instrument;
import com.neueda.leap.repositories.InstrumentsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import static com.neueda.leap.services.AlpacaMarketDataClient.*;

/** Shared caches keep external requests independent of the number of logged-in users. */
@Service
public class AlpacaMarketDataService {
    private final AlpacaMarketDataClient client;
    private final InstrumentsRepository instruments;
    private final Clock clock;
    private Overview overview;
    private Instant lastAttempt;
    private final ConcurrentHashMap<String, CachedBars> charts = new ConcurrentHashMap<>();

    @Autowired
    public AlpacaMarketDataService(AlpacaMarketDataClient client, InstrumentsRepository instruments) {
        this(client, instruments, Clock.systemUTC());
    }
    AlpacaMarketDataService(AlpacaMarketDataClient client, InstrumentsRepository instruments, Clock clock) {
        this.client = client;
        this.instruments = instruments;
        this.clock = clock;
    }
    public boolean isConfigured() { return client.isConfigured(); }

    /** Index funds first, then popular stocks; labels name the index the fund tracks */
    private static final Map<String, String> TICKER = tickerSymbols();

    private static Map<String, String> tickerSymbols() {
        Map<String, String> symbols = new LinkedHashMap<>();
        symbols.put("SPY", "S&P 500");
        symbols.put("QQQ", "Nasdaq-100");
        for (String stock : List.of("AAPL", "MSFT", "NVDA", "AMZN", "GOOGL", "META", "TSLA", "JPM")) symbols.put(stock, stock);
        return symbols;
    }

    /**
     * Prices for the public landing page ticker, taken from the shared overview cache so it adds no
     * Alpaca requests. Only symbols with a current price are included; nothing account-related.
     */
    public List<TickerQuote> ticker() { return tickerFrom(overview().snapshots()); }

    static List<TickerQuote> tickerFrom(List<Snapshot> snapshots) {
        Map<String, Snapshot> bySymbol = new LinkedHashMap<>();
        snapshots.forEach(snapshot -> bySymbol.putIfAbsent(snapshot.symbol(), snapshot));
        return TICKER.entrySet().stream().map(entry -> {
            Snapshot snapshot = bySymbol.get(entry.getKey());
            return snapshot == null || snapshot.price() == null ? null
                    : new TickerQuote(entry.getKey(), entry.getValue(), snapshot.price(), snapshot.changePercent());
        }).filter(Objects::nonNull).toList();
    }

    public synchronized Overview overview() {
        if (!isConfigured()) return new Overview(false, client.feed(), null,
                "Alpaca market data is not connected yet.", List.of(), List.of());
        Instant now = clock.instant();
        if (lastAttempt != null && Duration.between(lastAttempt, now).compareTo(Duration.ofSeconds(10)) < 0) return overview;
        List<Instrument> tradable = instruments.findByTradable(true);
        List<String> symbols = tradable.stream().filter(this::supports).map(Instrument::getSymbol).sorted().toList();
        List<String> unsupported = tradable.stream().filter(instrument -> !supports(instrument)).map(Instrument::getSymbol).sorted().toList();
        lastAttempt = now;
        try {
            List<Snapshot> snapshots = client.snapshots(symbols);
            overview = new Overview(true, client.feed(), now, "", snapshots, unsupported);
        } catch (DataUnavailable error) {
            overview = new Overview(true, client.feed(), overview == null ? null : overview.updatedAt(),
                    error.getMessage(), overview == null ? List.of() : overview.snapshots(), unsupported);
        }
        return overview;
    }

    public Chart bars(String rawSymbol, String timeframe) {
        if (!List.of("1Min", "1Day").contains(timeframe)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Timeframe must be 1Min or 1Day");
        String symbol = rawSymbol.trim().toUpperCase(Locale.ROOT);
        Instrument instrument = instruments.findBySymbol(symbol).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Instrument not found"));
        if (!supports(instrument)) return new Chart(symbol, client.feed(), timeframe, List.of(), "Alpaca stock data is unavailable for this instrument.");
        if (!isConfigured()) return new Chart(symbol, client.feed(), timeframe, List.of(), "Alpaca market data is not connected yet.");
        Instant now = clock.instant();
        Duration cacheTime = Duration.ofMinutes(timeframe.equals("1Day") ? 5 : 1);
        try {
            CachedBars result = charts.compute(symbol + ":" + timeframe, (key, previous) -> {
                if (previous != null && Duration.between(previous.fetchedAt(), now).compareTo(cacheTime) < 0) return previous;
                return new CachedBars(now, client.bars(symbol, timeframe, now));
            });
            return new Chart(symbol, client.feed(), timeframe, result.bars(), "");
        } catch (DataUnavailable error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, error.getMessage());
        }
    }

    private boolean supports(Instrument instrument) {
        String assetClass = instrument.getAssetClass().toUpperCase(Locale.ROOT);
        return instrument.isTradable() && "USD".equalsIgnoreCase(instrument.getCurrency())
                && (List.of("EQUITY", "ETF", "COMMODITY").contains(assetClass)
                || instrument.getName().toUpperCase(Locale.ROOT).contains("ETF"));
    }

    public record Overview(boolean configured, String feed, Instant updatedAt, String message,
            List<Snapshot> snapshots, List<String> unsupportedSymbols) { }
    public record Chart(String symbol, String feed, String timeframe, List<Bar> bars, String message) { }
    public record TickerQuote(String symbol, String label, BigDecimal price, BigDecimal changePercent) { }
    private record CachedBars(Instant fetchedAt, List<Bar> bars) { }
}
