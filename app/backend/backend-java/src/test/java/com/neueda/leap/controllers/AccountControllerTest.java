package com.neueda.leap.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import com.neueda.leap.security.SecurityUtilsService;
import com.neueda.leap.services.AccountService;
import com.neueda.leap.dtos.*;
import com.neueda.leap.models.*;
import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.exceptions.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;
import java.math.BigDecimal;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import static org.mockito.Mockito.*;

class AccountControllerTest {
    private final AccountService service = mock(AccountService.class);
    private final SecurityUtilsService securityUtilsService = mock(SecurityUtilsService.class);
    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private AccountController controller() {
        AccountController controller = new AccountController();
        ReflectionTestUtils.setField(controller, "accountService", service);
        ReflectionTestUtils.setField(controller, "securityUtilsService", securityUtilsService);
        ReflectionTestUtils.setField(controller, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(controller, "authServiceUrl", "http://auth-service:3000");
        when(securityUtilsService.isAdmin()).thenReturn(true);
        return controller;
    }
    private final AccountInput input = new AccountInput("alice", "password", null, null);
    @ParameterizedTest
    @ValueSource(longs = {1800L, 3600L})
    void returnsLoginTokenWithProvidedOrDefaultExpiry(long expiresIn) throws Exception {
        Map<String, Object> authBody = new HashMap<>();
        authBody.put("access_token", "test-token");
        if (expiresIn != 1800L) {
            authBody.put("expiresIn", expiresIn);
        }
        when(restTemplate.postForEntity(eq("http://auth-service:3000/auth/login"),
                eq(Map.of("username", "alice", "password", "password")), eq(Object.class)))
                .thenReturn(ResponseEntity.ok(authBody));
        Account account = mock(Account.class);
        when(account.getAccountId()).thenReturn("ACC-001");
        when(service.getAccountByUsername("alice")).thenReturn(account);

        var response = controller().login(new LoginRequest("alice", "password"));

        assertEquals(200, response.getStatusCode().value());
        LoginResponse body = assertInstanceOf(LoginResponse.class, response.getBody());
        assertEquals("test-token", body.getToken());
        assertEquals("alice", body.getUsername());
        assertEquals("ACC-001", body.getAccountId());
        assertEquals(Long.valueOf(expiresIn), body.getExpiresIn());
    }
    @Test void rejectsLoginWhenAuthServiceReturnsAnEmptyBody() {
        when(restTemplate.postForEntity(anyString(), any(), eq(Object.class)))
                .thenReturn(ResponseEntity.ok().build());

        var response = controller().login(new LoginRequest("alice", "password"));

        assertEquals(401, response.getStatusCode().value());
        assertEquals("Authentication failed", response.getBody());
        verifyNoInteractions(service);
    }
    @Test void rejectsLoginWhenAuthServiceReturnsAnUnexpectedBodyType() {
        when(restTemplate.postForEntity(anyString(), any(), eq(Object.class)))
                .thenReturn(ResponseEntity.ok("invalid response"));

        var response = controller().login(new LoginRequest("alice", "password"));

        assertEquals(401, response.getStatusCode().value());
        assertEquals("Authentication failed", response.getBody());
        verifyNoInteractions(service);
    }
    @Test void rejectsLoginWhenAuthServiceReturnsAnUnsuccessfulStatus() {
        when(restTemplate.postForEntity(anyString(), any(), eq(Object.class)))
                .thenReturn(ResponseEntity.status(401).body(Map.of("access_token", "invalid-token")));

        var response = controller().login(new LoginRequest("alice", "password"));

        assertEquals(401, response.getStatusCode().value());
        assertEquals("Authentication failed", response.getBody());
        verifyNoInteractions(service);
    }
    @Test void reportsAuthenticationServiceConnectionFailure() {
        when(restTemplate.postForEntity(anyString(), any(), eq(Object.class)))
                .thenThrow(new ResourceAccessException("Auth service unavailable"));

        var response = controller().login(new LoginRequest("alice", "password"));

        assertEquals(401, response.getStatusCode().value());
        assertEquals("Authentication failed: Auth service unavailable", response.getBody());
        verifyNoInteractions(service);
    }
    @Test void rejectsLoginWhenAuthenticatedUserHasNoLocalAccount() throws Exception {
        when(restTemplate.postForEntity(anyString(), any(), eq(Object.class)))
                .thenReturn(ResponseEntity.ok(Map.of("access_token", "test-token")));
        when(service.getAccountByUsername("alice"))
                .thenThrow(new AccountNotFoundException("Account with username alice not found"));

        var response = controller().login(new LoginRequest("alice", "password"));

        assertEquals(401, response.getStatusCode().value());
        assertEquals("Authentication failed: Account with username alice not found", response.getBody());
        verify(service).getAccountByUsername("alice");
    }
    @Test void rejectsLoginWhenAuthServiceSuppliesAnInvalidExpiry() {
        when(restTemplate.postForEntity(anyString(), any(), eq(Object.class)))
                .thenReturn(ResponseEntity.ok(Map.of("access_token", "test-token", "expiresIn", "invalid")));

        var response = controller().login(new LoginRequest("alice", "password"));

        assertEquals(401, response.getStatusCode().value());
        assertTrue(assertInstanceOf(String.class, response.getBody()).startsWith("Authentication failed:"));
        verifyNoInteractions(service);
    }
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
    @Test void returnsNoContentAfterDeletingAccount() throws Exception {
        Account account = mock(Account.class);
        when(account.getId()).thenReturn(1L);
        when(service.getAccountByIdentifier("ACC-001")).thenReturn(account);

        var response = controller().deleteAccount("ACC-001");

        assertEquals(204, response.getStatusCode().value());
        assertNull(response.getBody());
        verify(service).deleteAccount(1L);
    }
    @Test void returnsNotFoundWhenDeletingMissingAccount() throws Exception {
        when(service.getAccountByIdentifier("ACC-001")).thenThrow(new AccountNotFoundException("missing"));

        var response = controller().deleteAccount("ACC-001");

        assertEquals(404, response.getStatusCode().value());
        assertEquals("missing", response.getBody());
        verify(service, never()).deleteAccount(anyLong());
    }
    @Test void reportsUnexpectedDeletionFailure() throws Exception {
        Account account = mock(Account.class);
        when(account.getId()).thenReturn(1L);
        when(service.getAccountByIdentifier("ACC-001")).thenReturn(account);
        doThrow(new IllegalStateException("offline")).when(service).deleteAccount(1L);

        var response = controller().deleteAccount("ACC-001");

        assertEquals(500, response.getStatusCode().value());
        assertEquals("offline", response.getBody());
        verify(service).deleteAccount(1L);
    }
    @Test void listsAccountsAndHandlesRepositoryFailure() {
        // Mock admin user
        when(securityUtilsService.isAdmin()).thenReturn(true);
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
        List<Positions> positions = List.of(mock(Positions.class));
        when(account.getId()).thenReturn(1L);
        when(account.getAccountId()).thenReturn("ACC-001");
        when(account.getName()).thenReturn("Alice");
        when(positions.get(0).getSymbol()).thenReturn("AAPL");
        OrderHistory orders = mock(OrderHistory.class);
        when(orders.getStatus()).thenReturn(OrderStatus.FILLED);
        when(service.getAccountByIdentifier("ACC-001")).thenReturn(account);
        when(service.getBalance(1L)).thenReturn(BigDecimal.TEN);
        when(service.getPositions(1L)).thenReturn(positions);
        when(service.getOrders(1L)).thenReturn(orders);
        when(securityUtilsService.isAdmin()).thenReturn(true);  // Admin can access any account
        AccountController controller = controller();
        AccountResponse accountBody = controller.getAccount("ACC-001").getBody();
        assertEquals(1L, accountBody.id());
        assertEquals("ACC-001", accountBody.accountId());
        assertEquals("Alice", accountBody.holderName());
        assertEquals(BigDecimal.TEN, controller.getBalance(1L).getBody());
        assertEquals("AAPL", controller.getPositions(1L).getBody().get(0).symbol());
        assertEquals(OrderStatus.FILLED, controller.getOrders(1L).getBody().status());
    }
    @Test void returnsNotFoundForMissingAccountResources() throws Exception {
        when(service.getAccountByIdentifier("ACC-001")).thenThrow(new AccountNotFoundException("missing"));
        AccountController controller = controller();
        for (var response : List.of(controller.getAccount("ACC-001"), controller.getBalance("ACC-001"), controller.getPositions("ACC-001"), controller.getOrders("ACC-001"))) {
            assertEquals(404, response.getStatusCode().value());
            assertNull(response.getBody());
        }
    }
}
