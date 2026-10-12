package com.neueda.leap.models;

import com.neueda.leap.enums.AccountStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class AccountTest {

    @Test
    void registrationStartsWithZeroBalanceAndHashesThePassword() {
        LocalDateTime before = LocalDateTime.now();

        Account account = new Account("ACC-001", "alice", "secret-password",
                "alice@example.com", "123456789", AccountStatus.ACTIVE);

        assertNull(account.getId());
        assertEquals("ACC-001", account.getAccountId());
        assertEquals("alice", account.getUsername());
        assertEquals("alice", account.getName());
        assertEquals("alice", account.getHolderName());
        assertEquals("alice@example.com", account.getEmail());
        assertEquals("123456789", account.getPhone());
        assertEquals(BigDecimal.ZERO, account.getCashBalance());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertEquals(0, account.getVersion());
        assertFalse(account.getCreatedOn().isBefore(before));
        assertFalse(account.getLastUpdated().isBefore(account.getCreatedOn()));
        String passwordHash = (String) ReflectionTestUtils.getField(account, "passwordHash");
        assertNotEquals("secret-password", passwordHash);
        assertTrue(new BCryptPasswordEncoder().matches("secret-password", passwordHash));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"-0.01", "-100.00"})
    void rejectsInvalidInitialBalance(String initialBalance) {
        BigDecimal balance = initialBalance == null ? null : new BigDecimal(initialBalance);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> accountWithBalance(balance));

        assertEquals("Initial balance must be zero or positive", exception.getMessage());
    }

    @Test
    void acceptsZeroInitialBalanceAndMaintainsLegacyConstructorIdentity() {
        Account account = new Account("ACC-001", "Alice Smith", "alice@example.com",
                "123456789", BigDecimal.ZERO, AccountStatus.ACTIVE);

        assertEquals("acc001", account.getUsername());
        assertEquals("Alice Smith", account.getName());
        assertEquals(BigDecimal.ZERO, account.getCashBalance());
        assertTrue(account.isValidForTrading());
    }

    @Test
    void depositAndWithdrawalUpdateBalanceVersionAndTimestamp() {
        Account account = accountWithBalance(new BigDecimal("100.00"));
        LocalDateTime previousUpdate = account.getLastUpdated();

        account.deposit(new BigDecimal("25.50"));

        assertEquals(new BigDecimal("125.50"), account.getCashBalance());
        assertEquals(1, account.getVersion());
        assertFalse(account.getLastUpdated().isBefore(previousUpdate));

        account.withdraw(new BigDecimal("125.50"));

        assertEquals(new BigDecimal("0.00"), account.getCashBalance());
        assertEquals(2, account.getVersion());
        assertTrue(account.isValidForTrading());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1"})
    void rejectsInvalidDepositsAndWithdrawalsWithoutChangingAccount(String value) {
        Account account = accountWithBalance(new BigDecimal("100.00"));
        BigDecimal amount = value == null ? null : new BigDecimal(value);
        Class<? extends RuntimeException> expected = value == null
                ? NullPointerException.class : IllegalArgumentException.class;

        assertThrows(expected, () -> account.deposit(amount));
        assertThrows(expected, () -> account.withdraw(amount));

        assertEquals(new BigDecimal("100.00"), account.getCashBalance());
        assertEquals(0, account.getVersion());
    }

    @Test
    void rejectsOverdrawWithoutChangingBalanceOrVersion() {
        Account account = accountWithBalance(new BigDecimal("100.00"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> account.withdraw(new BigDecimal("100.01")));

        assertEquals("Insufficient funds", exception.getMessage());
        assertEquals(new BigDecimal("100.00"), account.getCashBalance());
        assertEquals(0, account.getVersion());
    }

    @Test
    void affordabilityRequiresPositiveAmountWithinAvailableBalance() {
        Account account = accountWithBalance(new BigDecimal("100.00"));

        assertFalse(account.canAfford(null));
        assertFalse(account.canAfford(BigDecimal.ZERO));
        assertFalse(account.canAfford(new BigDecimal("-1.00")));
        assertTrue(account.canAfford(new BigDecimal("99.99")));
        assertTrue(account.canAfford(new BigDecimal("100.00")));
        assertFalse(account.canAfford(new BigDecimal("100.01")));
        assertEquals(0, account.getVersion());
    }

    @Test
    void suspensionDisablesTradingAndRecordsTheStatusChange() {
        Account account = accountWithBalance(new BigDecimal("100.00"));
        assertTrue(account.isActive());
        assertTrue(account.isValidForTrading());

        account.suspendAccount();

        assertEquals(AccountStatus.SUSPENDED, account.getStatus());
        assertFalse(account.isActive());
        assertFalse(account.isValidForTrading());
        assertEquals(1, account.getVersion());

        account.setStatus(AccountStatus.ACTIVE);

        assertTrue(account.isValidForTrading());
        assertEquals(2, account.getVersion());
    }

    @Test
    void rejectsNullStatusWithoutChangingExistingStatus() {
        Account account = accountWithBalance(BigDecimal.ZERO);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> account.setStatus(null));

        assertEquals("Status cannot be null", exception.getMessage());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertEquals(0, account.getVersion());
    }

    @Test
    void allowsClosureOnlyAfterBalanceHasBeenWithdrawn() {
        Account account = accountWithBalance(new BigDecimal("100.00"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, account::closeAccount);

        assertEquals("Account cannot be closed with non-zero balance", exception.getMessage());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertEquals(0, account.getVersion());

        account.withdraw(new BigDecimal("100.00"));
        account.closeAccount();

        assertEquals(AccountStatus.CLOSED, account.getStatus());
        assertFalse(account.isActive());
        assertFalse(account.isValidForTrading());
        assertEquals(2, account.getVersion());
    }

    @Test
    void profileUpdatesPreserveTheBalanceAndRejectAnInvalidAccountId() {
        Account account = accountWithBalance(new BigDecimal("100.00"));

        account.setAccountId("ACC-002");
        account.setName("Alice Smith");
        account.setEmail("alice@example.com");
        account.setPhone("987654321");

        assertEquals("ACC-002", account.getAccountId());
        assertEquals("Alice Smith", account.getName());
        assertEquals("Alice Smith", account.getHolderName());
        assertEquals("alice@example.com", account.getEmail());
        assertEquals("987654321", account.getPhone());
        assertEquals(new BigDecimal("100.00"), account.getCashBalance());
        assertThrows(IllegalArgumentException.class, () -> account.setAccountId(" "));
        assertEquals("ACC-002", account.getAccountId());
    }

    private Account accountWithBalance(BigDecimal balance) {
        return new Account("ACC-001", "alice", "secret-password", "Alice",
                "alice@example.com", null, balance, AccountStatus.ACTIVE);
    }
}
