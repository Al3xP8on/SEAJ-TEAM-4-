package com.neueda.leap.services;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AlpacaBondServiceTest {
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-20T12:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    private static final String BASE = "https://broker-api.sandbox.alpaca.markets";
    private static final String TOKEN = "{\"access_token\":\"test-token\",\"expires_in\":899}";
    @Test void disabledConnectionMakesNoRequests() {
        var service = new AlpacaBondService(false, BASE, "id", "secret", (m,u,t,b) -> { fail("Unexpected request"); return null; }, Clock.systemUTC());
        assertFalse(service.overview().configured());
    }
    @Test void authenticatesCachesTokenParsesBothCataloguesAndRetainsDatedDataOnFailure() {
        var clock = new MutableClock();
        List<String> requests = new ArrayList<>();
        var service = new AlpacaBondService(true, BASE, "client", "secret+", (method, uri, token, body) -> {
            requests.add(method + " " + uri.getPath());
            if (method.equals("POST")) {
                assertEquals("authx.sandbox.alpaca.markets", uri.getHost());
                assertTrue(body.contains("client_secret=secret%2B"));
                return new AlpacaBondService.Reply(200, TOKEN);
            }
            assertEquals("test-token", token);
            if (requests.size() > 3) return new AlpacaBondService.Reply(503, "sensitive upstream content");
            if (uri.getPath().endsWith("us_treasuries")) return new AlpacaBondService.Reply(200, "{\"us_treasuries\":[{\"isin\":\"US912797MU86\",\"cusip\":\"912797MU8\",\"description\":\"Treasury Bill\",\"maturity_date\":\"2027-03-27\",\"bond_status\":\"outstanding\",\"tradable\":true,\"coupon\":0,\"close_price\":99.6,\"close_price_date\":\"2026-10-19\",\"close_yield_to_maturity\":\"4.2\"}]}");
            return new AlpacaBondService.Reply(200, "{\"us_corporates\":[{\"isin\":\"US00138CAU27\",\"description\":\"Corporate Bond\",\"issuer\":\"Issuer\",\"bond_status\":\"outstanding\",\"maturity_date\":\"2027-05-01\",\"coupon\":5.75}]}");
        }, clock);
        var first = service.overview();
        assertEquals(2, first.bonds().size()); assertTrue(first.message().isEmpty());
        assertEquals("2026-10-19", first.bonds().get(0).priceDate());
        assertEquals(0, first.bonds().get(0).coupon().intValue());
        assertNull(first.bonds().get(1).closePrice());
        assertSame(first, service.overview()); assertEquals(3, requests.size());
        clock.now = clock.now.plusSeconds(301);
        var stale = service.overview(); assertEquals(first.bonds(), stale.bonds());
        assertFalse(stale.message().isEmpty()); assertFalse(stale.message().contains("sensitive"));
        assertEquals(1, requests.stream().filter(r -> r.startsWith("POST")).count());
    }
    @Test void reportsEntitlementDenialsWithoutExposingUpstreamBodiesOrCredentials() {
        var service = new AlpacaBondService(true, BASE, "client", "private-secret", (m,u,t,b) -> new AlpacaBondService.Reply(m.equals("POST") ? 200 : 403, m.equals("POST") ? TOKEN : "subscription details private-secret"), Clock.systemUTC());
        var result = service.overview(); assertTrue(result.configured()); assertTrue(result.bonds().isEmpty());
        assertTrue(result.message().contains("subscription")); assertFalse(result.toString().contains("private-secret"));
    }
    @Test void retriesOnceWithANewTokenWhenCatalogueReturns401() {
        int[] tokens = {0}, requests = {0};
        var service = new AlpacaBondService(true, BASE, "client", "secret", (m,u,t,b) -> {
            if (m.equals("POST")) { tokens[0]++; return new AlpacaBondService.Reply(200, TOKEN); }
            requests[0]++; if (requests[0] == 1) return new AlpacaBondService.Reply(401, "");
            return new AlpacaBondService.Reply(200, u.getPath().endsWith("us_treasuries") ? "{\"us_treasuries\":[]}" : "{\"us_corporates\":[]}");
        }, Clock.systemUTC());
        assertTrue(service.overview().message().isEmpty()); assertEquals(2, tokens[0]); assertEquals(3, requests[0]);
    }
}
