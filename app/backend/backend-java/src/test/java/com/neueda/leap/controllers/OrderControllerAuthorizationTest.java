package com.neueda.leap.controllers;

import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.exceptions.DuplicateOrderException;
import com.neueda.leap.exceptions.OrderException;
import com.neueda.leap.models.Account;
import com.neueda.leap.models.Instrument;
import com.neueda.leap.models.Order;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.InstrumentsRepository;
import com.neueda.leap.security.AuthorizationException;
import com.neueda.leap.security.SecurityUtilsService;
import com.neueda.leap.services.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderController Authorization Unit Tests")
public class OrderControllerAuthorizationTest {

    @Mock
    private OrderService orderService;

    @Mock
    private SecurityUtilsService securityUtilsService;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private InstrumentsRepository instrumentsRepository;

    @InjectMocks
    private OrderController orderController;

    private Account aliceAccount;
    private Account brianAccount;
    private Instrument testInstrument;
    private Order aliceOrder;
    private PlaceOrderRequest orderRequest;

    @BeforeEach
    void setUp() {
        aliceAccount = new Account("ACC-0001", "alice", "SecurePass123!", "Alice Johnson",
                "alice@example.com", null, new BigDecimal("10000.00"), AccountStatus.ACTIVE);

        brianAccount = new Account("ACC-0002", "brian", "SecurePass123!", "Brian Smith",
                "brian@example.com", null, new BigDecimal("5000.00"), AccountStatus.ACTIVE);

        testInstrument = new Instrument("AAPL", "Apple Inc.", "EQUITY", "USD", true);

        UUID orderId = UUID.randomUUID();
        aliceOrder = new Order(orderId, aliceAccount, testInstrument, 10,
                new BigDecimal("150.50"), OrderSide.BUY, "idem-key-001", OrderStatus.NEW, LocalDateTime.now());

        orderRequest = new PlaceOrderRequest();
        orderRequest.setAccountId("ACC-0001");
        orderRequest.setSymbol("AAPL");
        orderRequest.setQuantity(10);
        orderRequest.setPrice(new BigDecimal("150.50"));
        orderRequest.setSide(OrderSide.BUY);
    }

    @Test
    @DisplayName("Should validate account ownership before creating order")
    void testCreateOrderValidatesAccountOwnership() throws Exception {
        doNothing().when(securityUtilsService).validateAccountOwnership("ACC-0001");
        when(accountRepository.findByAccountId("ACC-0001")).thenReturn(Optional.of(aliceAccount));
        when(instrumentsRepository.findBySymbol("AAPL")).thenReturn(Optional.of(testInstrument));
        when(orderService.createOrder(any(), any(), anyLong(), any(), any(), any())).thenReturn(aliceOrder);

        orderRequest.setAccountId("ACC-0001");
        orderController.createOrder(orderRequest);

        verify(securityUtilsService, times(1)).validateAccountOwnership("ACC-0001");
    }

    @Test
    @DisplayName("Should throw AuthorizationException when creating order for unauthorized account")
    void testCreateOrderThrowsAuthorizationException() throws Exception {
        AuthorizationException authException = new AuthorizationException("User does not have permission to access account: ACC-0002");
        doThrow(authException).when(securityUtilsService).validateAccountOwnership("ACC-0002");

        orderRequest.setAccountId("ACC-0002");
        
        try {
            orderController.createOrder(orderRequest);
            fail("Expected AuthorizationException to be thrown");
        } catch (AuthorizationException e) {
            assertTrue(e.getMessage().contains("ACC-0002"));
        }

        verify(securityUtilsService).validateAccountOwnership("ACC-0002");
        verify(orderService, never()).createOrder(any(), any(), anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("Should validate order ownership when getting single order")
    void testGetOrderValidatesOwnership() {
        UUID orderId = aliceOrder.getId();
        when(orderService.getOrderById(orderId)).thenReturn(Optional.of(aliceOrder));
        doNothing().when(securityUtilsService).validateOrderOwnership(aliceOrder);

        orderController.getOrder(orderId);

        verify(securityUtilsService).validateOrderOwnership(aliceOrder);
    }

    @Test
    @DisplayName("Should throw AuthorizationException when accessing unauthorized order")
    void testGetOrderThrowsAuthorizationException() {
        UUID orderId = aliceOrder.getId();
        when(orderService.getOrderById(orderId)).thenReturn(Optional.of(aliceOrder));
        doThrow(new AuthorizationException("User does not have permission to access this order"))
                .when(securityUtilsService)
                .validateOrderOwnership(aliceOrder);

        assertThrows(AuthorizationException.class, () -> {
            orderController.getOrder(orderId);
        });

        verify(securityUtilsService).validateOrderOwnership(aliceOrder);
    }

    @Test
    @DisplayName("Should validate account ownership when listing orders by account")
    void testGetOrdersByAccountValidatesOwnership() {
        when(securityUtilsService.isAdmin()).thenReturn(false);
        doNothing().when(securityUtilsService).validateAccountOwnership("ACC-0001");
        when(orderService.getOrdersByAccount("ACC-0001")).thenReturn(java.util.Arrays.asList());

        orderController.getOrdersByAccount("ACC-0001", null, null, null);

        verify(securityUtilsService).validateAccountOwnership("ACC-0001");
    }

    @Test
    @DisplayName("Should throw AuthorizationException when listing orders for unauthorized account")
    void testGetOrdersByAccountThrowsAuthorizationException() {
        doThrow(new AuthorizationException("User does not have permission to access account: ACC-0002"))
                .when(securityUtilsService)
                .validateAccountOwnership("ACC-0002");

        assertThrows(AuthorizationException.class, () -> {
            orderController.getOrdersByAccount("ACC-0002", null, null, null);
        });

        verify(securityUtilsService).validateAccountOwnership("ACC-0002");
    }

    @Test
    @DisplayName("Should validate order ownership when executing order")
    void testExecuteOrderValidatesOwnership() {
        UUID orderId = aliceOrder.getId();
        when(orderService.executeOrder(orderId)).thenReturn(aliceOrder);
        doNothing().when(securityUtilsService).validateOrderOwnership(aliceOrder);

        orderController.executeOrder(orderId);

        verify(securityUtilsService).validateOrderOwnership(aliceOrder);
    }

    @Test
    @DisplayName("Should throw AuthorizationException when executing unauthorized order")
    void testExecuteOrderThrowsAuthorizationException() {
        UUID orderId = aliceOrder.getId();
        when(orderService.executeOrder(orderId)).thenReturn(aliceOrder);
        doThrow(new AuthorizationException("User does not have permission to access this order"))
                .when(securityUtilsService)
                .validateOrderOwnership(aliceOrder);

        assertThrows(AuthorizationException.class, () -> {
            orderController.executeOrder(orderId);
        });

        verify(securityUtilsService).validateOrderOwnership(aliceOrder);
    }

    @Test
    @DisplayName("Should validate order ownership when cancelling order")
    void testCancelOrderValidatesOwnership() {
        UUID orderId = aliceOrder.getId();
        when(orderService.cancelOrder(orderId)).thenReturn(aliceOrder);
        doNothing().when(securityUtilsService).validateOrderOwnership(aliceOrder);

        orderController.cancelOrder(orderId);

        verify(securityUtilsService).validateOrderOwnership(aliceOrder);
    }

    @Test
    @DisplayName("Should throw AuthorizationException when cancelling unauthorized order")
    void testCancelOrderThrowsAuthorizationException() {
        UUID orderId = aliceOrder.getId();
        when(orderService.cancelOrder(orderId)).thenReturn(aliceOrder);
        doThrow(new AuthorizationException("User does not have permission to access this order"))
                .when(securityUtilsService)
                .validateOrderOwnership(aliceOrder);

        assertThrows(AuthorizationException.class, () -> {
            orderController.cancelOrder(orderId);
        });

        verify(securityUtilsService).validateOrderOwnership(aliceOrder);
    }

    @Test
    @DisplayName("Should validate order ownership when getting order history")
    void testGetOrderHistoryValidatesOwnership() {
        UUID orderId = aliceOrder.getId();
        when(orderService.getOrderById(orderId)).thenReturn(Optional.of(aliceOrder));
        doNothing().when(securityUtilsService).validateOrderOwnership(aliceOrder);
        when(orderService.getOrderHistory(orderId)).thenReturn(java.util.Arrays.asList());

        orderController.getOrderHistory(orderId);

        verify(securityUtilsService).validateOrderOwnership(aliceOrder);
    }

    @Test
    @DisplayName("Should throw AuthorizationException when accessing unauthorized order history")
    void testGetOrderHistoryThrowsAuthorizationException() {
        UUID orderId = aliceOrder.getId();
        when(orderService.getOrderById(orderId)).thenReturn(Optional.of(aliceOrder));
        doThrow(new AuthorizationException("User does not have permission to access this order"))
                .when(securityUtilsService)
                .validateOrderOwnership(aliceOrder);

        assertThrows(AuthorizationException.class, () -> {
            orderController.getOrderHistory(orderId);
        });

        verify(securityUtilsService).validateOrderOwnership(aliceOrder);
    }
}
