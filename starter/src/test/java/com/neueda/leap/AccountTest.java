package com.neueda.leap;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class AccountValidatorTest {

    @Test
    void testValidateAccountIdValid() {
        assertDoesNotThrow(() -> AccountValidator.validateAccountId("ACC123"));
    }

    @Test
    void testValidateAccountIdNull() {
        assertThrows(NullPointerException.class, () -> 
            AccountValidator.validateAccountId(null));
    }

    @Test
    void testValidateAccountIdEmpty() {
        assertThrows(IllegalArgumentException.class, () -> 
            AccountValidator.validateAccountId(""));
    }

    @Test
    void testValidateHolderNameValid() {
        assertDoesNotThrow(() -> AccountValidator.validateHolderName("John Doe"));
    }

    @Test
    void testValidateHolderNameNull() {
        assertThrows(NullPointerException.class, () -> 
            AccountValidator.validateHolderName(null));
    }

    @Test
    void testValidatePositiveAmountValid() {
        assertDoesNotThrow(() -> 
            AccountValidator.validatePositiveAmount(new BigDecimal("100.00"), "Balance"));
    }

    @Test
    void testValidatePositiveAmountZero() {
        assertThrows(IllegalArgumentException.class, () -> 
            AccountValidator.validatePositiveAmount(BigDecimal.ZERO, "Balance"));
    }

    @Test
    void testValidatePositiveAmountNegative() {
        assertThrows(IllegalArgumentException.class, () -> 
            AccountValidator.validatePositiveAmount(new BigDecimal("-50.00"), "Balance"));
    }

    @Test
    void testValidateCanCloseValidZeroBalance() {
        assertDoesNotThrow(() -> AccountValidator.validateCanClose(BigDecimal.ZERO));
    }

    @Test
    void testValidateCanCloseInvalidNonZeroBalance() {
        assertThrows(IllegalArgumentException.class, () -> 
            AccountValidator.validateCanClose(new BigDecimal("100.00")));
    }
}