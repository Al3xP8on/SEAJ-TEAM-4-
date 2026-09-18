package com.neueda.leap.models;

import com.neueda.leap.models.Account;
import com.neueda.leap.models.Instrument;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.exceptions.OrderException;
import com.neueda.leap.enums.AccountStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Order Tests")
public class OrdersTest {

    @Test
    @DisplayName("Should create an order successfully")
    public void testCreateOrder() {
        // Arrange - Create real test fixtures
        Account account = new Account("ACC-001", "Test Account", new BigDecimal("50000.00"), AccountStatus.ACTIVE);
        Instrument instrument = new Instrument("AAPL", "Apple Inc.", "EQUITY", "USD", true);
        BigDecimal price = new BigDecimal("150.50");
        long quantity = 100;
        
        // Act
        Order order = new Order(account, instrument, quantity, price, 
                               OrderSide.BUY, "test-key-001");
        
        // Assert
        assertNotNull(order.getId());
        assertEquals(OrderStatus.NEW, order.getStatus());
        assertEquals(quantity, order.getQuantity());
        assertEquals(price, order.getPrice());
        assertEquals(OrderSide.BUY, order.getSide());
    }

    @Test
    @DisplayName("Should execute a BUY order successfully")
    public void testExecuteBuyOrder() {
        Account account = new Account("ACC-002", "Buyer", new BigDecimal("10000.00"), AccountStatus.ACTIVE);
        Instrument instrument = new Instrument("TSLA", "Tesla", "EQUITY", "USD", true);
        
        Order order = new Order(account, instrument, 50, new BigDecimal("100.00"), 
                               OrderSide.BUY, "exec-buy-001");
        order.execute();
        
        assertEquals(OrderStatus.EXECUTED, order.getStatus());
    }

    @Test
    @DisplayName("Should execute a SELL order successfully")
    public void testExecuteSellOrder() {
        Account account = new Account("ACC-003", "Seller", new BigDecimal("5000.00"), AccountStatus.ACTIVE);
        Instrument instrument = new Instrument("GOOGL", "Google", "EQUITY", "USD", true);
        
        Order order = new Order(account, instrument, 100, new BigDecimal("75.00"), 
                               OrderSide.SELL, "exec-sell-001");
        order.execute();
        
        assertEquals(OrderStatus.EXECUTED, order.getStatus());
    }

    @Test
    @DisplayName("Should cancel order in NEW state")
    public void testCancelOrder() {
        Account account = new Account("ACC-004", "Cancel Test", new BigDecimal("20000.00"), AccountStatus.ACTIVE);
        Instrument instrument = new Instrument("MSFT", "Microsoft", "EQUITY", "USD", true);
        
        Order order = new Order(account, instrument, 75, new BigDecimal("200.00"), 
                               OrderSide.BUY, "cancel-001");
        order.cancel();
        
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    @Test
    @DisplayName("Should calculate total value correctly")
    public void testCalculateTotalValue() {
        Account account = new Account("ACC-005", "Calc Test", new BigDecimal("100000.00"), AccountStatus.ACTIVE);
        Instrument instrument = new Instrument("AMZN", "Amazon", "EQUITY", "USD", true);
        
        Order order = new Order(account, instrument, 100, new BigDecimal("50.00"), 
                               OrderSide.BUY, "calc-001");
        
        assertEquals(new BigDecimal("5000.00"), order.calculateTotalValue());
    }

    @Test
    @DisplayName("Should reject order with insufficient funds")
    public void testRejectInsufficientFunds() {
        Account poorAccount = new Account("POOR-001", "Poor", new BigDecimal("100.00"), AccountStatus.ACTIVE);
        Instrument instrument = new Instrument("TEST", "Test", "EQUITY", "USD", true);
        
        Order order = new Order(poorAccount, instrument, 100, new BigDecimal("100.00"), 
                               OrderSide.BUY, "poor-001");
        
        assertThrows(OrderException.class, order::execute);
        assertEquals(OrderStatus.REJECTED, order.getStatus());
    }

    @Test
    @DisplayName("Should reject order from inactive account")
    public void testRejectInactiveAccount() {
        Account inactiveAccount = new Account("INACTIVE-001", "Inactive", new BigDecimal("10000.00"), AccountStatus.SUSPENDED);
        Instrument instrument = new Instrument("TEST", "Test", "EQUITY", "USD", true);
        
        assertThrows(OrderException.class, () ->
            new Order(inactiveAccount, instrument, 50, new BigDecimal("100.00"), 
                     OrderSide.BUY, "inactive-001")
        );
    }

    @Test
    @DisplayName("Should reject non-tradable instrument")
    public void testRejectNonTradableInstrument() {
        Account account = new Account("ACC-006", "Non-Tradable Test", new BigDecimal("10000.00"), AccountStatus.ACTIVE);
        Instrument nonTradable = new Instrument("BOND", "Bond", "BOND", "USD", false);
        
        assertThrows(OrderException.class, () ->
            new Order(account, nonTradable, 50, new BigDecimal("100.00"), 
                     OrderSide.BUY, "ntrd-001")
        );
    }

    @Test
    @DisplayName("Should reject zero quantity")
    public void testRejectZeroQuantity() {
        Account account = new Account("ACC-007", "Zero Qty Test", new BigDecimal("10000.00"), AccountStatus.ACTIVE);
        Instrument instrument = new Instrument("TEST", "Test", "EQUITY", "USD", true);
        
        assertThrows(OrderException.class, () ->
            new Order(account, instrument, 0, new BigDecimal("100.00"), 
                     OrderSide.BUY, "zero-qty-001")
        );
    }

    @Test
    @DisplayName("Should reject negative price")
    public void testRejectNegativePrice() {
        Account account = new Account("ACC-008", "Neg Price Test", new BigDecimal("10000.00"), AccountStatus.ACTIVE);
        Instrument instrument = new Instrument("TEST", "Test", "EQUITY", "USD", true);
        
        assertThrows(OrderException.class, () ->
            new Order(account, instrument, 50, new BigDecimal("-100.00"), 
                     OrderSide.BUY, "neg-price-001")
        );
    }

    @Test
    @DisplayName("Should reject null account")
    public void testRejectNullAccount() {
        Instrument instrument = new Instrument("TEST", "Test", "EQUITY", "USD", true);
        
        assertThrows(NullPointerException.class, () ->
            new Order(null, instrument, 50, new BigDecimal("100.00"), 
                     OrderSide.BUY, "null-acc-001")
        );
    }

    @Test
    @DisplayName("Should not allow cancellation of executed order")
    public void testCannotCancelExecutedOrder() {
        Account account = new Account("ACC-009", "Exec Cancel Test", new BigDecimal("10000.00"), AccountStatus.ACTIVE);
        Instrument instrument = new Instrument("TEST", "Test", "EQUITY", "USD", true);
        
        Order order = new Order(account, instrument, 50, new BigDecimal("100.00"), 
                               OrderSide.BUY, "exec-cancel-001");
        order.execute();
        
        assertThrows(OrderException.class, order::cancel);
    }

    @Test
    @DisplayName("Should verify order equality by ID")
    public void testOrderEqualityById() {
        Account account = new Account("ACC-010", "Equality Test", new BigDecimal("10000.00"), AccountStatus.ACTIVE);
        Instrument instrument = new Instrument("TEST", "Test", "EQUITY", "USD", true);
        
        Order order1 = new Order(account, instrument, 50, new BigDecimal("100.00"), 
                                OrderSide.BUY, "eq-001");
        String id = order1.getId();
        
        Order order2 = new Order(id, account, instrument, 50, new BigDecimal("100.00"), 
                                OrderSide.BUY, "eq-001", OrderStatus.NEW, order1.getCreatedAt());
        
        assertEquals(order1, order2);
    }
}
