package com.neueda.leap.services;

import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.messaging.LatestPriceStore;
import com.neueda.leap.messaging.MarketPrice;
import com.neueda.leap.models.Account;
import com.neueda.leap.models.Instrument;
import com.neueda.leap.models.Order;
import com.neueda.leap.repositories.AccountRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("OrderPricingValidator Tests")
public class OrderPricingValidatorTest {

    @InjectMocks
    private OrderPricingValidator orderPricingValidator;

    @Mock
    private LatestPriceStore latestPriceStore;

    @Mock
    private AccountRepository accountRepository;

    private Account account;
    private Instrument instrument;
    private Order order;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        account = new Account("ACC-001", "testuser", "password", "Test Account", "test@example.com", "1234567890",
                new BigDecimal("50000.00"), com.neueda.leap.enums.AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(account, "id", 1L);
        
        instrument = new Instrument("AAPL", "Apple Inc.", "EQUITY", "USD", true);
        
        order = new Order(UUID.randomUUID(), account, instrument, 100, new BigDecimal("150.50"),
                OrderSide.BUY, "idem-key", OrderStatus.NEW, LocalDateTime.now());
    }

    @Test
    @DisplayName("Should reject order for non-tradeable instrument")
    void testValidateOrderNonTradeableInstrument() {
        ReflectionTestUtils.setField(instrument, "tradable", false);
        
        OrderPricingValidator.PricingValidationResult result = orderPricingValidator.validateOrderPricing(order);
        
        assertEquals(OrderStatus.REJECTED, result.newStatus());
        assertTrue(result.reason().contains("not tradeable"));
    }

    @Test
    @DisplayName("Should reject order when no live quote available")
    void testValidateOrderNoLiveQuote() {
        when(latestPriceStore.getPriceIfNotStale("AAPL")).thenReturn(Optional.empty());
        
        OrderPricingValidator.PricingValidationResult result = orderPricingValidator.validateOrderPricing(order);
        
        assertEquals(OrderStatus.REJECTED, result.newStatus());
        assertTrue(result.reason().contains("No live quote available"));
    }

    @Test
    @DisplayName("Should approve BUY order when quote price is at or below order price")
    void testValidateBuyOrderApproved() {
        MarketPrice marketPrice = new MarketPrice("AAPL", new BigDecimal("149.00"), new BigDecimal("151.00"), new BigDecimal("149.00"), Instant.now());
        when(latestPriceStore.getPriceIfNotStale("AAPL")).thenReturn(Optional.of(marketPrice));
        when(accountRepository.findByAccountId("ACC-001")).thenReturn(Optional.of(account));
        
        OrderPricingValidator.PricingValidationResult result = orderPricingValidator.validateOrderPricing(order);
        
        assertEquals(OrderStatus.PENDING, result.newStatus());
        assertEquals("BUY execution at quote", result.reason());
    }

    @Test
    @DisplayName("Should reject BUY order when quote price is above order price")
    void testValidateBuyOrderRejectedHighQuote() {
        ReflectionTestUtils.setField(order, "price", new BigDecimal("150.00"));
        MarketPrice marketPrice = new MarketPrice("AAPL", new BigDecimal("151.00"), new BigDecimal("153.00"), new BigDecimal("151.00"), Instant.now());
        when(latestPriceStore.getPriceIfNotStale("AAPL")).thenReturn(Optional.of(marketPrice));
        when(accountRepository.findByAccountId("ACC-001")).thenReturn(Optional.of(account));
        
        OrderPricingValidator.PricingValidationResult result = orderPricingValidator.validateOrderPricing(order);
        
        assertEquals(OrderStatus.REJECTED, result.newStatus());
    }

    @Test
    @DisplayName("Should reject BUY order when account not found")
    void testValidateBuyOrderAccountNotFound() {
        MarketPrice marketPrice = new MarketPrice("AAPL", new BigDecimal("149.00"), new BigDecimal("151.00"), new BigDecimal("149.00"), Instant.now());
        when(latestPriceStore.getPriceIfNotStale("AAPL")).thenReturn(Optional.of(marketPrice));
        when(accountRepository.findByAccountId("ACC-001")).thenReturn(Optional.empty());
        
        OrderPricingValidator.PricingValidationResult result = orderPricingValidator.validateOrderPricing(order);
        
        assertEquals(OrderStatus.REJECTED, result.newStatus());
        assertTrue(result.reason().contains("Account not found"));
    }

    @Test
    @DisplayName("Should reject BUY order when insufficient funds")
    void testValidateBuyOrderInsufficientFunds() {
        ReflectionTestUtils.setField(account, "cashBalance", new BigDecimal("10000.00"));  // Not enough for 100 shares @ 150.50
        
        MarketPrice marketPrice = new MarketPrice("AAPL", new BigDecimal("149.00"), new BigDecimal("151.00"), new BigDecimal("149.00"), Instant.now());
        when(latestPriceStore.getPriceIfNotStale("AAPL")).thenReturn(Optional.of(marketPrice));
        when(accountRepository.findByAccountId("ACC-001")).thenReturn(Optional.of(account));
        
        OrderPricingValidator.PricingValidationResult result = orderPricingValidator.validateOrderPricing(order);
        
        assertEquals(OrderStatus.REJECTED, result.newStatus());
        assertTrue(result.reason().contains("Insufficient funds"));
    }

    @Test
    @DisplayName("Should approve SELL order when quote price is at or above order price")
    void testValidateSellOrderApproved() {
        ReflectionTestUtils.setField(order, "side", OrderSide.SELL);
        ReflectionTestUtils.setField(order, "price", new BigDecimal("150.00"));
        
        MarketPrice marketPrice = new MarketPrice("AAPL", new BigDecimal("149.00"), new BigDecimal("151.00"), new BigDecimal("149.00"), Instant.now());
        when(latestPriceStore.getPriceIfNotStale("AAPL")).thenReturn(Optional.of(marketPrice));
        
        OrderPricingValidator.PricingValidationResult result = orderPricingValidator.validateOrderPricing(order);
        
        assertEquals(OrderStatus.PENDING, result.newStatus());
    }

    @Test
    @DisplayName("Should reject SELL order when quote price is below order price")
    void testValidateSellOrderRejectedLowQuote() {
        ReflectionTestUtils.setField(order, "side", OrderSide.SELL);
        ReflectionTestUtils.setField(order, "price", new BigDecimal("151.00"));
        
        MarketPrice marketPrice = new MarketPrice("AAPL", new BigDecimal("149.00"), new BigDecimal("150.00"), new BigDecimal("149.00"), Instant.now());
        when(latestPriceStore.getPriceIfNotStale("AAPL")).thenReturn(Optional.of(marketPrice));
        
        OrderPricingValidator.PricingValidationResult result = orderPricingValidator.validateOrderPricing(order);
        
        assertEquals(OrderStatus.REJECTED, result.newStatus());
    }

    @Test
    @DisplayName("Should not check account funds for SELL orders")
    void testValidateSellOrderNoFundsCheck() {
        ReflectionTestUtils.setField(account, "cashBalance", new BigDecimal("0.00"));
        ReflectionTestUtils.setField(order, "side", OrderSide.SELL);
        ReflectionTestUtils.setField(order, "price", new BigDecimal("150.00"));
        
        MarketPrice marketPrice = new MarketPrice("AAPL", new BigDecimal("149.00"), new BigDecimal("151.00"), new BigDecimal("149.00"), Instant.now());
        when(latestPriceStore.getPriceIfNotStale("AAPL")).thenReturn(Optional.of(marketPrice));
        
        OrderPricingValidator.PricingValidationResult result = orderPricingValidator.validateOrderPricing(order);
        
        assertEquals(OrderStatus.PENDING, result.newStatus());
        verify(accountRepository, never()).findByAccountId(anyString());
    }

    @Test
    @DisplayName("Should calculate mid-price correctly for pricing decision")
    void testMidPriceCalculation() {
        // Bid: 150.00, Ask: 151.00, Mid: 150.50
        MarketPrice marketPrice = new MarketPrice("AAPL", new BigDecimal("150.00"), new BigDecimal("151.00"), new BigDecimal("150.00"), Instant.now());
        when(latestPriceStore.getPriceIfNotStale("AAPL")).thenReturn(Optional.of(marketPrice));
        when(accountRepository.findByAccountId("ACC-001")).thenReturn(Optional.of(account));
        
        ReflectionTestUtils.setField(order, "price", new BigDecimal("150.50"));
        OrderPricingValidator.PricingValidationResult result = orderPricingValidator.validateOrderPricing(order);
        
        assertEquals(OrderStatus.PENDING, result.newStatus());
    }

    @Test
    @DisplayName("Should handle exactly sufficient funds for BUY order")
    void testValidateBuyOrderExactFunds() {
        ReflectionTestUtils.setField(account, "cashBalance", new BigDecimal("15050.00"));  // Exactly 100 * 150.50
        
        MarketPrice marketPrice = new MarketPrice("AAPL", new BigDecimal("149.00"), new BigDecimal("151.00"), new BigDecimal("149.00"), Instant.now());
        when(latestPriceStore.getPriceIfNotStale("AAPL")).thenReturn(Optional.of(marketPrice));
        when(accountRepository.findByAccountId("ACC-001")).thenReturn(Optional.of(account));
        
        OrderPricingValidator.PricingValidationResult result = orderPricingValidator.validateOrderPricing(order);
        
        assertEquals(OrderStatus.PENDING, result.newStatus());
    }

    @Test
    @DisplayName("Should handle one cent insufficient for BUY order")
    void testValidateBuyOrderInsufficientByOneCent() {
        ReflectionTestUtils.setField(account, "cashBalance", new BigDecimal("15049.99"));  // One cent short
        
        MarketPrice marketPrice = new MarketPrice("AAPL", new BigDecimal("149.00"), new BigDecimal("151.00"), new BigDecimal("149.00"), Instant.now());
        when(latestPriceStore.getPriceIfNotStale("AAPL")).thenReturn(Optional.of(marketPrice));
        when(accountRepository.findByAccountId("ACC-001")).thenReturn(Optional.of(account));
        
        OrderPricingValidator.PricingValidationResult result = orderPricingValidator.validateOrderPricing(order);
        
        assertEquals(OrderStatus.REJECTED, result.newStatus());
        assertTrue(result.reason().contains("Insufficient funds"));
    }

    @Test
    @DisplayName("Should handle large quantities")
    void testValidateLargeQuantityOrder() {
        ReflectionTestUtils.setField(order, "quantity", 10000);  // 10,000 shares
        ReflectionTestUtils.setField(account, "cashBalance", new BigDecimal("1600000.00"));  // 10,000 * 150.50 + buffer
        
        MarketPrice marketPrice = new MarketPrice("AAPL", new BigDecimal("149.00"), new BigDecimal("151.00"), new BigDecimal("149.00"), Instant.now());
        when(latestPriceStore.getPriceIfNotStale("AAPL")).thenReturn(Optional.of(marketPrice));
        when(accountRepository.findByAccountId("ACC-001")).thenReturn(Optional.of(account));
        
        OrderPricingValidator.PricingValidationResult result = orderPricingValidator.validateOrderPricing(order);
        
        assertEquals(OrderStatus.PENDING, result.newStatus());
    }

    @Test
    @DisplayName("Should handle very small prices")
    void testValidateSmallPriceOrder() {
        ReflectionTestUtils.setField(order, "price", new BigDecimal("0.01"));
        
        MarketPrice marketPrice = new MarketPrice("AAPL", new BigDecimal("0.005"), new BigDecimal("0.015"), new BigDecimal("0.005"), Instant.now());
        when(latestPriceStore.getPriceIfNotStale("AAPL")).thenReturn(Optional.of(marketPrice));
        when(accountRepository.findByAccountId("ACC-001")).thenReturn(Optional.of(account));
        
        OrderPricingValidator.PricingValidationResult result = orderPricingValidator.validateOrderPricing(order);
        
        assertEquals(OrderStatus.PENDING, result.newStatus());
    }

    @Test
    @DisplayName("Should handle very large prices")
    void testValidateLargePriceOrder() {
        ReflectionTestUtils.setField(order, "price", new BigDecimal("99999.99"));
        ReflectionTestUtils.setField(order, "quantity", 1);
        ReflectionTestUtils.setField(account, "cashBalance", new BigDecimal("100000.00"));
        
        MarketPrice marketPrice = new MarketPrice("AAPL", new BigDecimal("99998.00"), new BigDecimal("100000.00"), new BigDecimal("99998.00"), Instant.now());
        when(latestPriceStore.getPriceIfNotStale("AAPL")).thenReturn(Optional.of(marketPrice));
        when(accountRepository.findByAccountId("ACC-001")).thenReturn(Optional.of(account));
        
        OrderPricingValidator.PricingValidationResult result = orderPricingValidator.validateOrderPricing(order);
        
        assertEquals(OrderStatus.PENDING, result.newStatus());
    }
}
