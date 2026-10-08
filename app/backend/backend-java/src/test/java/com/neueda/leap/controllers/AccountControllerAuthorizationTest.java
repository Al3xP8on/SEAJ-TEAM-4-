package com.neueda.leap.controllers;

import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.models.Account;
import com.neueda.leap.models.OrderHistory;
import com.neueda.leap.models.Positions;
import com.neueda.leap.security.AuthorizationException;
import com.neueda.leap.security.SecurityUtilsService;
import com.neueda.leap.services.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountController Authorization Unit Tests")
public class AccountControllerAuthorizationTest {

    @Mock
    private AccountService accountService;

    @Mock
    private SecurityUtilsService securityUtilsService;

    @InjectMocks
    private AccountController accountController;

    private Account aliceAccount;
    private Account brianAccount;

    @BeforeEach
    void setUp() {
        aliceAccount = new Account("ACC-0001", "alice", "SecurePass123!", "Alice Johnson",
                "alice@example.com", null, new BigDecimal("10000.00"), AccountStatus.ACTIVE);

        brianAccount = new Account("ACC-0002", "brian", "SecurePass123!", "Brian Smith",
                "brian@example.com", null, new BigDecimal("5000.00"), AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should return only user's account when non-admin lists accounts")
    void testGetAllAccountsReturnsOnlyUserAccount() throws Exception {
        when(securityUtilsService.isAdmin()).thenReturn(false);
        when(securityUtilsService.getAuthenticatedUserAccount()).thenReturn(aliceAccount);

        accountController.getAllAccounts();

        verify(securityUtilsService).isAdmin();
        verify(securityUtilsService).getAuthenticatedUserAccount();
    }

    @Test
    @DisplayName("Should return all accounts when admin lists accounts")
    void testGetAllAccountsReturnsAllForAdmin() throws Exception {
        when(securityUtilsService.isAdmin()).thenReturn(true);
        when(accountService.getAllAccounts()).thenReturn(java.util.Arrays.asList(aliceAccount, brianAccount));

        accountController.getAllAccounts();

        verify(securityUtilsService).isAdmin();
        verify(accountService).getAllAccounts();
    }

    @Test
    @DisplayName("Should validate account ownership when getting account")
    void testGetAccountValidatesOwnership() throws Exception {
        when(accountService.getAccount(1L)).thenReturn(aliceAccount);
        when(securityUtilsService.isAdmin()).thenReturn(false);
        doNothing().when(securityUtilsService).validateAccountOwnership("ACC-0001");

        accountController.getAccount(1L);

        verify(securityUtilsService).validateAccountOwnership("ACC-0001");
    }

    @Test
    @DisplayName("Should throw AuthorizationException when accessing unauthorized account")
    void testGetAccountThrowsAuthorizationException() throws Exception {
        when(accountService.getAccount(2L)).thenReturn(brianAccount);
        when(securityUtilsService.isAdmin()).thenReturn(false);
        doThrow(new AuthorizationException("User does not have permission to access account: ACC-0002"))
                .when(securityUtilsService)
                .validateAccountOwnership("ACC-0002");

        assertThrows(AuthorizationException.class, () -> {
            accountController.getAccount(2L);
        });

        verify(securityUtilsService).validateAccountOwnership("ACC-0002");
    }

    @Test
    @DisplayName("Should skip authorization check for admin accessing any account")
    void testGetAccountSkipsAuthorizationForAdmin() throws Exception {
        when(accountService.getAccount(2L)).thenReturn(brianAccount);
        when(securityUtilsService.isAdmin()).thenReturn(true);

        accountController.getAccount(2L);

        verify(securityUtilsService, never()).validateAccountOwnership("ACC-0002");
    }

    @Test
    @DisplayName("Should validate account ownership when getting balance")
    void testGetBalanceValidatesOwnership() throws Exception {
        when(accountService.getAccount(1L)).thenReturn(aliceAccount);
        when(securityUtilsService.isAdmin()).thenReturn(false);
        doNothing().when(securityUtilsService).validateAccountOwnership("ACC-0001");
        when(accountService.getBalance(1L)).thenReturn(new BigDecimal("10000.00"));

        accountController.getBalance(1L);

        verify(securityUtilsService).validateAccountOwnership("ACC-0001");
    }

    @Test
    @DisplayName("Should throw AuthorizationException when accessing unauthorized balance")
    void testGetBalanceThrowsAuthorizationException() throws Exception {
        when(accountService.getAccount(2L)).thenReturn(brianAccount);
        when(securityUtilsService.isAdmin()).thenReturn(false);
        doThrow(new AuthorizationException("User does not have permission to access account: ACC-0002"))
                .when(securityUtilsService)
                .validateAccountOwnership("ACC-0002");

        assertThrows(AuthorizationException.class, () -> {
            accountController.getBalance(2L);
        });

        verify(securityUtilsService).validateAccountOwnership("ACC-0002");
    }

    @Test
    @DisplayName("Should validate account ownership when getting positions")
    void testGetPositionsValidatesOwnership() throws Exception {
        when(accountService.getAccount(1L)).thenReturn(aliceAccount);
        when(securityUtilsService.isAdmin()).thenReturn(false);
        doNothing().when(securityUtilsService).validateAccountOwnership("ACC-0001");
        when(accountService.getPositions(1L)).thenReturn(new Positions());

        accountController.getPositions(1L);

        verify(securityUtilsService).validateAccountOwnership("ACC-0001");
    }

    @Test
    @DisplayName("Should throw AuthorizationException when accessing unauthorized positions")
    void testGetPositionsThrowsAuthorizationException() throws Exception {
        when(accountService.getAccount(2L)).thenReturn(brianAccount);
        when(securityUtilsService.isAdmin()).thenReturn(false);
        doThrow(new AuthorizationException("User does not have permission to access account: ACC-0002"))
                .when(securityUtilsService)
                .validateAccountOwnership("ACC-0002");

        assertThrows(AuthorizationException.class, () -> {
            accountController.getPositions(2L);
        });

        verify(securityUtilsService).validateAccountOwnership("ACC-0002");
    }

    @Test
    @DisplayName("Should validate account ownership when getting orders")
    void testGetOrdersValidatesOwnership() throws Exception {
        when(accountService.getAccount(1L)).thenReturn(aliceAccount);
        when(securityUtilsService.isAdmin()).thenReturn(false);
        doNothing().when(securityUtilsService).validateAccountOwnership("ACC-0001");
        when(accountService.getOrders(1L)).thenReturn(new OrderHistory());

        accountController.getOrders(1L);

        verify(securityUtilsService).validateAccountOwnership("ACC-0001");
    }

    @Test
    @DisplayName("Should throw AuthorizationException when accessing unauthorized orders")
    void testGetOrdersThrowsAuthorizationException() throws Exception {
        when(accountService.getAccount(2L)).thenReturn(brianAccount);
        when(securityUtilsService.isAdmin()).thenReturn(false);
        doThrow(new AuthorizationException("User does not have permission to access account: ACC-0002"))
                .when(securityUtilsService)
                .validateAccountOwnership("ACC-0002");

        assertThrows(AuthorizationException.class, () -> {
            accountController.getOrders(2L);
        });

        verify(securityUtilsService).validateAccountOwnership("ACC-0002");
    }
}
