package com.neueda.leap.services;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Fixed, read-only Prometheus queries. One cached snapshot is shared by all admins. */
@Service
public class AdminObservabilityService {
    private static final String HTTP = "http_server_request_duration_seconds";
    private static final String SELECTOR = "{service_name=~\"seaj-(app|auth-service|execution-engine)\"}";
    static final String SIGNALS = tag("sum by(service_name)(rate(" + HTTP + "_count" + SELECTOR + "[5m]))", "signal", "requests")
            + " or " + tag("sum by(service_name)(rate(" + HTTP + "_count{service_name=~\"seaj-.*\",http_response_status_code=~\"5..\"}[5m]))", "signal", "errors")
            + " or " + tag("histogram_quantile(0.95,sum by(le,service_name)(rate(" + HTTP + "_bucket" + SELECTOR + "[5m])))", "signal", "latency")
            + " or " + tag("count by(service_name)(last_over_time(jvm_memory_used_bytes" + SELECTOR + "[2m]) or last_over_time(" + HTTP + "_count" + SELECTOR + "[2m]))", "signal", "reporting");
    static final String TARGETS = "up{job=~\"otel-collector|otel-collector-internal|tempo|loki|prometheus|kafka-exporter\"}";
    static final String LAG = "sum by(consumergroup,topic)(kafka_consumergroup_lag >= 0)";
    static final String BUSINESS = tag("sum(increase(seaj_orders_placed_total[1h]))", "stat", "placed")
            + " or " + tag("sum(increase(seaj_orders_completed_total{order_status=\"FILLED\"}[1h]))", "stat", "filled")
            + " or " + tag("sum(increase(seaj_orders_completed_total{order_status=\"REJECTED\"}[1h]))", "stat", "rejected")
            + " or " + tag("sum(increase(seaj_orders_completed_total{order_status=\"CANCELLED\"}[1h]))", "stat", "cancelled")
            + " or " + tag("sum(increase(seaj_orders_traded_value_total[1h]))", "stat", "tradedValue")
            + " or " + tag("sum(increase(seaj_order_fill_duration_seconds_sum[1h])) / sum(increase(seaj_order_fill_duration_seconds_count[1h]))", "stat", "fillSeconds");
    static final String TRAFFIC = "sum(rate(" + HTTP + "_count" + SELECTOR + "[5m]))";
    private final RestTemplate http;
    private final String baseUrl;
    private final String grafanaUrl;
    private final Clock clock;
    private Snapshot cached;

    @Autowired
    public AdminObservabilityService(RestTemplateBuilder builder,
            @Value("${observability.prometheus-url:http://prometheus:9090}") String baseUrl,
            @Value("${observability.grafana-url:}") String grafanaUrl) {
        this(builder.connectTimeout(Duration.ofMillis(500)).readTimeout(Duration.ofSeconds(2)).build(),
                baseUrl, grafanaUrl, Clock.systemUTC());
    }

    AdminObservabilityService(RestTemplate http, String baseUrl, String grafanaUrl, Clock clock) {
        this.http = http;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.grafanaUrl = grafanaUrl;
        this.clock = clock;
    }

    public synchronized Snapshot snapshot() {
        Instant now = clock.instant();
        if (cached != null && now.isBefore(cached.observedAt().plusSeconds(30))) return cached;
        JsonNode signals;
        try {
            signals = query(SIGNALS);
        } catch (RuntimeException unavailable) {
            // Fail fast when monitoring is disabled; never keep showing an old healthy snapshot.
            return cached = new Snapshot("UNAVAILABLE", "Monitoring is unavailable. Check that the observability profile and telemetry are enabled.",
                    clock.instant(), 3600, 30, grafanaUrl, List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        int[] failures = {0};
        JsonNode targets = optionalQuery(TARGETS, failures);
        JsonNode lag = optionalQuery(LAG, failures);
        JsonNode business = optionalQuery(BUSINESS, failures);
        List<TrafficPoint> traffic = new ArrayList<>();
        try {
            var uri = UriComponentsBuilder.fromUriString(baseUrl + "/api/v1/query_range")
                    .queryParam("query", TRAFFIC).queryParam("start", now.minusSeconds(3600).getEpochSecond())
                    .queryParam("end", now.getEpochSecond()).queryParam("step", 120).build().encode().toUri();
            JsonNode result = results(http.getForObject(uri, JsonNode.class), "matrix");
            if (!result.isEmpty()) for (JsonNode point : result.get(0).path("values")) {
                Double value = finite(point.path(1));
                if (value != null && point.path(0).isNumber() && traffic.size() < 61) {
                    traffic.add(new TrafficPoint(Instant.ofEpochSecond(point.get(0).asLong()), value));
                }
            }
        } catch (RuntimeException unavailable) { failures[0]++; }

        Map<String, Map<String, Double>> byService = new HashMap<>();
        for (JsonNode sample : signals) {
            Double value = finite(sample.path("value").path(1));
            if (value != null) byService.computeIfAbsent(sample.path("metric").path("service_name").asText(), key -> new HashMap<>())
                    .put(sample.path("metric").path("signal").asText(), value);
        }
        List<Monitor> monitors = new ArrayList<>();
        Map<String, Boolean> jobs = new TreeMap<>();
        if (targets != null) for (JsonNode target : targets) {
            Double value = finite(target.path("value").path(1));
            if (value != null) jobs.merge(target.path("metric").path("job").asText(), value == 1, (a, b) -> a && b);
        }
        Map.of("otel-collector", "Telemetry pipeline", "otel-collector-internal", "Collector metrics", "prometheus", "Prometheus",
                "tempo", "Trace storage", "loki", "Log storage", "kafka-exporter", "Kafka exporter")
                .forEach((job, label) -> monitors.add(new Monitor(job, label, jobs.containsKey(job) ? (jobs.get(job) ? "UP" : "DOWN") : "UNKNOWN")));
        monitors.sort(Comparator.comparing(Monitor::name));
        List<ServiceSignal> services = new ArrayList<>();
        for (String id : List.of("seaj-app", "seaj-auth-service", "seaj-execution-engine")) {
            var values = byService.getOrDefault(id, Map.of());
            Double requests = values.get("requests");
            Double errors = requests != null && requests > 0 ? 100 * values.getOrDefault("errors", 0d) / requests : null;
            Double latency = values.get("latency");
            boolean reporting = values.getOrDefault("reporting", 0d) > 0 && !Boolean.FALSE.equals(jobs.get("otel-collector"));
            services.add(new ServiceSignal(id, switch (id) {
                case "seaj-app" -> "Trading API"; case "seaj-auth-service" -> "Authentication"; default -> "Execution engine";
            }, reporting, reporting ? requests : null, reporting ? errors : null, reporting && latency != null ? latency * 1000 : null));
        }
        List<ConsumerLag> consumers = new ArrayList<>();
        if (lag != null) for (JsonNode sample : lag) {
            Double value = finite(sample.path("value").path(1));
            if (value != null && value >= 0) consumers.add(new ConsumerLag(sample.path("metric").path("consumergroup").asText(),
                    sample.path("metric").path("topic").asText(), value));
        }
        consumers.sort(Comparator.comparingDouble(ConsumerLag::lag).reversed());
        Map<String, Double> trading = new HashMap<>();
        if (business != null) for (JsonNode sample : business) {
            Double value = finite(sample.path("value").path(1));
            String stat = sample.path("metric").path("stat").asText();
            if (value != null && Set.of("placed", "filled", "rejected", "cancelled", "tradedValue", "fillSeconds").contains(stat)) trading.put(stat, value);
        }
        boolean reporting = services.stream().anyMatch(ServiceSignal::reporting);
        return cached = new Snapshot(failures[0] > 0 ? "PARTIAL" : "CONNECTED",
                failures[0] > 0 ? "Some monitoring signals could not be loaded." : reporting ? "Receiving platform telemetry." : "Prometheus is connected. Waiting for recent service telemetry.",
                clock.instant(), 3600, 30, grafanaUrl, List.copyOf(services), List.copyOf(monitors),
                consumers.stream().limit(50).toList(), List.copyOf(traffic), Map.copyOf(trading));
    }

    private JsonNode optionalQuery(String query, int[] failures) {
        try { return query(query); } catch (RuntimeException unavailable) { failures[0]++; return null; }
    }
    private JsonNode query(String query) {
        var uri = UriComponentsBuilder.fromUriString(baseUrl + "/api/v1/query").queryParam("query", query).build().encode().toUri();
        return results(http.getForObject(uri, JsonNode.class), "vector");
    }
    private JsonNode results(JsonNode body, String type) {
        if (body == null || !"success".equals(body.path("status").asText()) || !type.equals(body.path("data").path("resultType").asText())
                || !body.path("data").path("result").isArray()) throw new IllegalStateException("Invalid monitoring response");
        return body.path("data").path("result");
    }
    private static Double finite(JsonNode value) {
        try { double number = Double.parseDouble(value.asText()); return Double.isFinite(number) ? number : null; }
        catch (NumberFormatException invalid) { return null; }
    }
    private static String tag(String expression, String label, String value) {
        return "label_replace((" + expression + "),\"" + label + "\",\"" + value + "\",\"\",\"\")";
    }
    public record Snapshot(String status, String message, Instant observedAt, int windowSeconds, int refreshSeconds, String grafanaUrl,
                           List<ServiceSignal> services, List<Monitor> monitors, List<ConsumerLag> consumerLag, List<TrafficPoint> traffic, Map<String, Double> trading) {}
    public record ServiceSignal(String id, String name, boolean reporting, Double requestsPerSecond, Double errorPercent, Double p95Millis) {}
    public record Monitor(String id, String name, String status) {}
    public record ConsumerLag(String group, String topic, double lag) {}
    public record TrafficPoint(Instant timestamp, double value) {}
}
