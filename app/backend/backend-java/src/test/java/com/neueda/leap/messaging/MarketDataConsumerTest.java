package com.neueda.leap.messaging;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;

class MarketDataConsumerTest {
    private final LatestPriceStore store = mock(LatestPriceStore.class);
    private final MarketDataConsumer consumer = new MarketDataConsumer(store);
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private String message(Map<String, Object> payload) throws Exception {
        return mapper.writeValueAsString(new EventEnvelope(UUID.randomUUID(), EventType.PRICE_UPDATE, Instant.now(), 1, payload));
    }
    private Map<String, Object> payload(Object timestamp) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("source", "ALPACA_IEX");
        payload.put("symbol", " aapl ");
        payload.put("bidPrice", "10.25");
        payload.put("askPrice", 10.5);
        payload.put("lastPrice", 10);
        if (timestamp != null) payload.put("priceTimeStamp", timestamp);
        return payload;
    }
    @Test void convertsNumericAndStringPricesAndIsoTimestamp() throws Exception {
        Instant timestamp = Instant.parse("2026-01-01T00:00:00Z");
        consumer.consumeMarketData(message(payload(timestamp.toString())), "IGNORED");
        ArgumentCaptor<MarketPrice> captor = ArgumentCaptor.forClass(MarketPrice.class);
        verify(store).updatePrice(captor.capture());
        MarketPrice price = captor.getValue();
        assertEquals("AAPL", price.symbol());
        assertEquals(new BigDecimal("10.25"), price.bidPrice());
        assertEquals(0, new BigDecimal("10.5").compareTo(price.askPrice()));
        assertEquals(0, BigDecimal.TEN.compareTo(price.lastPrice()));
        assertEquals(timestamp, price.priceTimeStamp());
    }
    @Test void convertsEpochMillisTimestamps() throws Exception {
        long millis = 1767225600000L;
        consumer.consumeMarketData(message(payload(millis)), "AAPL");
        consumer.consumeMarketData(message(payload((double) millis)), "AAPL");
        verify(store, times(2)).updatePrice(argThat(price -> price.priceTimeStamp().equals(Instant.ofEpochMilli(millis))));
    }
    @Test void rejectsMissingTimestampInsteadOfMakingAnOldQuoteFresh() throws Exception {
        String message = message(payload(null));
        assertThrows(IllegalArgumentException.class, () -> consumer.consumeMarketData(message, "AAPL"));
        verifyNoInteractions(store);
    }
    @Test void rejectsUnreadableOrEmptyMessagesWithoutUpdatingStore() {
        for (String invalid : new String[]{null, "", "not-json", "null", "{}"}) {
            assertThrows(PoisonMessageException.class, () -> consumer.consumeMarketData(invalid, "AAPL"));
        }
        verifyNoInteractions(store);
    }
    @Test void rejectsEmptyAndIncompletePayloads() throws Exception {
        String empty = message(Map.of());
        String incomplete = message(Map.of("symbol", "AAPL"));
        assertThrows(PoisonMessageException.class, () -> consumer.consumeMarketData(empty, "AAPL"));
        assertThrows(PoisonMessageException.class, () -> consumer.consumeMarketData(incomplete, "AAPL"));
        verifyNoInteractions(store);
    }
    @Test void rejectsInvalidPriceRepresentations() throws Exception {
        Map<String, Object> payload = payload(null);
        payload.put("bidPrice", true);
        String invalid = message(payload);
        assertThrows(IllegalArgumentException.class, () -> consumer.consumeMarketData(invalid, "AAPL"));
        verifyNoInteractions(store);
    }
    @Test void convertsJacksonDecimalSecondsTimestampWithoutTreatingItAsMillis() throws Exception {
        Instant instant = Instant.parse("2026-10-14T09:00:00.125Z");
        consumer.consumeMarketData(message(payload(instant)), "AAPL");
        verify(store).updatePrice(argThat(price -> price.priceTimeStamp().equals(instant)));
    }
}
