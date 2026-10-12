package com.neueda.leap.services;

import com.neueda.leap.models.Account;
import com.neueda.leap.dtos.AccountInput;
import com.neueda.leap.dtos.AccountResponse;
import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.exceptions.DuplicateAccountException;
import com.neueda.leap.validators.AccountValidator;
import com.neueda.leap.repositories.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("AccountService Tests")
public class AccountServiceTest {

    @InjectMocks
    private AccountService accountService;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountValidator validator;

    private Account testAccount;
    private AccountInput accountInput;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        testAccount = new Account("ACC-001", "testuser", "password", "Test Account", 
                "test@example.com", "1234567890", new BigDecimal("50000.00"), AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(testAccount, "id", 1L);
        
        accountInput = new AccountInput("testuser", "password", "test@example.com", "1234567890");
    }

    @Test
    @DisplayName("Should create account with a generated account ID")
    void testCreateAccountSuccess() throws DuplicateAccountException {
        when(accountRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        when(accountRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(accountRepository.findByPhone("1234567890")).thenReturn(Optional.empty());
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        AccountResponse response = accountService.createAccount(accountInput);

        assertNotNull(response);
        assertEquals(testAccount.getAccountId(), response.accountId());
        assertEquals(testAccount.getName(), response.holderName());
        assertEquals(AccountStatus.ACTIVE, response.status());

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(saved.capture());
        assertTrue(saved.getValue().getAccountId().matches("ACC-[0-9A-F]{8}"));
        assertEquals("testuser", saved.getValue().getUsername());
        assertEquals("test@example.com", saved.getValue().getEmail());
        assertEquals("1234567890", saved.getValue().getPhone());
    }

    @Test
    @DisplayName("Should create new accounts with a zero balance")
    void testCreateAccountStartsWithZeroBalance() throws DuplicateAccountException {
        when(accountRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(accountRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(accountRepository.findByPhone(anyString())).thenReturn(Optional.empty());
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            ReflectionTestUtils.setField(account, "id", 2L);
            return account;
        });

        AccountResponse response = accountService.createAccount(accountInput);

        assertEquals(BigDecimal.ZERO, response.cashBalance());
    }

    @Test
    @DisplayName("Should throw DuplicateAccountException when username already taken")
    void testCreateAccountDuplicateUsername() {
        when(accountRepository.findByUsername("testuser")).thenReturn(Optional.of(testAccount));

        assertThrows(DuplicateAccountException.class, () -> 
            accountService.createAccount(accountInput)
        );

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    @DisplayName("Should throw DuplicateAccountException when email already in use")
    void testCreateAccountDuplicateEmail() {
        when(accountRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        when(accountRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testAccount));

        assertThrows(DuplicateAccountException.class, () -> 
            accountService.createAccount(accountInput)
        );

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    @DisplayName("Should throw DuplicateAccountException when phone number already in use")
    void testCreateAccountDuplicatePhone() {
        when(accountRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        when(accountRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(accountRepository.findByPhone("1234567890")).thenReturn(Optional.of(testAccount));

        assertThrows(DuplicateAccountException.class, () -> 
            accountService.createAccount(accountInput)
        );

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    @DisplayName("Should skip email and phone checks when they are not provided")
    void testCreateAccountWithoutEmailOrPhone() throws DuplicateAccountException {
        AccountInput minimalInput = new AccountInput("minuser", "pass", null, null);
        when(accountRepository.findByUsername("minuser")).thenReturn(Optional.empty());
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        accountService.createAccount(minimalInput);

        verify(accountRepository, never()).findByEmail(any());
        verify(accountRepository, never()).findByPhone(any());
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    @DisplayName("Should retrieve all accounts")
    void testGetAllAccounts() {
        Account account2 = new Account("ACC-002", "user2", "pass2", "Account 2", 
                "user2@example.com", "9876543210", new BigDecimal("30000.00"), AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(account2, "id", 2L);
        
        List<Account> accounts = Arrays.asList(testAccount, account2);
        when(accountRepository.findAll()).thenReturn(accounts);

        List<Account> result = accountService.getAllAccounts();

        assertNotNull(result);
        assertEquals(2, result.size());
        verify(accountRepository).findAll();
    }

    @Test
    @DisplayName("Should retrieve empty list when no accounts exist")
    void testGetAllAccountsEmpty() {
        when(accountRepository.findAll()).thenReturn(Arrays.asList());

        List<Account> result = accountService.getAllAccounts();

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(accountRepository).findAll();
    }

    @Test
    @DisplayName("Should retrieve account by ID")
    void testGetAccountSuccess() throws AccountNotFoundException {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

        Account result = accountService.getAccount(1L);

        assertNotNull(result);
        assertEquals(testAccount.getAccountId(), result.getAccountId());
        assertEquals(testAccount.getName(), result.getName());
        verify(accountRepository).findById(1L);
    }

    @Test
    @DisplayName("Should throw AccountNotFoundException when account not found")
    void testGetAccountNotFound() {
        when(accountRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> 
            accountService.getAccount(999L)
        );

        verify(accountRepository).findById(999L);
    }

    @Test
    @DisplayName("Should retrieve account balance")
    void testGetBalance() throws AccountNotFoundException {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

        BigDecimal balance = accountService.getBalance(1L);

        assertNotNull(balance);
        assertEquals(new BigDecimal("50000.00"), balance);
        verify(accountRepository).findById(1L);
    }

    @Test
    @DisplayName("Should throw AccountNotFoundException when getting balance for non-existent account")
    void testGetBalanceAccountNotFound() {
        when(accountRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> 
            accountService.getBalance(999L)
        );
    }

    @Test
    @DisplayName("Should throw UnsupportedOperationException when getting positions")
    void testGetPositions() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

        assertThrows(UnsupportedOperationException.class, () -> 
            accountService.getPositions(1L)
        );

        verify(accountRepository).findById(1L);
    }

    @Test
    @DisplayName("Should retrieve empty OrderHistory for account")
    void testGetOrders() throws AccountNotFoundException {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

        var orderHistory = accountService.getOrders(1L);

        assertNotNull(orderHistory);
        verify(accountRepository).findById(1L);
    }

    @Test
    @DisplayName("Should add account to repository")
    void testAddAccount() {
        when(accountRepository.save(testAccount)).thenReturn(testAccount);

        accountService.addAccount(testAccount);

        verify(accountRepository).save(testAccount);
    }

    @Test
    @DisplayName("Should check if account exists by ID")
    void testAccountExistsTrue() {
        when(accountRepository.existsById(1L)).thenReturn(true);

        boolean exists = accountService.accountExists(1L);

        assertTrue(exists);
        verify(accountRepository).existsById(1L);
    }

    @Test
    @DisplayName("Should return false when account does not exist")
    void testAccountExistsFalse() {
        when(accountRepository.existsById(999L)).thenReturn(false);

        boolean exists = accountService.accountExists(999L);

        assertFalse(exists);
        verify(accountRepository).existsById(999L);
    }

    @Test
    @DisplayName("Should handle multiple account retrievals")
    void testMultipleAccountRetrievals() throws AccountNotFoundException {
        Account account2 = new Account("ACC-002", "user2", "pass2", "Account 2", 
                "user2@example.com", "9876543210", new BigDecimal("30000.00"), AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(account2, "id", 2L);

        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account2));

        Account result1 = accountService.getAccount(1L);
        Account result2 = accountService.getAccount(2L);

        assertNotNull(result1);
        assertNotNull(result2);
        assertEquals("ACC-001", result1.getAccountId());
        assertEquals("ACC-002", result2.getAccountId());
        verify(accountRepository, times(2)).findById(any());
    }
}
