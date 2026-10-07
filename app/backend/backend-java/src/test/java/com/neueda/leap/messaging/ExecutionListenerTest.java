package com.neueda.leap.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.models.Account;
import com.neueda.leap.models.Instrument;
import com.neueda.leap.models.Order;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test suite for ExecutionListener - proves idempotency guarantee.
 * 
 * STORY: Duplicate Delivery: Prove No Double Debit
 * 
 * Acceptance Criteria:
 * - A replayed message does not debit the account twice
 * - No second message appears on trade-events
 * - The demonstration can be given on demand, at any point in the review
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExecutionListener - Idempotency Tests")
public class ExecutionListenerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private TradeEventPublisher tradeEventPublisher;

    @InjectMocks
    private ExecutionListener executionListener;

    private UUID executionId;
    private UUID orderId;
    private String accountId;
    private Account account;
    private Order order;
    private ExecutionEvent executionEvent;
    private String executionMessage;

    @BeforeEach
    void setUp() throws Exception {
        executionId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        accountId = "ACC-001";

        // Setup test account with initial balance
        account = new Account(accountId, "Alice Johnson", "alice@example.com", "555-1234", 
                            new BigDecimal("50000.00"), AccountStatus.ACTIVE);

        // Setup test instrument
        Instrument instrument = new Instrument("AAPL", "Apple Inc.", "EQUITY", "USD", true);

        // Setup test order (in PENDING status - ready for execution)
        order = new Order(orderId, account, instrument, 100, new BigDecimal("150.50"),
                        OrderSide.BUY, "idem-key-001", OrderStatus.PENDING, LocalDateTime.now());

        // Setup execution event (100 shares at $150.50 = $15,050.00 debit)
        executionEvent = new ExecutionEvent(
            executionId,
            orderId,
            accountId,
            "AAPL",
            OrderSide.BUY,
            100,
            new BigDecimal("150.50"),
            new BigDecimal("150.50"),
            "SIM",
            Instant.now()
        );

        // JSON representation of execution event
        executionMessage = "{\"executionId\":\"" + executionId + "\",\"orderId\":\"" + orderId + 
                         "\",\"accountId\":\"" + accountId + 
                         "\",\"symbol\":\"AAPL\",\"side\":\"BUY\",\"price\":150.50,\"quantity\":100," +
                         "\"venue\":\"SIM\",\"executedOn\":\"2026-10-06T15:00:00Z\"}";
    }

    // ==================== CORE IDEMPOTENCY TESTS ====================

    @Test
    @DisplayName("Should settle execution and debit account on first message")
    void testSettleExecutionFirstTime() throws Exception {
        // Arrange
        when(objectMapper.readValue(executionMessage, ExecutionEvent.class)).thenReturn(executionEvent);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(accountRepository.findByAccountId(accountId)).thenReturn(Optional.of(account));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(accountRepository.save(any(Account.class))).thenReturn(account);

        BigDecimal initialBalance = account.getCashBalance();
        BigDecimal expectedDebit = new BigDecimal("150.50").multiply(BigDecimal.valueOf(100));
        BigDecimal expectedBalance = initialBalance.subtract(expectedDebit);

        // Act
        executionListener.onExecution(executionMessage);

        // Assert
        assertEquals(expectedBalance, account.getCashBalance(), 
                   "Account should be debited by execution amount on first message");
        assertEquals(OrderStatus.FILLED, order.getStatus(), 
                   "Order should transition to FILLED");
        verify(accountRepository).save(account);
        verify(orderRepository).save(order);
        verify(tradeEventPublisher, times(2)).publishTradeEvent(any()); // TRADE_EXECUTED + ORDER_FILLED
    }

    @Test
    @DisplayName("IDEMPOTENCY TEST: Replayed execution message does NOT debit account twice")
    void testReplayedExecutionDoesNotDebitTwice() throws Exception {
        // Arrange
        // First execution: order is PENDING
        when(objectMapper.readValue(executionMessage, ExecutionEvent.class)).thenReturn(executionEvent);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(accountRepository.findByAccountId(accountId)).thenReturn(Optional.of(account));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order savedOrder = invocation.getArgument(0);
            savedOrder.setStatus(OrderStatus.FILLED);
            return savedOrder;
        });
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account savedAccount = invocation.getArgument(0);
            return savedAccount;
        });

        BigDecimal initialBalance = account.getCashBalance();
        BigDecimal executionAmount = new BigDecimal("150.50").multiply(BigDecimal.valueOf(100));

        // Act - First execution message
        executionListener.onExecution(executionMessage);

        BigDecimal balanceAfterFirstExecution = account.getCashBalance();
        assertEquals(initialBalance.subtract(executionAmount), balanceAfterFirstExecution,
                   "Account should be debited on first execution");

        // Act - Replayed execution message (order now FILLED)
        order.setStatus(OrderStatus.FILLED); // Simulate state after first execution

        executionListener.onExecution(executionMessage);

        BigDecimal balanceAfterReplay = account.getCashBalance();

        // Assert - CRITICAL: Balance should NOT change on replayed message
        assertEquals(balanceAfterFirstExecution, balanceAfterReplay,
                   "IDEMPOTENCY FAILURE: Account was debited twice on replayed message!");
        
        // Verify save was called only once (first execution), not twice
        verify(accountRepository).save(account);
    }

    @Test
    @DisplayName("IDEMPOTENCY TEST: Duplicate events are NOT published on replayed execution")
    void testReplayedExecutionDoesNotPublishDuplicateEvents() throws Exception {
        // Arrange
        when(objectMapper.readValue(executionMessage, ExecutionEvent.class)).thenReturn(executionEvent);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(accountRepository.findByAccountId(accountId)).thenReturn(Optional.of(account));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order savedOrder = invocation.getArgument(0);
            savedOrder.setStatus(OrderStatus.FILLED);
            return savedOrder;
        });
        when(accountRepository.save(any(Account.class))).thenReturn(account);

        // Act - First execution message
        executionListener.onExecution(executionMessage);

        // Verify 2 events were published (TRADE_EXECUTED + ORDER_FILLED)
        verify(tradeEventPublisher, times(2)).publishTradeEvent(any());

        // Reset mock to track only new calls
        reset(tradeEventPublisher);

        // Act - Replayed execution message (order now FILLED, should be skipped)
        order.setStatus(OrderStatus.FILLED); // Simulate state after first execution

        executionListener.onExecution(executionMessage);

        // Assert - CRITICAL: No new events should be published on replay
        verify(tradeEventPublisher, never()).publishTradeEvent(any());
    }

    @Test
    @DisplayName("Should skip settlement when order is already FILLED (at-least-once safety)")
    void testSkipsSettlementWhenOrderAlreadyFilled() throws Exception {
        // Arrange
        order.setStatus(OrderStatus.FILLED); // Order already filled from previous execution

        when(objectMapper.readValue(executionMessage, ExecutionEvent.class)).thenReturn(executionEvent);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(accountRepository.findByAccountId(accountId)).thenReturn(Optional.of(account));

        BigDecimal initialBalance = account.getCashBalance();

        // Act
        executionListener.onExecution(executionMessage);

        // Assert
        assertEquals(initialBalance, account.getCashBalance(),
                   "Account balance should not change for already-filled order");
        verify(accountRepository, never()).save(any(Account.class));
        verify(orderRepository, never()).save(any(Order.class));
        verify(tradeEventPublisher, never()).publishTradeEvent(any());
    }

    @Test
    @DisplayName("Should skip settlement when order is REJECTED (at-least-once safety)")
    void testSkipsSettlementWhenOrderRejected() throws Exception {
        // Arrange
        order.setStatus(OrderStatus.REJECTED); // Order was rejected during pricing validation

        when(objectMapper.readValue(executionMessage, ExecutionEvent.class)).thenReturn(executionEvent);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(accountRepository.findByAccountId(accountId)).thenReturn(Optional.of(account));

        BigDecimal initialBalance = account.getCashBalance();

        // Act
        executionListener.onExecution(executionMessage);

        // Assert
        assertEquals(initialBalance, account.getCashBalance(),
                   "Account balance should not change for rejected order");
        verify(accountRepository, never()).save(any(Account.class));
        verify(orderRepository, never()).save(any(Order.class));
        verify(tradeEventPublisher, never()).publishTradeEvent(any());
    }

    @Test
    @DisplayName("Should skip settlement when order is CANCELLED (at-least-once safety)")
    void testSkipsSettlementWhenOrderCancelled() throws Exception {
        // Arrange
        order.setStatus(OrderStatus.CANCELLED); // Order was cancelled before execution

        when(objectMapper.readValue(executionMessage, ExecutionEvent.class)).thenReturn(executionEvent);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(accountRepository.findByAccountId(accountId)).thenReturn(Optional.of(account));

        BigDecimal initialBalance = account.getCashBalance();

        // Act
        executionListener.onExecution(executionMessage);

        // Assert
        assertEquals(initialBalance, account.getCashBalance(),
                   "Account balance should not change for cancelled order");
        verify(accountRepository, never()).save(any(Account.class));
        verify(orderRepository, never()).save(any(Order.class));
        verify(tradeEventPublisher, never()).publishTradeEvent(any());
    }

    @Test
    @DisplayName("Should throw PoisonMessageException when order not found")
    void testThrowsPoisonMessageWhenOrderNotFound() throws Exception {
        // Arrange
        when(objectMapper.readValue(executionMessage, ExecutionEvent.class)).thenReturn(executionEvent);
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(PoisonMessageException.class, () -> executionListener.onExecution(executionMessage),
                   "Should throw PoisonMessageException when order not found");
    }

    @Test
    @DisplayName("Should throw PoisonMessageException when account not found")
    void testThrowsPoisonMessageWhenAccountNotFound() throws Exception {
        // Arrange
        when(objectMapper.readValue(executionMessage, ExecutionEvent.class)).thenReturn(executionEvent);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(accountRepository.findByAccountId(accountId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(PoisonMessageException.class, () -> executionListener.onExecution(executionMessage),
                   "Should throw PoisonMessageException when account not found");
    }

    // ==================== SELL ORDER TESTS ====================

    @Test
    @DisplayName("Should credit account for SELL execution")
    void testSettleSellExecutionCreditsAccount() throws Exception {
        // Arrange
        order.setStatus(OrderStatus.PENDING);
        ExecutionEvent sellEvent = new ExecutionEvent(
            executionId,
            orderId,
            accountId,
            "AAPL",
            OrderSide.SELL,
            100,
            new BigDecimal("150.50"),
            new BigDecimal("150.50"),
            "SIM",
            Instant.now()
        );

        String sellMessage = "{\"executionId\":\"" + executionId + "\",\"orderId\":\"" + orderId + 
                           "\",\"accountId\":\"" + accountId + 
                           "\",\"symbol\":\"AAPL\",\"side\":\"SELL\",\"price\":150.50,\"quantity\":100," +
                           "\"venue\":\"SIM\",\"executedOn\":\"2026-10-06T15:00:00Z\"}";

        when(objectMapper.readValue(sellMessage, ExecutionEvent.class)).thenReturn(sellEvent);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(accountRepository.findByAccountId(accountId)).thenReturn(Optional.of(account));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(accountRepository.save(any(Account.class))).thenReturn(account);

        BigDecimal initialBalance = account.getCashBalance();
        BigDecimal executionAmount = new BigDecimal("150.50").multiply(BigDecimal.valueOf(100));
        BigDecimal expectedBalance = initialBalance.add(executionAmount);

        // Act
        executionListener.onExecution(sellMessage);

        // Assert
        assertEquals(expectedBalance, account.getCashBalance(),
                   "Account should be credited by execution amount for SELL order");
        assertEquals(OrderStatus.FILLED, order.getStatus());
    }

    // ==================== INTEGRATION SCENARIO TESTS ====================

    /**
     * DEMONSTRATION TEST: Complete scenario showing idempotency guarantee
     * 
     * This test can be run on demand to prove:
     * 1. First execution debits account correctly
     * 2. Replayed execution does not debit again (the critical requirement)
     * 3. No duplicate events appear on trade-events topic
     */
    @Test
    @DisplayName("DEMO: Duplicate Delivery - Prove No Double Debit (Complete Scenario)")
    void demonstrateDuplicateDeliveryHandling() throws Exception {
        // SETUP: Create order and account state
        when(objectMapper.readValue(executionMessage, ExecutionEvent.class)).thenReturn(executionEvent);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(accountRepository.findByAccountId(accountId)).thenReturn(Optional.of(account));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order savedOrder = invocation.getArgument(0);
            savedOrder.setStatus(OrderStatus.FILLED);
            return savedOrder;
        });
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account savedAccount = invocation.getArgument(0);
            return savedAccount;
        });

        BigDecimal initialBalance = new BigDecimal("50000.00");
        BigDecimal executionAmount = new BigDecimal("15050.00"); // 150.50 * 100

        System.out.println("\n=== DEMONSTRATION: Duplicate Delivery Handling ===\n");
        System.out.println("Initial Account Balance: $" + initialBalance);
        System.out.println("Order: 100 AAPL @ $150.50 = $" + executionAmount + " debit");
        System.out.println("Execution Message ID: " + orderId);
        System.out.println("\n--- Scenario 1: First Delivery ---");

        // STEP 1: Process execution message first time
        executionListener.onExecution(executionMessage);
        BigDecimal balanceAfterFirstDelivery = account.getCashBalance();
        BigDecimal expectedAfterFirstDelivery = initialBalance.subtract(executionAmount);

        System.out.println("✓ Execution message processed");
        System.out.println("✓ Account debited: " + initialBalance + " - " + executionAmount + 
                         " = " + balanceAfterFirstDelivery);
        System.out.println("✓ Order status: " + order.getStatus());
        System.out.println("✓ Events published: 2 (TRADE_EXECUTED + ORDER_FILLED)");

        assertEquals(expectedAfterFirstDelivery, balanceAfterFirstDelivery);
        verify(tradeEventPublisher, times(2)).publishTradeEvent(any());

        System.out.println("\n--- Scenario 2: Message Replay (Idempotency Test) ---");

        // STEP 2: Simulate message replay (same execution message delivered again)
        order.setStatus(OrderStatus.FILLED); // System already processed first delivery
        reset(tradeEventPublisher); // Reset to track only new event publications

        executionListener.onExecution(executionMessage);
        BigDecimal balanceAfterReplay = account.getCashBalance();

        System.out.println("✓ SAME execution message replayed");
        System.out.println("✓ Account balance UNCHANGED: " + balanceAfterFirstDelivery + 
                         " (NOT debited again)");
        System.out.println("✓ Order status: " + order.getStatus() + " (skipped re-settlement)");
        System.out.println("✓ Events published: 0 (duplicate prevention working)");

        // CRITICAL ASSERTIONS
        assertEquals(balanceAfterFirstDelivery, balanceAfterReplay,
                   "CRITICAL: Account must NOT be debited twice!");
        verify(tradeEventPublisher, never()).publishTradeEvent(any());

        System.out.println("\n✅ IDEMPOTENCY TEST PASSED:");
        System.out.println("   - No double debit on message replay");
        System.out.println("   - No duplicate events on trade-events topic");
        System.out.println("   - Demonstration complete and repeatable on demand\n");
    }
}
