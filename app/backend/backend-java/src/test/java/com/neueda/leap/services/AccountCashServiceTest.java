package com.neueda.leap.services;

import com.neueda.leap.dtos.AccountResponse;
import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.exceptions.AccountNotActiveException;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.exceptions.InsufficientFundsException;
import com.neueda.leap.models.Account;
import com.neueda.leap.repositories.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("AccountService deposit and withdraw")
class AccountCashServiceTest {

    @InjectMocks
    private AccountService accountService;

    @Mock
    private AccountRepository accountRepository;

    private Account account;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        account = accountWithBalance("100.00", AccountStatus.ACTIVE);
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static Account accountWithBalance(String balance, AccountStatus status) {
        Account account = new Account("ACC-001", "alice", "password", "Alice",
                "alice@example.com", "1234567890", new BigDecimal(balance), status);
        ReflectionTestUtils.setField(account, "id", 1L);
        return account;
    }

    @Test
    @DisplayName("Deposit adds to the balance and saves the account")
    void depositAddsToBalance() throws Exception {
        AccountResponse response = accountService.deposit(1L, new BigDecimal("50.25"));

        assertEquals(new BigDecimal("150.25"), response.cashBalance());
        verify(accountRepository).save(account);
    }

    @Test
    @DisplayName("Withdraw subtracts from the balance and saves the account")
    void withdrawSubtractsFromBalance() throws Exception {
        AccountResponse response = accountService.withdraw(1L, new BigDecimal("40.00"));

        assertEquals(new BigDecimal("60.00"), response.cashBalance());
        verify(accountRepository).save(account);
    }

    @Test
    @DisplayName("Withdrawing the full balance leaves zero")
    void withdrawFullBalance() throws Exception {
        AccountResponse response = accountService.withdraw(1L, new BigDecimal("100.00"));

        assertEquals(0, response.cashBalance().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Withdrawing more than the balance is rejected and nothing is saved")
    void withdrawMoreThanBalance() {
        assertThrows(InsufficientFundsException.class,
                () -> accountService.withdraw(1L, new BigDecimal("100.01")));

        assertEquals(new BigDecimal("100.00"), account.getCashBalance());
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Suspended and closed accounts cannot deposit or withdraw")
    void inactiveAccountRejected() {
        for (AccountStatus status : new AccountStatus[] {AccountStatus.SUSPENDED, AccountStatus.CLOSED}) {
            Account inactive = accountWithBalance("100.00", status);
            when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(inactive));

            assertThrows(AccountNotActiveException.class, () -> accountService.deposit(1L, BigDecimal.TEN));
            assertThrows(AccountNotActiveException.class, () -> accountService.withdraw(1L, BigDecimal.TEN));
        }
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Unknown account is reported as not found")
    void unknownAccount() {
        when(accountRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> accountService.deposit(99L, BigDecimal.TEN));
        assertThrows(AccountNotFoundException.class, () -> accountService.withdraw(99L, BigDecimal.TEN));
    }

    @Test
    @DisplayName("Balance changes read the account with a row lock")
    void usesLockedLookup() throws Exception {
        accountService.deposit(1L, BigDecimal.ONE);
        accountService.withdraw(1L, BigDecimal.ONE);

        verify(accountRepository, times(2)).findByIdForUpdate(1L);
        verify(accountRepository, never()).findById(any());
    }
}
