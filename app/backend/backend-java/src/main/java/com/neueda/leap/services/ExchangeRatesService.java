package com.neueda.leap.services;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;

@Service
public class ExchangeRatesService {
    static final String URL = "https://api.frankfurter.dev/v1/latest?base=USD&symbols=GBP,EUR,CHF,JPY,CAD,AUD";
    private final RestTemplate http;
    private final Clock clock;
    private Rates cached;
    private Instant lastAttempt;
    @org.springframework.beans.factory.annotation.Autowired
    public ExchangeRatesService(RestTemplateBuilder builder) {
        this(builder.connectTimeout(Duration.ofSeconds(3)).readTimeout(Duration.ofSeconds(8))
            .defaultHeader("User-Agent", "SEAJ/1.0").build(), Clock.systemUTC());
    }
    ExchangeRatesService(RestTemplate http, Clock clock) { this.http = http; this.clock = clock; }
    public synchronized Rates latest() {
        Instant now = clock.instant();
        if (lastAttempt != null && Duration.between(lastAttempt, now).compareTo(Duration.ofSeconds(cached != null && cached.message().isEmpty() ? 3600 : 60)) < 0) {
            if (cached != null) return cached;
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Exchange rates are temporarily unavailable");
        }
        lastAttempt = now;
        try {
            JsonNode data = http.getForObject(URL, JsonNode.class);
            if (data == null || !"USD".equals(data.path("base").asText())) throw new IllegalArgumentException();
            String date = LocalDate.parse(data.path("date").asText()).toString();
            Map<String, BigDecimal> rates = new LinkedHashMap<>();
            for (String currency : List.of("GBP", "EUR", "CHF", "JPY", "CAD", "AUD")) {
                JsonNode rate = data.path("rates").path(currency);
                if (rate.isNumber() && rate.decimalValue().signum() > 0) rates.put(currency, rate.decimalValue());
            }
            if (rates.isEmpty()) throw new IllegalArgumentException();
            cached = new Rates("USD", date, Map.copyOf(rates), "");
        } catch (RuntimeException error) {
            if (cached == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Exchange rates are temporarily unavailable");
            cached = new Rates(cached.base(), cached.date(), cached.rates(), "Exchange rates could not be refreshed. Displayed conversions use the dated rates shown.");
        }
        return cached;
    }
    private History historyCache;
    private Instant historyAttempt;
    public synchronized History history() {
        Instant now = clock.instant();
        if (historyAttempt != null && Duration.between(historyAttempt, now).compareTo(Duration.ofSeconds(historyCache != null && historyCache.message().isEmpty() ? 3600 : 60)) < 0) {
            if (historyCache != null) return historyCache;
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Forex history is temporarily unavailable");
        }
        historyAttempt = now;
        try {
            LocalDate end = LocalDate.now(clock);
            String url = "https://api.frankfurter.dev/v1/" + end.minusDays(120) + ".." + end + "?base=USD&symbols=GBP,EUR,CHF,JPY,CAD,AUD";
            JsonNode data = http.getForObject(url, JsonNode.class);
            if (data == null || !"USD".equals(data.path("base").asText()) || !data.path("rates").isObject()) throw new IllegalArgumentException();
            List<Session> sessions = new ArrayList<>();
            data.path("rates").fields().forEachRemaining(entry -> {
                String date = LocalDate.parse(entry.getKey()).toString();
                Map<String, BigDecimal> rates = new LinkedHashMap<>();
                for (String currency : List.of("GBP", "EUR", "CHF", "JPY", "CAD", "AUD")) {
                    JsonNode value = entry.getValue().path(currency);
                    if (value.isNumber() && value.decimalValue().signum() > 0) rates.put(currency, value.decimalValue());
                }
                if (!rates.isEmpty()) sessions.add(new Session(date, Map.copyOf(rates)));
            });
            if (sessions.isEmpty()) throw new IllegalArgumentException();
            sessions.sort(Comparator.comparing(Session::date));
            historyCache = new History("USD", List.copyOf(sessions), "");
        } catch (RuntimeException error) {
            if (historyCache == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Forex history is temporarily unavailable");
            historyCache = new History("USD", historyCache.sessions(), "Forex rates could not be refreshed. Showing dated reference rates.");
        }
        return historyCache;
    }
    public record Session(String date, Map<String, BigDecimal> rates) {}
    public record History(String base, List<Session> sessions, String message) {}
    public record Rates(String base, String date, Map<String, BigDecimal> rates, String message) {}
}
