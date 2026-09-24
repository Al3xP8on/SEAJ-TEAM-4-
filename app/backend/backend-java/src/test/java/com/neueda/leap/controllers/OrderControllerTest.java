package com.neueda.leap.controllers;

import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.exceptions.OrderException;
import com.neueda.leap.exceptions.DuplicateOrderException;
import com.neueda.leap.models.Account;
import com.neueda.leap.models.Instrument;
import com.neueda.leap.models.Order;
import com.neueda.leap.models.OrderHistory;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.InstrumentsRepository;
import com.neueda.leap.services.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
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

    private Account testAccount;
    private Instrument testInstrument;
    private Order testOrder;
    private OrderHistory testOrderHistory;

    @BeforeEach
    void setUp() {
        testAccount = new Account("ACC-001", "Test Account", new BigDecimal("50000.00"), AccountStatus.ACTIVE);
        testInstrument = new Instrument("AAPL", "Apple Inc.", "EQUITY", "USD", true);
        testOrder = new Order("order-001", testAccount, testInstrument, 100, new BigDecimal("150.50"), 
                            OrderSide.BUY, "idem-key-001", OrderStatus.NEW, LocalDateTime.now());
        testOrderHistory = new OrderHistory("order-001", OrderStatus.NEW);
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
                .andExpect(jsonPath("$[0].id", equalTo("order-001")));

        verify(orderService, times(1)).getAllOrders();
    }

    @Test
    @DisplayName("Should get order by ID successfully")
    void testGetOrderSuccess() throws Exception {
        when(orderService.getOrderById("order-001")).thenReturn(Optional.of(testOrder));

        mockMvc.perform(get("/v1/orders/order-001")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo("order-001")))
                .andExpect(jsonPath("$.quantity", equalTo(100)))
                .andExpect(jsonPath("$.status", equalTo("NEW")));

        verify(orderService, times(1)).getOrderById("order-001");
    }

    @Test
    @DisplayName("Should return 404 when order not found")
    void testGetOrderNotFound() throws Exception {
        when(orderService.getOrderById("non-existent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/orders/non-existent")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(orderService, times(1)).getOrderById("non-existent");
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
                .andExpect(jsonPath("$[0].id", equalTo("order-001")));

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
        when(orderService.getOrderById("order-001")).thenReturn(Optional.of(testOrder));
        when(orderService.getOrderHistory("order-001")).thenReturn(history);

        mockMvc.perform(get("/v1/orders/order-001/history")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        verify(orderService, times(1)).getOrderById("order-001");
        verify(orderService, times(1)).getOrderHistory("order-001");
    }

    @Test
    @DisplayName("Should return 404 when order history not found")
    void testGetOrderHistoryOrderNotFound() throws Exception {
        when(orderService.getOrderById("non-existent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/orders/non-existent/history")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(orderService, times(1)).getOrderById("non-existent");
        verify(orderService, never()).getOrderHistory(any());
    }

    // ==================== POST Tests ====================

    @Test
    @DisplayName("Should create order successfully")
    void testCreateOrderSuccess() throws Exception {
        Order createdOrder = new Order("order-002", testAccount, testInstrument, 100, new BigDecimal("150.50"),
                                       OrderSide.BUY, "idem-key-002", OrderStatus.NEW, LocalDateTime.now());
        
        when(accountRepository.findByAccountId("ACC-001")).thenReturn(Optional.of(testAccount));
        when(instrumentsRepository.findBySymbol("AAPL")).thenReturn(Optional.of(testInstrument));
        when(orderService.createOrder(eq(testAccount), eq(testInstrument), eq(100L), eq(new BigDecimal("150.50")), 
                                      eq(OrderSide.BUY), anyString())).thenReturn(createdOrder);

        mockMvc.perform(post("/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"ACC-001\",\"symbol\":\"AAPL\",\"quantity\":100,\"price\":150.50}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", equalTo("order-002")))
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
                .content("{\"accountId\":\"ACC-999\",\"symbol\":\"AAPL\",\"quantity\":100,\"price\":150.50}"))
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
                .content("{\"accountId\":\"ACC-001\",\"symbol\":\"XYZ\",\"quantity\":100,\"price\":150.50}"))
                .andExpect(status().isNotFound());

        verify(accountRepository, times(1)).findByAccountId("ACC-001");
        verify(instrumentsRepository, times(1)).findBySymbol("XYZ");
        verify(orderService, never()).createOrder(any(), any(), anyLong(), any(), any(), any());
    }

    // ==================== PUT Tests ====================

    @Test
    @DisplayName("Should execute order successfully")
    void testExecuteOrderSuccess() throws Exception {
        Order executedOrder = new Order("order-001", testAccount, testInstrument, 100, new BigDecimal("150.50"),
                                       OrderSide.BUY, "idem-key-001", OrderStatus.EXECUTED, LocalDateTime.now());
        when(orderService.executeOrder("order-001")).thenReturn(executedOrder);

        mockMvc.perform(put("/v1/orders/order-001/execute")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("EXECUTED")));

        verify(orderService, times(1)).executeOrder("order-001");
    }

    @Test
    @DisplayName("Should return 400 when executing invalid order")
    void testExecuteOrderBadRequest() throws Exception {
        when(orderService.executeOrder("invalid-order"))
                .thenThrow(new OrderException("Cannot execute order", null, "invalid-order"));

        mockMvc.perform(put("/v1/orders/invalid-order/execute")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verify(orderService, times(1)).executeOrder("invalid-order");
    }

    @Test
    @DisplayName("Should cancel order successfully")
    void testCancelOrderSuccess() throws Exception {
        Order cancelledOrder = new Order("order-001", testAccount, testInstrument, 100, new BigDecimal("150.50"),
                                        OrderSide.BUY, "idem-key-001", OrderStatus.CANCELLED, LocalDateTime.now());
        when(orderService.cancelOrder("order-001")).thenReturn(cancelledOrder);

        mockMvc.perform(put("/v1/orders/order-001/cancel")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("CANCELLED")));

        verify(orderService, times(1)).cancelOrder("order-001");
    }

    @Test
    @DisplayName("Should return 400 when cancelling invalid order")
    void testCancelOrderBadRequest() throws Exception {
        when(orderService.cancelOrder("invalid-order"))
                .thenThrow(new OrderException("Cannot cancel order", null, "invalid-order"));

        mockMvc.perform(put("/v1/orders/invalid-order/cancel")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verify(orderService, times(1)).cancelOrder("invalid-order");
    }

    @Test
    @DisplayName("Should reject order successfully")
    void testRejectOrderSuccess() throws Exception {
        Order rejectedOrder = new Order("order-001", testAccount, testInstrument, 100, new BigDecimal("150.50"),
                                       OrderSide.BUY, "idem-key-001", OrderStatus.REJECTED, LocalDateTime.now());
        when(orderService.rejectOrder("order-001", "Insufficient funds")).thenReturn(rejectedOrder);

        mockMvc.perform(put("/v1/orders/order-001/reject")
                .param("reason", "Insufficient funds")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("REJECTED")));

        verify(orderService, times(1)).rejectOrder("order-001", "Insufficient funds");
    }

    @Test
    @DisplayName("Should return 400 when rejecting invalid order")
    void testRejectOrderBadRequest() throws Exception {
        when(orderService.rejectOrder("invalid-order", "Test reason"))
                .thenThrow(new OrderException("Cannot reject order", null, "invalid-order"));

        mockMvc.perform(put("/v1/orders/invalid-order/reject")
                .param("reason", "Test reason")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verify(orderService, times(1)).rejectOrder("invalid-order", "Test reason");
    }

    // ==================== Error Handling Tests ====================

    @Test
    @DisplayName("Should handle internal server error on getOrder")
    void testGetOrderInternalServerError() throws Exception {
        when(orderService.getOrderById("order-001"))
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
        when(orderService.executeOrder("order-001"))
                .thenThrow(new RuntimeException("Unexpected error"));

        mockMvc.perform(put("/v1/orders/order-001/execute")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());
    }
}
