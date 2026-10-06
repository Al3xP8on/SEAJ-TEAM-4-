package com.neueda.leap.controllers;

import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.exceptions.OrderException;
import com.neueda.leap.exceptions.DuplicateOrderException;
import com.neueda.leap.messaging.OrderEventPublisher;
import com.neueda.leap.messaging.TradeEventPublisher;
import com.neueda.leap.models.Account;
import com.neueda.leap.models.Instrument;
import com.neueda.leap.models.Order;
import com.neueda.leap.models.OrderHistory;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.InstrumentsRepository;
import com.neueda.leap.services.OrderService;
import com.neueda.leap.config.TestSecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@Import(TestSecurityConfig.class)
@DisplayName("OrderController Tests")
public class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private AccountRepository accountRepository;

    @MockBean
    private InstrumentsRepository instrumentsRepository;

    @MockBean
    private OrderEventPublisher orderEventPublisher;

    @MockBean
    private TradeEventPublisher tradeEventPublisher;

    private Account testAccount;
    private Instrument testInstrument;
    private Order testOrder;
    private OrderHistory testOrderHistory;
    private UUID testOrderId;
    private UUID invalidOrderId;

    @BeforeEach
    void setUp() {
        testAccount = new Account("ACC-001", "Test Account", "test@example.com", "123456789", new BigDecimal("50000.00"), AccountStatus.ACTIVE);
        testInstrument = new Instrument("AAPL", "Apple Inc.", "EQUITY", "USD", true);
        testOrderId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        invalidOrderId = UUID.fromString("550e8400-e29b-41d4-a716-446655440999");
        testOrder = new Order(testOrderId, testAccount, testInstrument, 100, new BigDecimal("150.50"), 
                            OrderSide.BUY, "idem-key-001", OrderStatus.NEW, LocalDateTime.now());
        testOrderHistory = new OrderHistory(testOrderId, OrderStatus.NEW);
    }

    // ==================== GET Tests ====================

    @Test
    @DisplayName("Should get all orders (admin)")
    void testGetAllOrdersSuccess() throws Exception {
        List<Order> orders = Arrays.asList(testOrder);
        when(orderService.getAllOrders()).thenReturn(orders);

        mockMvc.perform(get("/v1/orders")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", equalTo("550e8400-e29b-41d4-a716-446655440000")));

        verify(orderService, times(1)).getAllOrders();
    }

    @Test
    @DisplayName("Should get order by ID successfully")
    void testGetOrderSuccess() throws Exception {
        UUID testOrderId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        when(orderService.getOrderById(testOrderId)).thenReturn(Optional.of(testOrder));

        mockMvc.perform(get("/v1/orders/550e8400-e29b-41d4-a716-446655440000")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo("550e8400-e29b-41d4-a716-446655440000")))
                .andExpect(jsonPath("$.quantity", equalTo(100)))
                .andExpect(jsonPath("$.status", equalTo("NEW")));

        verify(orderService, times(1)).getOrderById(testOrderId);
    }

    @Test
    @DisplayName("Should return 404 when order not found")
    void testGetOrderNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        when(orderService.getOrderById(nonExistentId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/orders/" + nonExistentId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(orderService, times(1)).getOrderById(nonExistentId);
    }

    @Test
    @DisplayName("Should get all orders by account ID")
    void testGetOrdersByAccountSuccess() throws Exception {
        List<Order> orders = Arrays.asList(testOrder);
        when(orderService.getOrdersByAccount("ACC-001")).thenReturn(orders);

        mockMvc.perform(get("/v1/orders/account/ACC-001")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", equalTo(testOrderId.toString())));

        verify(orderService, times(1)).getOrdersByAccount("ACC-001");
    }

    @Test
    @DisplayName("Should get orders by account ID with status filter")
    void testGetOrdersByAccountWithStatusFilter() throws Exception {
        List<Order> orders = Arrays.asList(testOrder);
        when(orderService.getOrdersByAccount("ACC-001")).thenReturn(orders);

        mockMvc.perform(get("/v1/orders/account/ACC-001")
                .param("status", "NEW")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status", equalTo("NEW")));

        verify(orderService, times(1)).getOrdersByAccount("ACC-001");
    }

    @Test
    @DisplayName("Should return empty list when account has no orders")
    void testGetOrdersByAccountEmpty() throws Exception {
        when(orderService.getOrdersByAccount("ACC-002")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/v1/orders/account/ACC-002")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        verify(orderService, times(1)).getOrdersByAccount("ACC-002");
    }

    @Test
    @DisplayName("Should get order history")
    void testGetOrderHistorySuccess() throws Exception {
        List<OrderHistory> history = Arrays.asList(testOrderHistory);
        when(orderService.getOrderById(testOrderId)).thenReturn(Optional.of(testOrder));
        when(orderService.getOrderHistory(testOrderId)).thenReturn(history);

        mockMvc.perform(get("/v1/orders/" + testOrderId + "/history")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        verify(orderService, times(1)).getOrderById(testOrderId);
        verify(orderService, times(1)).getOrderHistory(testOrderId);
    }

    @Test
    @DisplayName("Should return 404 when order history not found")
    void testGetOrderHistoryOrderNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        when(orderService.getOrderById(nonExistentId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/orders/" + nonExistentId + "/history")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(orderService, times(1)).getOrderById(nonExistentId);
        verify(orderService, never()).getOrderHistory(any());
    }

    // ==================== POST Tests ====================

    @Test
    @DisplayName("Should create order successfully")
    void testCreateOrderSuccess() throws Exception {
        Order createdOrder = new Order(UUID.randomUUID(), testAccount, testInstrument, 100, new BigDecimal("150.50"),
                                       OrderSide.BUY, "idem-key-002", OrderStatus.NEW, LocalDateTime.now());
        
        when(accountRepository.findByAccountId("ACC-001")).thenReturn(Optional.of(testAccount));
        when(instrumentsRepository.findBySymbol("AAPL")).thenReturn(Optional.of(testInstrument));
        when(orderService.createOrder(eq(testAccount), eq(testInstrument), eq(100L), eq(new BigDecimal("150.50")), 
                                      eq(OrderSide.BUY), anyString())).thenReturn(createdOrder);

        mockMvc.perform(post("/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"ACC-001\",\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":100,\"price\":150.50}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", equalTo("NEW")));

        verify(accountRepository, times(1)).findByAccountId("ACC-001");
        verify(instrumentsRepository, times(1)).findBySymbol("AAPL");
        verify(orderService, times(1)).createOrder(eq(testAccount), eq(testInstrument), eq(100L), 
                                                    eq(new BigDecimal("150.50")), eq(OrderSide.BUY), anyString());
    }

    @Test
    @DisplayName("Should return 404 when account not found")
    void testCreateOrderAccountNotFound() throws Exception {
        when(accountRepository.findByAccountId("ACC-999")).thenReturn(Optional.empty());

        mockMvc.perform(post("/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"ACC-999\",\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":100,\"price\":150.50}"))
                .andExpect(status().isNotFound());

        verify(accountRepository, times(1)).findByAccountId("ACC-999");
        verify(instrumentsRepository, never()).findBySymbol(any());
        verify(orderService, never()).createOrder(any(), any(), anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("Should return 404 when instrument not found")
    void testCreateOrderInstrumentNotFound() throws Exception {
        when(accountRepository.findByAccountId("ACC-001")).thenReturn(Optional.of(testAccount));
        when(instrumentsRepository.findBySymbol("XYZ")).thenReturn(Optional.empty());

        mockMvc.perform(post("/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"ACC-001\",\"symbol\":\"XYZ\",\"side\":\"BUY\",\"quantity\":100,\"price\":150.50}"))
                .andExpect(status().isNotFound());

        verify(accountRepository, times(1)).findByAccountId("ACC-001");
        verify(instrumentsRepository, times(1)).findBySymbol("XYZ");
        verify(orderService, never()).createOrder(any(), any(), anyLong(), any(), any(), any());
    }

    // ==================== PUT Tests ====================

    @Test
    @DisplayName("Should execute order successfully")
    void testExecuteOrderSuccess() throws Exception {
        Order executedOrder = new Order(testOrderId, testAccount, testInstrument, 100, new BigDecimal("150.50"),
                                       OrderSide.BUY, "idem-key-001", OrderStatus.FILLED, LocalDateTime.now());
        when(orderService.executeOrder(testOrderId)).thenReturn(executedOrder);

        mockMvc.perform(put("/v1/orders/" + testOrderId + "/execute")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("FILLED")));

        verify(orderService, times(1)).executeOrder(testOrderId);
    }

    @Test
    @DisplayName("Should return 400 when executing invalid order")
    void testExecuteOrderBadRequest() throws Exception {
        when(orderService.executeOrder(invalidOrderId))
                .thenThrow(new OrderException("Cannot execute order", null, invalidOrderId.toString()));

        mockMvc.perform(put("/v1/orders/" + invalidOrderId + "/execute")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verify(orderService, times(1)).executeOrder(invalidOrderId);
    }

    @Test
    @DisplayName("Should cancel order successfully")
    void testCancelOrderSuccess() throws Exception {
        Order cancelledOrder = new Order(testOrderId, testAccount, testInstrument, 100, new BigDecimal("150.50"),
                                        OrderSide.BUY, "idem-key-001", OrderStatus.CANCELLED, LocalDateTime.now());
        when(orderService.cancelOrder(testOrderId)).thenReturn(cancelledOrder);

        mockMvc.perform(put("/v1/orders/" + testOrderId + "/cancel")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("CANCELLED")));

        verify(orderService, times(1)).cancelOrder(testOrderId);
    }

    @Test
    @DisplayName("Should return 400 when cancelling invalid order")
    void testCancelOrderBadRequest() throws Exception {
        when(orderService.cancelOrder(invalidOrderId))
                .thenThrow(new OrderException("Cannot cancel order", null, invalidOrderId.toString()));

        mockMvc.perform(put("/v1/orders/" + invalidOrderId + "/cancel")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verify(orderService, times(1)).cancelOrder(invalidOrderId);
    }

    @Test
    @DisplayName("Should reject order successfully")
    void testRejectOrderSuccess() throws Exception {
        Order rejectedOrder = new Order(testOrderId, testAccount, testInstrument, 100, new BigDecimal("150.50"),
                                       OrderSide.BUY, "idem-key-001", OrderStatus.REJECTED, LocalDateTime.now());
        when(orderService.rejectOrder(testOrderId, "Insufficient funds")).thenReturn(rejectedOrder);

        mockMvc.perform(put("/v1/orders/" + testOrderId + "/reject")
                .param("reason", "Insufficient funds")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("REJECTED")));

        verify(orderService, times(1)).rejectOrder(testOrderId, "Insufficient funds");
    }

    @Test
    @DisplayName("Should return 400 when rejecting invalid order")
    void testRejectOrderBadRequest() throws Exception {
        when(orderService.rejectOrder(invalidOrderId, "Test reason"))
                .thenThrow(new OrderException("Cannot reject order", null, invalidOrderId.toString()));

        mockMvc.perform(put("/v1/orders/" + invalidOrderId + "/reject")
                .param("reason", "Test reason")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verify(orderService, times(1)).rejectOrder(invalidOrderId, "Test reason");
    }

    // ==================== Error Handling Tests ====================

    @Test
    @DisplayName("Should handle internal server error on getOrder")
    void testGetOrderInternalServerError() throws Exception {
        when(orderService.getOrderById(testOrderId))
                .thenThrow(new RuntimeException("Database connection failed"));

        mockMvc.perform(get("/v1/orders/order-001")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("Should handle internal server error on getOrdersByAccount")
    void testGetOrdersByAccountInternalServerError() throws Exception {
        when(orderService.getOrdersByAccount("ACC-001"))
                .thenThrow(new RuntimeException("Database connection failed"));

        mockMvc.perform(get("/v1/orders/account/ACC-001")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("Should handle internal server error on executeOrder")
    void testExecuteOrderInternalServerError() throws Exception {
        when(orderService.executeOrder(testOrderId))
                .thenThrow(new RuntimeException("Unexpected error"));

        mockMvc.perform(put("/v1/orders/" + testOrderId + "/execute")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());
    }
}
