package com.neueda.leap;

import com.neueda.leap.config.SchedulingConfig;
import com.neueda.leap.messaging.TradeEvent;
import com.neueda.leap.messaging.TradeEventPublishException;
import com.neueda.leap.models.Instrument;
import com.neueda.leap.models.Positions;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CoreContractTest {

    @Test
    void schedulingConfigurationEnablesScheduling() {
        assertNotNull(new SchedulingConfig());
        assertNotNull(SchedulingConfig.class.getAnnotation(EnableScheduling.class));
    }

    @Test
    void tradeEventDefaultConstructorAndAccessorsRoundTripValues() {
        TradeEvent event = new TradeEvent();
        UUID tradeId = UUID.randomUUID();
        LocalDateTime timestamp = LocalDateTime.of(2026, 10, 15, 13, 0);
        BigDecimal price = new BigDecimal("10.25");

        event.setTradeId(tradeId);
        event.setAccountId("ACC-001");
        event.setSymbol("AAPL");
        event.setSide("BUY");
        event.setPrice(price);
        event.setQuantity(3L);
        event.setStatus("INITIATED");
        event.setTimeInForce("IOC");
        event.setTimestamp(timestamp);
        event.setReason("test");

        assertAll(
                () -> assertEquals(tradeId, event.getTradeId()),
                () -> assertEquals("ACC-001", event.getAccountId()),
                () -> assertEquals("AAPL", event.getSymbol()),
                () -> assertEquals("BUY", event.getSide()),
                () -> assertEquals(price, event.getPrice()),
                () -> assertEquals(3L, event.getQuantity()),
                () -> assertEquals("INITIATED", event.getStatus()),
                () -> assertEquals("IOC", event.getTimeInForce()),
                () -> assertEquals(timestamp, event.getTimestamp()),
                () -> assertEquals("test", event.getReason()),
                () -> assertTrue(event.toString().contains("ACC-001"))
        );
    }

    @Test
    void tradeEventShortConstructorDefaultsToGtc() {
        TradeEvent event = new TradeEvent(
                UUID.randomUUID(), "ACC-001", "AAPL", "BUY",
                BigDecimal.TEN, 1L, "INITIATED", LocalDateTime.now());

        assertEquals("GTC", event.getTimeInForce());
    }

    @Test
    void entityEqualityHandlesNullIdentifiersSafely() {
        Instrument firstInstrument = new Instrument("AAPL", "Apple", "EQUITY", "USD", true);
        Instrument secondInstrument = new Instrument("AAPL", "Apple", "EQUITY", "USD", true);
        assertEquals(firstInstrument, secondInstrument);
        assertEquals(firstInstrument.hashCode(), secondInstrument.hashCode());
        assertNotEquals(firstInstrument, new Instrument("MSFT", "Microsoft", "EQUITY", "USD", true));
        assertNotEquals(firstInstrument, null);
        assertNotEquals(firstInstrument, "AAPL");

        Positions firstPosition = new Positions(1L, "AAPL", 10, BigDecimal.TEN);
        Positions secondPosition = new Positions(1L, "AAPL", 10, BigDecimal.TEN);
        assertEquals(firstPosition, secondPosition);
        assertEquals(firstPosition.hashCode(), secondPosition.hashCode());
        assertNotEquals(firstPosition, new Positions(2L, "AAPL", 10, BigDecimal.TEN));
        assertNotEquals(firstPosition, null);
    }

    @Test
    void publishingExceptionKeepsCauseAndContext() {
        IllegalStateException cause = new IllegalStateException("offline");
        TradeEventPublishException exception = new TradeEventPublishException("publish failed", cause);

        assertEquals("publish failed", exception.getMessage());
        assertSame(cause, exception.getCause());
    }
}
