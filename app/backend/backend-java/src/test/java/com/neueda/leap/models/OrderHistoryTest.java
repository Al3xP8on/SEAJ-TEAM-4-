package com.neueda.leap.models;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.neueda.leap.enums.OrderStatus;
import java.time.LocalDateTime;
import java.util.UUID;

class OrderHistoryTest {
    @Test void newRecordCapturesStatusAndCreationTime() {
        UUID orderId = UUID.randomUUID();
        LocalDateTime before = LocalDateTime.now();
        OrderHistory history = new OrderHistory(orderId, OrderStatus.PENDING);
        assertNull(history.getId());
        assertEquals(orderId, history.getOrderId());
        assertEquals(OrderStatus.PENDING, history.getStatus());
        assertFalse(history.getChangedOn().isBefore(before));
        assertFalse(history.getChangedOn().isAfter(LocalDateTime.now()));
    }
    @Test void rejectsNullFieldsForNewAndLoadedRecords() {
        UUID orderId = UUID.randomUUID();
        LocalDateTime time = LocalDateTime.of(2026, 1, 1, 12, 0);
        assertThrows(NullPointerException.class, () -> new OrderHistory(null, OrderStatus.NEW));
        assertThrows(NullPointerException.class, () -> new OrderHistory(orderId, null));
        assertThrows(NullPointerException.class, () -> new OrderHistory(null, orderId, OrderStatus.NEW, time));
        assertThrows(NullPointerException.class, () -> new OrderHistory(1L, null, OrderStatus.NEW, time));
        assertThrows(NullPointerException.class, () -> new OrderHistory(1L, orderId, null, time));
        assertThrows(NullPointerException.class, () -> new OrderHistory(1L, orderId, OrderStatus.NEW, null));
    }
    @Test void loadedRecordsUseHistoryIdForEquality() {
        LocalDateTime time = LocalDateTime.of(2026, 1, 1, 12, 0);
        UUID orderId = UUID.randomUUID();
        OrderHistory first = new OrderHistory(1L, orderId, OrderStatus.NEW, time);
        OrderHistory sameId = new OrderHistory(1L, UUID.randomUUID(), OrderStatus.FILLED, time);
        assertEquals(first, first);
        assertEquals(first, sameId);
        assertEquals(first.hashCode(), sameId.hashCode());
        assertNotEquals(first, new OrderHistory(2L, orderId, OrderStatus.NEW, time));
        assertNotEquals(first, null);
        assertNotEquals(first, "history");
        assertEquals(1L, first.getId());
        assertEquals(time, first.getChangedOn());
    }
}
