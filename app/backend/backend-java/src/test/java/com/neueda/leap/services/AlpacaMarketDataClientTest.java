package com.neueda.leap.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AlpacaMarketDataClientTest {
    private MockRestServiceServer server;
    private AlpacaMarketDataClient client;
    private final Instant now = Instant.parse("2026-10-14T15:00:00Z");
    private static final String SNAPSHOT = """
      {"latestTrade":{"p":110,"t":"2026-10-14T14:59:59Z"},
       "latestQuote":{"bp":109.9,"ap":110.1,"t":"2026-10-14T14:59:58Z"},
       "dailyBar":{"o":103,"h":112,"l":102,"c":110,"v":20000,"t":"2026-10-14T04:00:00Z"},
       "prevDailyBar":{"o":98,"h":102,"l":97,"c":100,"v":50000,"t":"2026-10-13T04:00:00Z"}}
      """;

    @BeforeEach void setup() {
        var builder = new RestTemplateBuilder().additionalCustomizers(template -> server = MockRestServiceServer.createServer(template));
        client = new AlpacaMarketDataClient(builder, true, "test-id", "test-secret", "iex");
    }
    private void snapshotResponse(String body) {
        server.expect(requestTo("https://data.alpaca.markets/v2/stocks/snapshots?symbols=AAPL,MSFT&feed=iex"))
                .andExpect(header("APCA-API-KEY-ID", "test-id"))
                .andExpect(header("APCA-API-SECRET-KEY", "test-secret"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }
    @Test void readsPricesDailyGainAndOriginalQuoteTimestampWithAuthentication() {
        snapshotResponse("{\"AAPL\":" + SNAPSHOT + "}");
        var data = client.snapshots(List.of("AAPL", "MSFT"));
        assertEquals(1, data.size());
        var price = data.get(0);
        assertEquals(new BigDecimal("110"), price.price());
        assertEquals(new BigDecimal("100"), price.previousClose());
        assertEquals(0, new BigDecimal("10").compareTo(price.changePercent()));
        assertEquals(Instant.parse("2026-10-14T14:59:58Z"), price.quote().priceTimeStamp());
        assertEquals(new BigDecimal("109.9"), price.quote().bidPrice());
        server.verify();
    }
    @Test void comparesPremarketTradesWithTheMostRecentDailyClose() {
        snapshotResponse("{\"AAPL\":" + SNAPSHOT.replace("2026-10-14T14:59:59Z", "2026-10-15T12:00:00Z") + "}");
        var price = client.snapshots(List.of("AAPL", "MSFT")).get(0);
        assertEquals(new BigDecimal("110"), price.previousClose());
        assertEquals(0, price.changePercent().signum());
    }
    @Test void omitsInvalidQuotesWithoutLosingTheLatestTrade() {
        snapshotResponse("{\"AAPL\":" + SNAPSHOT.replace("\"bp\":109.9", "\"bp\":0") + "}");
        var price = client.snapshots(List.of("AAPL", "MSFT")).get(0);
        assertNull(price.quote());
        assertEquals(new BigDecimal("110"), price.price());
    }
    @Test void skipsMalformedSymbolsAndUsesAvailableBarsWhenThereIsNoTrade() {
        String invalid = SNAPSHOT.replace("2026-10-14T14:59:59Z", "invalid-time")
                .replace("\"dailyBar\":", "\"absentDailyBar\":");
        String noTrade = SNAPSHOT.replace("\"latestTrade\":", "\"absentTrade\":");
        snapshotResponse("{\"AAPL\":" + invalid + ",\"MSFT\":" + noTrade + "}");
        var data = client.snapshots(List.of("AAPL", "MSFT"));
        assertEquals(1, data.size());
        assertEquals("MSFT", data.get(0).symbol());
        assertEquals(Instant.parse("2026-10-14T04:00:00Z"), data.get(0).timestamp());
    }
    @Test void followsChartPaginationAndKeepsBarsChronological() {
        server.expect(request -> {
            assertTrue(request.getURI().toString().contains("timeframe=1Min"));
            assertTrue(request.getURI().toString().contains("feed=iex"));
            assertTrue(request.getURI().toString().contains("start=2026-10-07T15:00:00Z"));
        }).andRespond(withSuccess("{\"bars\":{\"AAPL\":[{\"t\":\"2026-10-14T14:00:00Z\",\"o\":101,\"h\":103,\"l\":100,\"c\":102,\"v\":10}]},\"next_page_token\":\"page-2\"}", MediaType.APPLICATION_JSON));
        server.expect(request -> assertTrue(request.getURI().toString().contains("page_token=page-2")))
                .andRespond(withSuccess("{\"bars\":{\"AAPL\":[{\"t\":\"2026-10-14T14:01:00Z\",\"o\":102,\"h\":104,\"l\":101,\"c\":103,\"v\":20}]},\"next_page_token\":null}", MediaType.APPLICATION_JSON));
        var bars = client.bars("AAPL", "1Min", now);
        assertEquals(2, bars.size());
        assertTrue(bars.get(0).timestamp().isBefore(bars.get(1).timestamp()));
        assertEquals(new BigDecimal("103"), bars.get(1).close());
        server.verify();
    }
    @Test void returnsClearSanitizedAuthenticationErrors() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("upstream test-secret test-id"));
        var error = assertThrows(AlpacaMarketDataClient.DataUnavailable.class, () -> client.snapshots(List.of("AAPL")));
        assertTrue(error.getMessage().contains("credentials"));
        assertFalse(error.getMessage().contains("test-secret"));
        assertFalse(error.getMessage().contains("test-id"));
    }
    @Test void handlesSubscriptionAndRateLimitFailures() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.FORBIDDEN));
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertTrue(assertThrows(AlpacaMarketDataClient.DataUnavailable.class, () -> client.snapshots(List.of("AAPL"))).getMessage().contains("subscription"));
        assertTrue(assertThrows(AlpacaMarketDataClient.DataUnavailable.class, () -> client.snapshots(List.of("AAPL"))).getMessage().contains("limit"));
    }
    @Test void doesNotMakeRequestsWithoutBothCredentials() {
        var missing = new AlpacaMarketDataClient(new RestTemplateBuilder(), true, "", "", "iex");
        assertFalse(missing.isConfigured());
        assertThrows(AlpacaMarketDataClient.DataUnavailable.class, () -> missing.snapshots(List.of("AAPL")));
    }
    @Test void validatesChartTimeframeBeforeSendingRequests() {
        assertThrows(IllegalArgumentException.class, () -> client.bars("AAPL", "bad", now));
    }
    @Test void capsIntradayHistoryInsteadOfDownloadingAWeekOfBars() {
        assertHistoryLimit("1Min", 1000, 60);
    }
    @Test void capsDailyHistoryToTheThirtyBarsDisplayed() {
        assertHistoryLimit("1Day", 30, 86400);
    }
    @Test void requestsSixHourHistoryAndStopsAtItsBoundedLimit() {
        assertHistoryLimit("6Hour", 60, 6 * 3600);
    }
    private void assertHistoryLimit(String timeframe, int limit, long seconds) {
        String rows = java.util.stream.IntStream.range(0, limit).mapToObj(index ->
                "{\"t\":\"" + now.minusSeconds(index * seconds) + "\",\"o\":100,\"h\":110,\"l\":90,\"c\":100,\"v\":10}")
                .collect(java.util.stream.Collectors.joining(","));
        server.expect(request -> {
            assertTrue(request.getURI().toString().contains("timeframe=" + timeframe));
            if (timeframe.equals("6Hour")) {
                assertTrue(request.getURI().toString().contains("start=2026-09-23T15:00:00Z"));
            }
            assertTrue(request.getURI().toString().contains("sort=desc"));
            assertTrue(request.getURI().toString().contains("limit=" + limit));
        }).andRespond(withSuccess("{\"bars\":{\"AAPL\":[" + rows + "]},\"next_page_token\":\"do-not-fetch-more\"}", MediaType.APPLICATION_JSON));
        var bars = client.bars("AAPL", timeframe, now);
        assertEquals(limit, bars.size());
        assertEquals(now.minusSeconds((limit - 1) * seconds), bars.get(0).timestamp());
        assertEquals(now, bars.get(limit - 1).timestamp());
        server.verify();
    }
}
