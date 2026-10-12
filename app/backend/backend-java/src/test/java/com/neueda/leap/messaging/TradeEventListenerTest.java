package com.neueda.leap.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.models.Account;
import com.neueda.leap.models.Instrument;
import com.neueda.leap.models.Order;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("TradeEventListener Tests")
public class TradeEventListenerTest {

    @InjectMocks
    private TradeEventListener tradeEventListener;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TradeEventPublisher tradeEventPublisher;

    private ObjectMapper objectMapper;
    private UUID orderId;
    private UUID eventId;
    private Account account;
    private Order order;
    private Instrument instrument;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        
        orderId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        
        account = new Account("ACC-001", "testuser", "password", "Test Account", "test@example.com", "1234567890", 
                new BigDecimal("50000.00"), com.neueda.leap.enums.AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(account, "id", 1L);
        
        instrument = new Instrument("AAPL", "Apple Inc.", "EQUITY", "USD", true);
        
        order = new Order(orderId, account, instrument, 100, new BigDecimal("150.50"), 
                com.neueda.leap.enums.OrderSide.BUY, "idem-key", OrderStatus.PENDING, LocalDateTime.now());
    }

    @Test
    @DisplayName("Should handle ORDER_CANCELLED event successfully")
    void testHandleOrderCancelledEvent() throws Exception {
        EventType eventType = EventType.ORDER_CANCELLED;
        TradeEvent tradeEvent = new TradeEvent(
                orderId, "ACC-001", "AAPL", "BUY", new BigDecimal("150.50"), 100, "CANCELLED", "GTC", LocalDateTime.now()
        );
        tradeEvent.setReason("User requested cancellation");

        Map<String, Object> payload = new HashMap<>();
        payload.put("tradeId", orderId);
        payload.put("accountId", "ACC-001");
        payload.put("symbol", "AAPL");
        payload.put("side", "BUY");
        payload.put("price", new BigDecimal("150.50"));
        payload.put("quantity", 100);
        payload.put("status", "CANCELLED");
        payload.put("timeInForce", "GTC");
        payload.put("timestamp", LocalDateTime.now());
        payload.put("reason", "User requested cancellation");

        EventEnvelope envelope = new EventEnvelope(eventId, eventType, Instant.now(), 1, payload);
        String message = objectMapper.writeValueAsString(envelope);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        tradeEventListener.handleTradeEvent(message, 0, 100L);

        verify(orderRepository).findById(orderId);
        verify(orderRepository).save(any(Order.class));
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    @Test
    @DisplayName("Should handle ORDER_ACCEPTED event")
    void testHandleOrderAcceptedEvent() throws Exception {
        EventType eventType = EventType.ORDER_ACCEPTED;
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("tradeId", orderId);
        payload.put("accountId", "ACC-001");
        payload.put("symbol", "AAPL");
        payload.put("side", "BUY");
        payload.put("price", new BigDecimal("150.50"));
        payload.put("quantity", 100);
        payload.put("status", "ACCEPTED");
        payload.put("timeInForce", "GTC");
        payload.put("timestamp", LocalDateTime.now());

        EventEnvelope envelope = new EventEnvelope(eventId, eventType, Instant.now(), 1, payload);
        String message = objectMapper.writeValueAsString(envelope);

        tradeEventListener.handleTradeEvent(message, 0, 100L);

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Should handle ORDER_REJECTED event")
    void testHandleOrderRejectedEvent() throws Exception {
        EventType eventType = EventType.ORDER_REJECTED;
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("tradeId", orderId);
        payload.put("accountId", "ACC-001");
        payload.put("symbol", "AAPL");
        payload.put("side", "BUY");
        payload.put("price", new BigDecimal("150.50"));
        payload.put("quantity", 100);
        payload.put("status", "REJECTED");
        payload.put("timeInForce", "GTC");
        payload.put("timestamp", LocalDateTime.now());
        payload.put("reason", "Insufficient funds");

        EventEnvelope envelope = new EventEnvelope(eventId, eventType, Instant.now(), 1, payload);
        String message = objectMapper.writeValueAsString(envelope);

        tradeEventListener.handleTradeEvent(message, 0, 100L);

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Should handle PRICE_UPDATE event")
    void testHandlePriceUpdateEvent() throws Exception {
        EventType eventType = EventType.PRICE_UPDATE;
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("symbol", "AAPL");
        payload.put("bidPrice", new BigDecimal("150.00"));
        payload.put("askPrice", new BigDecimal("151.00"));
        payload.put("timestamp", LocalDateTime.now());

        EventEnvelope envelope = new EventEnvelope(eventId, eventType, Instant.now(), 1, payload);
        String message = objectMapper.writeValueAsString(envelope);

        tradeEventListener.handleTradeEvent(message, 0, 100L);

        verifyNoInteractions(orderRepository, accountRepository, tradeEventPublisher);
    }

    @Test
    @DisplayName("Should throw exception when order not found during cancellation")
    void testHandleCancelledTradeOrderNotFound() throws Exception {
        EventType eventType = EventType.ORDER_CANCELLED;
        UUID orderId = UUID.randomUUID();
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("tradeId", orderId);
        payload.put("accountId", "ACC-001");
        payload.put("symbol", "AAPL");
        payload.put("side", "BUY");
        payload.put("price", new BigDecimal("150.50"));
        payload.put("quantity", 100);
        payload.put("status", "CANCELLED");
        payload.put("timeInForce", "GTC");
        payload.put("timestamp", LocalDateTime.now());
        payload.put("reason", "User requested cancellation");

        EventEnvelope envelope = new EventEnvelope(eventId, eventType, Instant.now(), 1, payload);
        String message = objectMapper.writeValueAsString(envelope);

        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> 
            tradeEventListener.handleTradeEvent(message, 0, 100L)
        );

        verify(orderRepository).findById(orderId);
    }

    @Test
    @DisplayName("Should handle malformed JSON message gracefully")
    void testHandleTradeEventMalformedJSON() {
        String malformedMessage = "{ invalid json }";

        assertThrows(RuntimeException.class, () -> 
            tradeEventListener.handleTradeEvent(malformedMessage, 0, 100L)
        );
    }

    @Test
    @DisplayName("Should handle event with string UUID successfully")
    void testHandleTradeEventWithStringUUID() throws Exception {
        EventType eventType = EventType.ORDER_CANCELLED;
        String orderIdString = UUID.randomUUID().toString();
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("tradeId", orderIdString);  // String UUID
        payload.put("accountId", "ACC-001");
        payload.put("symbol", "AAPL");
        payload.put("side", "BUY");
        payload.put("price", new BigDecimal("150.50"));
        payload.put("quantity", 100);
        payload.put("status", "CANCELLED");
        payload.put("timeInForce", "GTC");
        payload.put("timestamp", LocalDateTime.now());
        payload.put("reason", "User requested cancellation");

        EventEnvelope envelope = new EventEnvelope(eventId, eventType, Instant.now(), 1, payload);
        String message = objectMapper.writeValueAsString(envelope);

        when(orderRepository.findById(any(UUID.class))).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        tradeEventListener.handleTradeEvent(message, 0, 100L);

        verify(orderRepository).save(any(Order.class));
    }

    @Test
    @DisplayName("Should preserve decimal precision for numeric price values")
    void testHandleTradeEventWithNumericPrice() throws Exception {
        EventType eventType = EventType.ORDER_CANCELLED;
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("tradeId", orderId);
        payload.put("accountId", "ACC-001");
        payload.put("symbol", "AAPL");
        payload.put("side", "BUY");
        payload.put("price", 150.1);  // Double price with a non-exact binary representation
        payload.put("quantity", 100);
        payload.put("status", "CANCELLED");
        payload.put("timeInForce", "GTC");
        payload.put("timestamp", LocalDateTime.now());
        payload.put("reason", "User requested cancellation");

        TradeEvent extractedEvent = ReflectionTestUtils.invokeMethod(tradeEventListener, "extractTradeEvent", payload);
        assertNotNull(extractedEvent);
        assertEquals(new BigDecimal("150.1"), extractedEvent.getPrice());

        EventEnvelope envelope = new EventEnvelope(eventId, eventType, Instant.now(), 1, payload);
        String message = objectMapper.writeValueAsString(envelope);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        tradeEventListener.handleTradeEvent(message, 0, 100L);

        verify(orderRepository).save(any(Order.class));
    }
}
