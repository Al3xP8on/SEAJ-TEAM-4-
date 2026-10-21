package com.neueda.leap.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;

/** Read-only Broker catalogue. OAuth credentials/tokens never enter response DTOs or logs. */
@Service
public class AlpacaBondService {
    private final boolean enabled;
    private final String base, authUrl, clientId, clientSecret;
    private final Transport transport;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper();
    private String token;
    private Instant tokenExpiry = Instant.EPOCH;
    private Overview cached;
    private Instant lastAttempt;
    private final Map<String, List<Bond>> lastByType = new HashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public AlpacaBondService(
        @Value("${alpaca.broker.enabled:false}") boolean enabled,
        @Value("${alpaca.broker.base-url:https://broker-api.sandbox.alpaca.markets}") String base,
        @Value("${alpaca.broker.client-id:}") String clientId,
        @Value("${alpaca.broker.client-secret:}") String clientSecret) {
        this(enabled, base, clientId, clientSecret, new HttpTransport(), Clock.systemUTC());
    }
    AlpacaBondService(boolean enabled, String base, String clientId, String clientSecret, Transport transport, Clock clock) {
        this.enabled = enabled; this.base = stripTrailingSlashes(base);
        if (!List.of("https://broker-api.sandbox.alpaca.markets", "https://broker-api.alpaca.markets").contains(this.base))
            throw new IllegalArgumentException("Unsupported Alpaca Broker base URL");
        this.authUrl = this.base.contains("sandbox") ? "https://authx.sandbox.alpaca.markets/v1/oauth2/token" : "https://authx.alpaca.markets/v1/oauth2/token";
        this.clientId = clientId.trim(); this.clientSecret = clientSecret.trim(); this.transport = transport; this.clock = clock;
    }

    private static String stripTrailingSlashes(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') end--;
        return value.substring(0, end);
    }
    public synchronized Overview overview() {
        boolean configured = enabled && !clientId.isBlank() && !clientSecret.isBlank();
        String environment = base.contains("sandbox") ? "Sandbox" : "Production";
        if (!configured) return new Overview(false, environment, null, List.of(), "Configure Alpaca Broker client credentials to load individual bonds.");
        Instant now = clock.instant();
        if (cached != null && lastAttempt != null && Duration.between(lastAttempt, now).compareTo(Duration.ofSeconds(cached.message().isEmpty() ? 300 : 60)) < 0) return cached;
        lastAttempt = now;
        List<String> warnings = new ArrayList<>();
        try {
            accessToken();
            for (String type : List.of("us_treasuries", "us_corporates")) {
                try { lastByType.put(type, catalogue(type)); }
                catch (Unavailable error) { warnings.add(error.getMessage()); }
            }
        } catch (Unavailable error) { warnings.add(error.getMessage()); }
        List<Bond> bonds = lastByType.values().stream().flatMap(Collection::stream)
            .sorted(Comparator.comparing(Bond::maturityDate, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Bond::isin)).toList();
        cached = new Overview(true, environment, now.toString(), bonds, String.join(" ", new LinkedHashSet<>(warnings)));
        return cached;
    }
    private List<Bond> catalogue(String type) {
        String url = base + "/v1/assets/fixed_income/" + type + "?bond_status=outstanding";
        Reply reply = call("GET", url, accessToken(), null);
        if (reply.status() == 401) { token = null; reply = call("GET", url, accessToken(), null); }
        String label = type.equals("us_treasuries") ? "Treasury" : "Corporate bond";
        if (reply.status() == 403) throw new Unavailable(label + " access is not enabled for this subscription.");
        if (reply.status() == 401) throw new Unavailable("Alpaca rejected the Broker access token. Check the Broker credentials.");
        if (reply.status() == 429) throw new Unavailable("Alpaca's Broker request limit was reached. Retrying shortly.");
        if (reply.status() != 200) throw new Unavailable(label + " catalogue is temporarily unavailable.");
        try {
            JsonNode root = json.readTree(reply.body());
            JsonNode rows = root.isArray() ? root : root.path(type);
            if (!rows.isArray()) throw new IllegalArgumentException();
            List<Bond> bonds = new ArrayList<>();
            for (JsonNode row : rows) {
                String isin = text(row, "isin");
                String status = text(row, "bond_status");
                if (isin == null || isin.isBlank() || (status != null && !status.equals("outstanding"))) continue;
                bonds.add(new Bond(isin, text(row, "cusip"), label,
                    Optional.ofNullable(text(row, "description")).orElse(isin),
                    Optional.ofNullable(text(row, "issuer")).orElse(type.equals("us_treasuries") ? "U.S. Treasury" : "Unknown issuer"),
                    text(row, "maturity_date"), number(row, "coupon"), number(row, "close_price"),
                    text(row, "close_price_date"), number(row, "close_yield_to_maturity"), row.path("tradable").asBoolean(false)));
            }
            return List.copyOf(bonds);
        } catch (Exception error) { throw new Unavailable(label + " catalogue returned an invalid response."); }
    }
    private String accessToken() {
        if (token != null && clock.instant().isBefore(tokenExpiry)) return token;
        String body = "grant_type=client_credentials&client_id=" + encode(clientId) + "&client_secret=" + encode(clientSecret);
        Reply reply = call("POST", authUrl, null, body);
        if (reply.status() != 200) throw new Unavailable(reply.status() == 400 || reply.status() == 401 || reply.status() == 403
            ? "Alpaca Broker authentication failed. Check the client ID, client secret and environment."
            : "Alpaca Broker authentication is temporarily unavailable.");
        try {
            JsonNode data = json.readTree(reply.body());
            String value = data.path("access_token").asText(""); long seconds = data.path("expires_in").asLong(0);
            if (value.isBlank() || seconds <= 0) throw new IllegalArgumentException();
            token = value; tokenExpiry = clock.instant().plusSeconds(Math.max(0, seconds - 30)); return token;
        } catch (Exception error) { throw new Unavailable("Alpaca returned an invalid authentication response."); }
    }
    private Reply call(String method, String url, String token, String body) {
        try { return transport.send(method, URI.create(url), token, body); }
        catch (Exception error) { if (error instanceof InterruptedException) Thread.currentThread().interrupt(); throw new Unavailable("Alpaca Broker could not be reached. Retrying shortly."); }
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String text(JsonNode row, String key) { return row.path(key).isTextual() ? row.path(key).asText() : null; }
    private static BigDecimal number(JsonNode row, String key) {
        JsonNode value = row.path(key);
        try { return value.isNumber() || value.isTextual() ? new BigDecimal(value.asText()) : null; }
        catch (NumberFormatException error) { return null; }
    }
    interface Transport { Reply send(String method, URI uri, String token, String body) throws Exception; }
    record Reply(int status, String body) {}
    private static class HttpTransport implements Transport {
        private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        public Reply send(String method, URI uri, String token, String body) throws Exception {
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).header("Accept", "application/json");
            if (token != null) request.header("Authorization", "Bearer " + token);
            if (method.equals("POST")) request.header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body));
            else request.GET();
            var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new Reply(response.statusCode(), response.body());
        }
    }
    private static class Unavailable extends RuntimeException { Unavailable(String message) { super(message); } }
    public record Bond(String isin, String cusip, String type, String name, String issuer, String maturityDate, BigDecimal coupon, BigDecimal closePrice, String priceDate, BigDecimal yieldToMaturity, boolean tradable) {}
    public record Overview(boolean configured, String environment, String checkedAt, List<Bond> bonds, String message) {}
}
