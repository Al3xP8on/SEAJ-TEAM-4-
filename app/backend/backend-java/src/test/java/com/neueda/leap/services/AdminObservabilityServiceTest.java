package com.neueda.leap.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import org.springframework.test.web.client.RequestMatcher;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AdminObservabilityServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private MockRestServiceServer server;
    private AdminObservabilityService service;
    private final TestClock clock = new TestClock();

    @BeforeEach void setup() {
        clock.now = Instant.parse("2026-10-19T12:00:00Z");
        RestTemplate http = new RestTemplate();
        server = MockRestServiceServer.bindTo(http).build();
        service = new AdminObservabilityService(http, "http://monitoring.test", "", clock);
    }

    private Map<String, Object> sample(Map<String, String> labels, String value) {
        return Map.of("metric", labels, "value", List.of(clock.instant().getEpochSecond(), value));
    }
    private String response(String type, Object values) throws Exception {
        return json.writeValueAsString(Map.of("status", "success", "data", Map.of("resultType", type, "result", values)));
    }
    private void vector(String query, Object values) throws Exception {
        server.expect(method(HttpMethod.GET)).andExpect(expression(query))
                .andRespond(withSuccess(response("vector", values), MediaType.APPLICATION_JSON));
    }
    private RequestMatcher expression(String expected) {
        return request -> assertEquals(expected, UriUtils.decode(UriComponentsBuilder.fromUri(request.getURI())
                .build().getQueryParams().getFirst("query"), StandardCharsets.UTF_8));
    }
    private void range(Object values) throws Exception {
        server.expect(requestTo(containsString("/api/v1/query_range"))).andExpect(queryParam("step", "120"))
                .andRespond(withSuccess(response("matrix", values), MediaType.APPLICATION_JSON));
    }
    private List<Map<String, Object>> signals() {
        return List.of(sample(Map.of("service_name", "seaj-app", "signal", "reporting"), "1"),
                sample(Map.of("service_name", "seaj-app", "signal", "requests"), "10"),
                sample(Map.of("service_name", "seaj-app", "signal", "errors"), "0.2"),
                sample(Map.of("service_name", "seaj-app", "signal", "latency"), "0.125"));
    }
    private void snapshotResponses(boolean collectorUp) throws Exception {
        vector(AdminObservabilityService.SIGNALS, signals());
        vector(AdminObservabilityService.TARGETS, List.of(sample(Map.of("job", "otel-collector"), collectorUp ? "1" : "0")));
        vector(AdminObservabilityService.LAG, List.of(sample(Map.of("consumergroup", "engine", "topic", "orders"), "12"),
                sample(Map.of("consumergroup", "app", "topic", "executions"), "0"),
                sample(Map.of("consumergroup", "unknown", "topic", "orders"), "-1")));
        vector(AdminObservabilityService.BUSINESS, List.of(sample(Map.of("stat", "placed"), "3.2"),
                sample(Map.of("stat", "tradedValue"), "450.75"), sample(Map.of("stat", "fillSeconds"), "NaN")));
        range(List.of(Map.of("metric", Map.of(), "values", List.of(List.of(1792410000L, "0"), List.of(1792410120L, "3.5")))));
    }

    @Test void mapsRealSignalsWithUnitsAndKeepsMissingValuesUnknown() throws Exception {
        snapshotResponses(true);
        var result = service.snapshot();
        assertEquals("CONNECTED", result.status());
        assertEquals(125d, result.services().get(0).p95Millis());
        assertEquals(2d, result.services().get(0).errorPercent());
        assertTrue(result.services().get(0).reporting());
        assertNull(result.services().get(1).requestsPerSecond());
        assertFalse(result.services().get(1).reporting());
        assertEquals(2, result.consumerLag().size());
        assertEquals(12, result.consumerLag().get(0).lag());
        assertEquals(0, result.traffic().get(0).value());
        assertEquals(450.75, result.trading().get("tradedValue"));
        assertFalse(result.trading().containsKey("fillSeconds"));
        assertEquals(30, result.refreshSeconds());
        server.verify();
    }

    @Test void sharesTheSnapshotForThirtySecondsIncludingFailureResults() throws Exception {
        server.expect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        var first = service.snapshot();
        clock.now = clock.now.plusSeconds(29);
        assertSame(first, service.snapshot());
        assertEquals("UNAVAILABLE", first.status());
        assertTrue(first.services().isEmpty());
        server.verify();
    }

    @Test void stopsShowingCachedHealthySignalsWhenMonitoringBecomesUnavailable() throws Exception {
        snapshotResponses(true);
        server.expect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertEquals("CONNECTED", service.snapshot().status());
        clock.now = clock.now.plusSeconds(30);
        var unavailable = service.snapshot();
        assertEquals("UNAVAILABLE", unavailable.status());
        assertTrue(unavailable.services().isEmpty());
        assertTrue(unavailable.trading().isEmpty());
        server.verify();
    }

    @Test void aFailedSignalDoesNotHideTheOtherSignals() throws Exception {
        vector(AdminObservabilityService.SIGNALS, signals());
        vector(AdminObservabilityService.TARGETS, List.of());
        server.expect(expression(AdminObservabilityService.LAG)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        vector(AdminObservabilityService.BUSINESS, List.of());
        range(List.of());
        var result = service.snapshot();
        assertEquals("PARTIAL", result.status());
        assertEquals(10d, result.services().get(0).requestsPerSecond());
        assertTrue(result.consumerLag().isEmpty());
        assertEquals("UNKNOWN", result.monitors().get(0).status());
        server.verify();
    }

    @Test void aDownCollectorDoesNotProduceLiveLookingServiceCards() throws Exception {
        snapshotResponses(false);
        var result = service.snapshot();
        assertFalse(result.services().get(0).reporting());
        assertNull(result.services().get(0).p95Millis());
        assertNull(result.services().get(0).errorPercent());
        server.verify();
    }

    @Test void emptyAndNonFiniteSamplesAreNotHealthyZeroes() throws Exception {
        vector(AdminObservabilityService.SIGNALS, List.of(sample(Map.of("service_name", "seaj-app", "signal", "requests"), "NaN")));
        vector(AdminObservabilityService.TARGETS, List.of());
        vector(AdminObservabilityService.LAG, List.of());
        vector(AdminObservabilityService.BUSINESS, List.of());
        range(List.of());
        var result = service.snapshot();
        assertNull(result.services().get(0).requestsPerSecond());
        assertTrue(result.traffic().isEmpty());
        assertTrue(result.trading().isEmpty());
        server.verify();
    }

    @Test void invalidResponsesReturnASafeUnavailableMessage() {
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess("{\"status\":\"error\",\"error\":\"private backend detail\"}", MediaType.APPLICATION_JSON));
        var result = service.snapshot();
        assertEquals("UNAVAILABLE", result.status());
        assertFalse(result.message().contains("private backend detail"));
        server.verify();
    }

    private static class TestClock extends Clock {
        Instant now;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
}
