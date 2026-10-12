package com.neueda.leap.controllers;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.neueda.leap.security.SecurityUtilsService;
import com.neueda.leap.services.AccountService;
import com.neueda.leap.dtos.*;
import com.neueda.leap.models.*;
import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.exceptions.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;

class AccountControllerTest {
    private final AccountService service = mock(AccountService.class);
    // Ownership rules are covered by AccountControllerAuthorizationTest; here the caller owns the account
    private final SecurityUtilsService security = mock(SecurityUtilsService.class);
    private AccountController controller() {
        AccountController controller = new AccountController();
        ReflectionTestUtils.setField(controller, "accountService", service);
        ReflectionTestUtils.setField(controller, "securityUtilsService", security);
        return controller;
    }
    private final AccountInput input = new AccountInput("alice", "password", null, null);
    @Test void returnsCreatedAccount() throws Exception {
        AccountResponse body = new AccountResponse(1L, "ACC-001", "Alice", BigDecimal.ZERO, AccountStatus.ACTIVE, Optional.empty());
        when(service.createAccount(input)).thenReturn(body);
        var response = controller().createAccount(input);
        assertEquals(201, response.getStatusCode().value());
        assertSame(body, response.getBody());
    }
    @Test void mapsDuplicateAccountAndUnexpectedCreationFailures() throws Exception {
        when(service.createAccount(input)).thenThrow(new DuplicateAccountException("duplicate"));
        var duplicate = controller().createAccount(input);
        assertEquals(409, duplicate.getStatusCode().value());
        assertEquals("duplicate", duplicate.getBody());
        doThrow(new IllegalStateException("offline")).when(service).createAccount(input);
        var failed = controller().createAccount(input);
        assertEquals(500, failed.getStatusCode().value());
        assertEquals("offline", failed.getBody());
    }
    @Test void listsAccountsAndHandlesRepositoryFailure() {
        when(security.isAdmin()).thenReturn(true);
        List<Account> accounts = List.of(mock(Account.class));
        when(service.getAllAccounts()).thenReturn(accounts);
        assertSame(accounts, controller().getAllAccounts().getBody());
        when(service.getAllAccounts()).thenThrow(new IllegalStateException("offline"));
        var response = controller().getAllAccounts();
        assertEquals(500, response.getStatusCode().value());
        assertEquals("Error retrieving accounts: offline", response.getBody());
    }
    @Test void returnsIndividualAccountResources() throws Exception {
        Account account = mock(Account.class);
        Positions positions = mock(Positions.class);
        OrderHistory orders = mock(OrderHistory.class);
        when(service.getAccount(1L)).thenReturn(account);
        when(service.getBalance(1L)).thenReturn(BigDecimal.TEN);
        when(service.getPositions(1L)).thenReturn(positions);
        when(service.getOrders(1L)).thenReturn(orders);
        AccountController controller = controller();
        assertSame(account, controller.getAccount(1L).getBody());
        assertEquals(BigDecimal.TEN, controller.getBalance(1L).getBody());
        assertSame(positions, controller.getPositions(1L).getBody());
        assertSame(orders, controller.getOrders(1L).getBody());
    }
    @Test void returnsNotFoundForMissingAccountResources() throws Exception {
        when(service.getAccount(1L)).thenThrow(new AccountNotFoundException("missing"));
        when(service.getBalance(1L)).thenThrow(new AccountNotFoundException("missing"));
        when(service.getPositions(1L)).thenThrow(new AccountNotFoundException("missing"));
        when(service.getOrders(1L)).thenThrow(new AccountNotFoundException("missing"));
        AccountController controller = controller();
        for (var response : List.of(controller.getAccount(1L), controller.getBalance(1L), controller.getPositions(1L), controller.getOrders(1L))) {
            assertEquals(404, response.getStatusCode().value());
            assertNull(response.getBody());
        }
    }
}
