package com.neueda.leap.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.neueda.leap.messaging.MarketPrice;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Read-only Alpaca market data. Credentials never leave the server. */
@Component
public class AlpacaMarketDataClient {
    private static final String DATA_URL = "https://data.alpaca.markets/v2/stocks";
    private static final ZoneId MARKET_ZONE = ZoneId.of("America/New_York");
    private final RestTemplate http;
    private final boolean configured;
    private final String feed;

    public AlpacaMarketDataClient(RestTemplateBuilder builder,
            @Value("${alpaca.enabled:true}") boolean enabled,
            @Value("${alpaca.key-id:}") String keyId,
            @Value("${alpaca.secret-key:}") String secretKey,
            @Value("${alpaca.feed:iex}") String feed) {
        if (!List.of("iex", "sip").contains(feed)) throw new IllegalArgumentException("Alpaca feed must be iex or sip");
        this.feed = feed;
        this.configured = enabled && !keyId.isBlank() && !secretKey.isBlank();
        this.http = builder.connectTimeout(Duration.ofSeconds(3)).readTimeout(Duration.ofSeconds(8))
                .defaultHeader("APCA-API-KEY-ID", keyId.trim())
                .defaultHeader("APCA-API-SECRET-KEY", secretKey.trim()).build();
    }

    public boolean isConfigured() { return configured; }
    public String feed() { return feed; }

    public List<Snapshot> snapshots(List<String> symbols) {
        if (symbols.isEmpty()) return List.of();
        JsonNode response = get(UriComponentsBuilder.fromUriString(DATA_URL + "/snapshots")
                .queryParam("symbols", String.join(",", symbols)).queryParam("feed", feed).build().encode().toUri());
        if (!response.isObject()) throw new DataUnavailable("Alpaca returned an invalid snapshot response.");
        List<Snapshot> results = new ArrayList<>();
        for (String symbol : symbols) {
            JsonNode raw = response.path(symbol);
            if (!raw.isObject()) continue;
            try {
                Snapshot snapshot = snapshot(symbol, raw);
                if (snapshot != null) results.add(snapshot);
            } catch (IllegalArgumentException ignored) {
                // A missing/invalid symbol must not prevent other instruments from updating.
            }
        }
        return List.copyOf(results);
    }

    public List<Bar> bars(String symbol, String timeframe, Instant now) {
        if (!List.of("1Min", "1Day").contains(timeframe)) throw new IllegalArgumentException("Timeframe must be 1Min or 1Day");
        Instant start = now.minus(Duration.ofDays(timeframe.equals("1Min") ? 7 : 60));
        int maxBars = timeframe.equals("1Min") ? 1000 : 30;
        // Historical SIP access on Basic is delayed; IEX is explicitly requested by default.
        Instant end = feed.equals("sip") ? now.minus(Duration.ofMinutes(15)) : now;
        List<Bar> results = new ArrayList<>();
        String page = null;
        for (int count = 0; count < 10; count++) {
            var uri = UriComponentsBuilder.fromUriString(DATA_URL + "/bars")
                    .queryParam("symbols", symbol).queryParam("timeframe", timeframe)
                    .queryParam("start", start.toString()).queryParam("end", end.toString())
                    .queryParam("feed", feed).queryParam("adjustment", "split")
                    .queryParam("sort", "desc").queryParam("limit", maxBars);
            if (page != null) uri.queryParam("page_token", page);
            JsonNode response = get(uri.build().encode().toUri());
            if (!response.path("bars").isObject()) throw new DataUnavailable("Alpaca returned an invalid chart response.");
            JsonNode rows = response.path("bars").path(symbol);
            if (rows.isArray()) for (JsonNode raw : rows) {
                try {
                    Bar bar = bar(raw);
                    if (bar != null) results.add(bar);
                } catch (IllegalArgumentException ignored) { }
            }
            if (results.size() >= maxBars) {
                return results.subList(0, maxBars).stream().sorted(Comparator.comparing(Bar::timestamp)).toList();
            }
            JsonNode next = response.path("next_page_token");
            if (next.isMissingNode() || next.isNull() || next.asText().isBlank()) {
                return results.stream().sorted(Comparator.comparing(Bar::timestamp)).toList();
            }
            if (next.asText().equals(page)) throw new DataUnavailable("Alpaca returned a repeated chart page.");
            page = next.asText();
        }
        throw new DataUnavailable("Alpaca chart pagination exceeded its limit.");
    }

    private JsonNode get(URI uri) {
        if (!configured) throw new DataUnavailable("Alpaca market data is not configured.");
        try {
            JsonNode response = http.getForObject(uri, JsonNode.class);
            if (response == null) throw new DataUnavailable("Alpaca returned an empty response.");
            return response;
        } catch (RestClientResponseException error) {
            // Do not propagate upstream bodies/headers or credentials into logs or API responses.
            String message = switch (error.getStatusCode().value()) {
                case 401 -> "Alpaca rejected the API credentials. Ask an administrator to check the connection.";
                case 403 -> "The Alpaca account cannot access this data feed. Check its subscription or use IEX.";
                case 429 -> "Alpaca's request limit was reached. Market data will retry shortly.";
                default -> "Alpaca market data is temporarily unavailable.";
            };
            throw new DataUnavailable(message);
        } catch (RestClientException error) {
            throw new DataUnavailable("Alpaca market data could not be reached. Retrying shortly.");
        }
    }

    private Snapshot snapshot(String symbol, JsonNode raw) {
        Bar daily = bar(raw.path("dailyBar"));
        Bar previous = bar(raw.path("prevDailyBar"));
        JsonNode trade = raw.path("latestTrade");
        BigDecimal price = positive(trade.path("p"));
        Instant timestamp = instant(trade.path("t"));
        if (price == null || timestamp == null) {
            Bar minute = bar(raw.path("minuteBar"));
            Bar fallback = minute != null ? minute : daily;
            if (fallback == null) return null;
            price = fallback.close();
            timestamp = fallback.timestamp();
        }
        BigDecimal previousClose = previous == null ? null : previous.close();
        // Before a new daily bar exists, yesterday's daily bar is the comparison close.
        if (daily != null && timestamp.atZone(MARKET_ZONE).toLocalDate()
                .isAfter(daily.timestamp().atZone(MARKET_ZONE).toLocalDate())) previousClose = daily.close();
        BigDecimal change = previousClose == null ? null : price.subtract(previousClose)
                .multiply(BigDecimal.valueOf(100)).divide(previousClose, 6, RoundingMode.HALF_UP);
        JsonNode quote = raw.path("latestQuote");
        BigDecimal bid = positive(quote.path("bp")), ask = positive(quote.path("ap"));
        Instant quoteTime = instant(quote.path("t"));
        MarketPrice marketPrice = bid == null || ask == null || quoteTime == null || ask.compareTo(bid) < 0
                ? null : new MarketPrice(symbol, bid, ask, price, quoteTime);
        return new Snapshot(symbol, price, timestamp, previousClose, change, daily, marketPrice);
    }

    private Bar bar(JsonNode raw) {
        if (!raw.isObject()) return null;
        Instant timestamp = instant(raw.path("t"));
        BigDecimal open = positive(raw.path("o")), high = positive(raw.path("h"));
        BigDecimal low = positive(raw.path("l")), close = positive(raw.path("c"));
        if (timestamp == null || open == null || high == null || low == null || close == null
                || high.compareTo(low) < 0 || raw.path("v").asLong(-1) < 0) return null;
        return new Bar(timestamp, open, high, low, close, raw.path("v").asLong());
    }

    private BigDecimal positive(JsonNode value) {
        if (!value.isNumber()) return null;
        BigDecimal result = value.decimalValue();
        return result.signum() > 0 ? result : null;
    }

    private Instant instant(JsonNode value) {
        if (!value.isTextual()) return null;
        try { return Instant.parse(value.asText()); }
        catch (java.time.format.DateTimeParseException ignored) { return null; }
    }

    public record Bar(Instant timestamp, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, long volume) { }
    public record Snapshot(String symbol, BigDecimal price, Instant timestamp, BigDecimal previousClose,
            BigDecimal changePercent, Bar dailyBar, MarketPrice quote) { }
    public static class DataUnavailable extends RuntimeException {
        public DataUnavailable(String message) { super(message); }
    }
}
