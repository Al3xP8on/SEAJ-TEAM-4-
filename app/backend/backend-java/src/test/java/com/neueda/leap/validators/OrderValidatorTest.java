package com.neueda.leap.validators;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.neueda.leap.models.*;
import com.neueda.leap.enums.*;
import com.neueda.leap.exceptions.OrderException;
import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.mockito.Mockito.*;

class OrderValidatorTest {
    @Test void validatesAccountAndInstrumentAndIncludesErrorContext() {
        Account account = mock(Account.class);
        when(account.getAccountId()).thenReturn("ACC-001");
        when(account.isActive()).thenReturn(false);
        OrderException inactive = assertThrows(OrderException.class, () -> OrderValidator.validateAccount(account));
        assertEquals(OrderErrorCode.ACCOUNT_NOT_ACTIVE, inactive.getErrorCode());
        assertEquals("ACC-001", inactive.getContext());
        when(account.isActive()).thenReturn(true);
        assertSame(account, OrderValidator.validateAccount(account));
        Instrument instrument = mock(Instrument.class);
        when(instrument.getSymbol()).thenReturn("AAPL");
        OrderException untradable = assertThrows(OrderException.class, () -> OrderValidator.validateInstrument(instrument));
        assertEquals(OrderErrorCode.INSTRUMENT_NOT_TRADABLE, untradable.getErrorCode());
        assertEquals("AAPL", untradable.getContext());
        when(instrument.isTradable()).thenReturn(true);
        assertSame(instrument, OrderValidator.validateInstrument(instrument));
        assertThrows(NullPointerException.class, () -> OrderValidator.validateAccount(null));
        assertThrows(NullPointerException.class, () -> OrderValidator.validateInstrument(null));
    }
    @Test void enforcesQuantityAndPriceBoundariesAndErrorCodes() {
        assertEquals(1L, OrderValidator.validateQuantity(1));
        assertEquals(Long.MAX_VALUE, OrderValidator.validateQuantity(Long.MAX_VALUE));
        for (long quantity : new long[]{0, -1, Long.MIN_VALUE})
            assertEquals(OrderErrorCode.INVALID_QUANTITY, assertThrows(OrderException.class, () -> OrderValidator.validateQuantity(quantity)).getErrorCode());
        assertEquals(new BigDecimal("0.000001"), OrderValidator.validatePrice(new BigDecimal("0.000001")));
        for (BigDecimal price : new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ONE.negate()})
            assertEquals(OrderErrorCode.INVALID_PRICE, assertThrows(OrderException.class, () -> OrderValidator.validatePrice(price)).getErrorCode());
        assertThrows(NullPointerException.class, () -> OrderValidator.validatePrice(null));
    }
    @Test void trimsIdempotencyKeysAndChecksLengthAfterTrimming() {
        assertEquals("key", OrderValidator.validateIdempotencyKey(" key "));
        assertEquals("a".repeat(100), OrderValidator.validateIdempotencyKey(" " + "a".repeat(100) + " "));
        assertThrows(NullPointerException.class, () -> OrderValidator.validateIdempotencyKey(null));
        assertEquals(OrderErrorCode.EMPTY_IDEMPOTENCY_KEY, assertThrows(OrderException.class, () -> OrderValidator.validateIdempotencyKey(" ")).getErrorCode());
        assertEquals(OrderErrorCode.IDEMPOTENCY_KEY_TOO_LONG, assertThrows(OrderException.class, () -> OrderValidator.validateIdempotencyKey("a".repeat(101))).getErrorCode());
    }
    @ParameterizedTest @EnumSource(value = OrderStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
    void rejectsExecutionOutsidePendingState(OrderStatus status) {
        Order order = mock(Order.class);
        when(order.getStatus()).thenReturn(status);
        assertFalse(OrderValidator.isValidForExecution(order));
        verify(order, never()).getInstrument();
    }
    @Test void rejectsUntradablePendingOrder() {
        Order order = mock(Order.class);
        Instrument instrument = mock(Instrument.class);
        when(order.getStatus()).thenReturn(OrderStatus.PENDING);
        when(order.getInstrument()).thenReturn(instrument);
        assertFalse(OrderValidator.isValidForExecution(order));
        verify(order, never()).getAccount();
    }
    @Test void checksExactTotalValueForBuyAndSkipsFundsCheckForSell() {
        Order order = mock(Order.class);
        Instrument instrument = mock(Instrument.class);
        Account account = mock(Account.class);
        when(order.getStatus()).thenReturn(OrderStatus.PENDING);
        when(order.getInstrument()).thenReturn(instrument);
        when(instrument.isTradable()).thenReturn(true);
        when(order.getAccount()).thenReturn(account);
        when(order.getSide()).thenReturn(OrderSide.BUY);
        when(order.getPrice()).thenReturn(new BigDecimal("10.25"));
        when(order.getQuantity()).thenReturn(3L);
        when(account.canAfford(new BigDecimal("30.75"))).thenReturn(false, true);
        assertFalse(OrderValidator.isValidForExecution(order));
        assertTrue(OrderValidator.isValidForExecution(order));
        verify(account, times(2)).canAfford(new BigDecimal("30.75"));
        clearInvocations(account);
        when(order.getSide()).thenReturn(OrderSide.SELL);
        assertTrue(OrderValidator.isValidForExecution(order));
        verifyNoInteractions(account);
    }
}
