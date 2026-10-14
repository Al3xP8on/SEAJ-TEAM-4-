package com.neueda.leap.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.models.Account;
import com.neueda.leap.models.Instrument;
import com.neueda.leap.models.Order;
import com.neueda.leap.models.OrderHistory;
import com.neueda.leap.repositories.OrderHistoryRepository;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.services.OrderPricingValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;
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

@DisplayName("TradesConsumer Tests")
public class TradesConsumerTest {

    @InjectMocks
    private TradesConsumer tradesConsumer;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderPricingValidator pricingValidator;

    @Mock
    private TradeEventPublisher tradeEventPublisher;

    @Mock
    private OrderHistoryRepository history;

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
                OrderSide.BUY, "idem-key", OrderStatus.NEW, LocalDateTime.now());
    }

    @Test
    @DisplayName("Should process ORDER_PLACED event and approve order")
    void testHandleOrderPlacedApprovedOrder() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId);
        payload.put("accountId", "ACC-001");
        payload.put("symbol", "AAPL");
        payload.put("side", "BUY");
        payload.put("price", "150.50");
        payload.put("quantity", 100);
        payload.put("status", "NEW");
        payload.put("timeInForce", "GTC");
        payload.put("timestamp", LocalDateTime.now());

        EventEnvelope envelope = new EventEnvelope(eventId, EventType.ORDER_PLACED, Instant.now(), 1, payload);
        String message = objectMapper.writeValueAsString(envelope);

        OrderPricingValidator.PricingValidationResult approvalResult = 
            new OrderPricingValidator.PricingValidationResult(OrderStatus.PENDING, "Approved for execution");

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(pricingValidator.validateOrderPricing(order)).thenReturn(approvalResult);
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        tradesConsumer.handleOrderPlaced(message, 0, 100L);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertEquals(OrderStatus.PENDING, orderCaptor.getValue().getStatus());
        verify(tradeEventPublisher).publishTradeEvent(any(TradeEvent.class));
    }

    @Test
    @DisplayName("Should process ORDER_PLACED event and reject order")
    void testHandleOrderPlacedRejectedOrder() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId);
        payload.put("accountId", "ACC-001");
        payload.put("symbol", "AAPL");
        payload.put("side", "BUY");
        payload.put("price", "150.50");
        payload.put("quantity", 100);
        payload.put("status", "NEW");
        payload.put("timeInForce", "GTC");
        payload.put("timestamp", LocalDateTime.now());

        EventEnvelope envelope = new EventEnvelope(eventId, EventType.ORDER_PLACED, Instant.now(), 1, payload);
        String message = objectMapper.writeValueAsString(envelope);

        OrderPricingValidator.PricingValidationResult rejectionResult = 
            new OrderPricingValidator.PricingValidationResult(OrderStatus.REJECTED, "Insufficient funds");

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(pricingValidator.validateOrderPricing(order)).thenReturn(rejectionResult);
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        tradesConsumer.handleOrderPlaced(message, 0, 100L);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertEquals(OrderStatus.REJECTED, orderCaptor.getValue().getStatus());
        var historyCaptor = ArgumentCaptor.forClass(OrderHistory.class);
        verify(history).save(historyCaptor.capture());
        assertEquals(orderId, historyCaptor.getValue().getOrderId());
        assertEquals(OrderStatus.REJECTED, historyCaptor.getValue().getStatus());
        assertEquals("Insufficient funds", historyCaptor.getValue().getReason());
        verify(tradeEventPublisher).publishTradeEvent(any(TradeEvent.class));
    }

    @Test
    @DisplayName("Should skip processing if order is already in PENDING status")
    void testHandleOrderPlacedAlreadyProcessed() throws Exception {
        order.setStatus(OrderStatus.PENDING);
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId);
        payload.put("accountId", "ACC-001");
        payload.put("symbol", "AAPL");
        payload.put("side", "BUY");
        payload.put("price", "150.50");
        payload.put("quantity", 100);
        payload.put("status", "PENDING");
        payload.put("timeInForce", "GTC");
        payload.put("timestamp", LocalDateTime.now());

        EventEnvelope envelope = new EventEnvelope(eventId, EventType.ORDER_PLACED, Instant.now(), 1, payload);
        String message = objectMapper.writeValueAsString(envelope);

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        tradesConsumer.handleOrderPlaced(message, 0, 100L);

        verify(pricingValidator, never()).validateOrderPricing(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when order not found")
    void testHandleOrderPlacedOrderNotFound() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId);
        payload.put("accountId", "ACC-001");
        payload.put("symbol", "AAPL");
        payload.put("side", "BUY");
        payload.put("price", "150.50");
        payload.put("quantity", 100);
        payload.put("status", "NEW");
        payload.put("timeInForce", "GTC");
        payload.put("timestamp", LocalDateTime.now());

        EventEnvelope envelope = new EventEnvelope(eventId, EventType.ORDER_PLACED, Instant.now(), 1, payload);
        String message = objectMapper.writeValueAsString(envelope);

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> 
            tradesConsumer.handleOrderPlaced(message, 0, 100L)
        );

        verify(orderRepository).findByIdForUpdate(orderId);
        verify(pricingValidator, never()).validateOrderPricing(any());
    }

    @Test
    @DisplayName("Should ignore non-ORDER_PLACED events")
    void testHandleOrderPlacedIgnoreOtherEventTypes() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId);
        payload.put("accountId", "ACC-001");
        payload.put("symbol", "AAPL");

        EventEnvelope envelope = new EventEnvelope(eventId, EventType.PRICE_UPDATE, Instant.now(), 1, payload);
        String message = objectMapper.writeValueAsString(envelope);

        tradesConsumer.handleOrderPlaced(message, 0, 100L);

        verify(orderRepository, never()).findById(any());
        verify(pricingValidator, never()).validateOrderPricing(any());
    }

    @Test
    @DisplayName("Should handle malformed JSON message gracefully")
    void testHandleOrderPlacedMalformedJSON() {
        String malformedMessage = "{ invalid json }";

        assertThrows(RuntimeException.class, () -> 
            tradesConsumer.handleOrderPlaced(malformedMessage, 0, 100L)
        );
    }

    @Test
    @DisplayName("Should handle missing orderId in payload")
    void testHandleOrderPlacedMissingOrderId() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("accountId", "ACC-001");
        payload.put("symbol", "AAPL");
        payload.put("side", "BUY");
        payload.put("price", "150.50");
        payload.put("quantity", 100);

        EventEnvelope envelope = new EventEnvelope(eventId, EventType.ORDER_PLACED, Instant.now(), 1, payload);
        String message = objectMapper.writeValueAsString(envelope);

        assertThrows(RuntimeException.class, () -> 
            tradesConsumer.handleOrderPlaced(message, 0, 100L)
        );
    }

    @Test
    @DisplayName("Should publish ORDER_ACCEPTED event when order is approved")
    void testPublishOrderAcceptedEvent() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId);
        payload.put("accountId", "ACC-001");
        payload.put("symbol", "AAPL");
        payload.put("side", "BUY");
        payload.put("price", "150.50");
        payload.put("quantity", 100);
        payload.put("status", "NEW");
        payload.put("timeInForce", "GTC");
        payload.put("timestamp", LocalDateTime.now());

        EventEnvelope envelope = new EventEnvelope(eventId, EventType.ORDER_PLACED, Instant.now(), 1, payload);
        String message = objectMapper.writeValueAsString(envelope);

        OrderPricingValidator.PricingValidationResult approvalResult = 
            new OrderPricingValidator.PricingValidationResult(OrderStatus.PENDING, "Approved");

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(pricingValidator.validateOrderPricing(order)).thenReturn(approvalResult);
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        tradesConsumer.handleOrderPlaced(message, 0, 100L);

        ArgumentCaptor<TradeEvent> eventCaptor = ArgumentCaptor.forClass(TradeEvent.class);
        verify(tradeEventPublisher).publishTradeEvent(eventCaptor.capture());
        TradeEvent publishedEvent = eventCaptor.getValue();
        assertEquals("ACCEPTED", publishedEvent.getStatus());
    }

    @Test
    @DisplayName("Should publish ORDER_REJECTED event when order is rejected")
    void testPublishOrderRejectedEvent() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId);
        payload.put("accountId", "ACC-001");
        payload.put("symbol", "AAPL");
        payload.put("side", "BUY");
        payload.put("price", "150.50");
        payload.put("quantity", 100);
        payload.put("status", "NEW");
        payload.put("timeInForce", "GTC");
        payload.put("timestamp", LocalDateTime.now());

        EventEnvelope envelope = new EventEnvelope(eventId, EventType.ORDER_PLACED, Instant.now(), 1, payload);
        String message = objectMapper.writeValueAsString(envelope);

        OrderPricingValidator.PricingValidationResult rejectionResult = 
            new OrderPricingValidator.PricingValidationResult(OrderStatus.REJECTED, "Instrument not tradeable");

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(pricingValidator.validateOrderPricing(order)).thenReturn(rejectionResult);
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        tradesConsumer.handleOrderPlaced(message, 0, 100L);

        ArgumentCaptor<TradeEvent> eventCaptor = ArgumentCaptor.forClass(TradeEvent.class);
        verify(tradeEventPublisher).publishTradeEvent(eventCaptor.capture());
        TradeEvent publishedEvent = eventCaptor.getValue();
        assertEquals("REJECTED", publishedEvent.getStatus());
        assertEquals("Instrument not tradeable", publishedEvent.getReason());
    }

    @Test
    @DisplayName("Should handle FILLED order status without reprocessing")
    void testHandleOrderPlacedAlreadyFilled() throws Exception {
        order.setStatus(OrderStatus.FILLED);
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId);
        payload.put("accountId", "ACC-001");
        payload.put("symbol", "AAPL");
        payload.put("side", "BUY");
        payload.put("price", "150.50");
        payload.put("quantity", 100);
        payload.put("status", "FILLED");
        payload.put("timeInForce", "GTC");
        payload.put("timestamp", LocalDateTime.now());

        EventEnvelope envelope = new EventEnvelope(eventId, EventType.ORDER_PLACED, Instant.now(), 1, payload);
        String message = objectMapper.writeValueAsString(envelope);

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        tradesConsumer.handleOrderPlaced(message, 0, 100L);

        verify(pricingValidator, never()).validateOrderPricing(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should handle REJECTED order status without reprocessing")
    void testHandleOrderPlacedAlreadyRejected() throws Exception {
        order.setStatus(OrderStatus.REJECTED);
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId);
        payload.put("accountId", "ACC-001");
        payload.put("symbol", "AAPL");
        payload.put("side", "BUY");
        payload.put("price", "150.50");
        payload.put("quantity", 100);
        payload.put("status", "REJECTED");
        payload.put("timeInForce", "GTC");
        payload.put("timestamp", LocalDateTime.now());

        EventEnvelope envelope = new EventEnvelope(eventId, EventType.ORDER_PLACED, Instant.now(), 1, payload);
        String message = objectMapper.writeValueAsString(envelope);

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        tradesConsumer.handleOrderPlaced(message, 0, 100L);

        verify(pricingValidator, never()).validateOrderPricing(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void preservesTheReasonWhenEventPublicationFailsAndSkipsDuplicateValidation() throws Exception {
        String reason = "No live quote available for symbol: AAPL";
        var envelope = new EventEnvelope(UUID.randomUUID(), EventType.ORDER_PLACED, Instant.now(), 1,
            Map.of("orderId", orderId.toString(), "accountId", "ACC-001", "symbol", "AAPL",
                "side", "BUY", "price", "150.50", "quantity", 100));
        String message = objectMapper.writeValueAsString(envelope);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(pricingValidator.validateOrderPricing(order)).thenReturn(new OrderPricingValidator.PricingValidationResult(OrderStatus.REJECTED, reason));
        doThrow(new RuntimeException("Kafka offline")).when(tradeEventPublisher).publishTradeEvent(any());
        tradesConsumer.handleOrderPlaced(message, 0, 1L);
        tradesConsumer.handleOrderPlaced(message, 0, 1L);
        var capture = ArgumentCaptor.forClass(OrderHistory.class);
        verify(history).save(capture.capture());
        assertEquals(reason, capture.getValue().getReason());
        assertEquals(OrderStatus.REJECTED, capture.getValue().getStatus());
        verify(pricingValidator, times(1)).validateOrderPricing(order);
    }
}
