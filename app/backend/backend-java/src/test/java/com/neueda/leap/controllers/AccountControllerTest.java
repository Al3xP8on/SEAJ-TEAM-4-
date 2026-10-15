// Tests account endpoints and forwarding of registration, login and verification requests.
// Checks challenge responses, safe upstream errors and prevention of direct signup or token bypass.
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
    private final AccountInput input = new AccountInput("alice", "password", "alice@example.com", null);
    private final EmailVerificationRequest verification = new EmailVerificationRequest("00000000-0000-4000-8000-000000000001", "123456");

    @Test void returnsEmailChallengeWithoutIssuingOrFetchingAToken() {
        Map<String, Object> body = Map.of("challenge_id", verification.challenge_id(), "expires_in", 300, "message", "Enter code");
        when(restTemplate.postForEntity(eq("http://auth-service:3000/auth/login"), eq(Map.of("username", "alice", "password", "password")), eq(Object.class)))
                .thenReturn(ResponseEntity.accepted().body(body));
        var response = controller().login(new LoginRequest("alice", "password"));
        assertEquals(202, response.getStatusCode().value()); assertSame(body, response.getBody()); verifyNoInteractions(service);
    }
    @Test void registrationDelegatesToEmailVerificationRatherThanCreatingLocally() {
        Map<String, Object> body = Map.of("challenge_id", verification.challenge_id(), "expires_in", 300);
        when(restTemplate.postForEntity(eq("http://auth-service:3000/auth/register"), eq(Map.of("username", "alice", "password", "password", "email", "alice@example.com")), eq(Object.class)))
                .thenReturn(ResponseEntity.accepted().body(body));
        var response = controller().createAccount(input);
        assertEquals(202, response.getStatusCode().value()); assertSame(body, response.getBody()); verifyNoInteractions(service);
    }
    @Test void forwardsVerifiedRegistrationWithGeneratedAccountId() {
        Map<String, Object> body = Map.of("account_id", "ACC-7A3F91C2", "username", "alice", "email_verified", true);
        when(restTemplate.postForEntity(eq("http://auth-service:3000/auth/register/verify"), eq(Map.of("challenge_id", verification.challenge_id(), "code", "123456")), eq(Object.class)))
                .thenReturn(ResponseEntity.status(201).body(body));
        var response = controller().verifyRegistration(verification);
        assertEquals(201, response.getStatusCode().value()); assertSame(body, response.getBody()); verifyNoInteractions(service);
    }
    @Test void onlyVerificationEndpointForwardsIssuedTokens() {
        Map<String, Object> body = Map.of("access_token", "signed-token", "refresh_token", "refresh-token", "expires_in", 1800);
        when(restTemplate.postForEntity(eq("http://auth-service:3000/auth/login/verify"), eq(Map.of("challenge_id", verification.challenge_id(), "code", "123456")), eq(Object.class)))
                .thenReturn(ResponseEntity.ok(body));
        var response = controller().verifyLogin(verification);
        assertEquals(200, response.getStatusCode().value()); assertSame(body, response.getBody());
    }
    @Test void rejectsUnexpectedDirectTokenAndMalformedResponses() {
        when(restTemplate.postForEntity(anyString(), any(), eq(Object.class))).thenReturn(ResponseEntity.ok(Map.of("access_token", "token")));
        assertEquals(503, controller().login(new LoginRequest("alice", "password")).getStatusCode().value());
        when(restTemplate.postForEntity(anyString(), any(), eq(Object.class))).thenReturn(ResponseEntity.accepted().body(Map.of("access_token", "token")));
        assertEquals(503, controller().login(new LoginRequest("alice", "password")).getStatusCode().value());
        when(restTemplate.postForEntity(anyString(), any(), eq(Object.class))).thenReturn(ResponseEntity.ok().build());
        assertEquals(503, controller().login(new LoginRequest("alice", "password")).getStatusCode().value()); verifyNoInteractions(service);
    }
    @Test void returnsGenericUnavailableErrorForConnectionFailure() {
        when(restTemplate.postForEntity(anyString(), any(), eq(Object.class))).thenThrow(new ResourceAccessException("sensitive connection details"));
        var response = controller().login(new LoginRequest("alice", "password"));
        assertEquals(503, response.getStatusCode().value());
        assertEquals("Authentication service is unavailable", ((Map<?, ?>) response.getBody()).get("message"));
    }
    @ParameterizedTest @ValueSource(ints = {400, 401, 409, 429, 503})
    void preservesExpectedAuthErrorsWithoutUpstreamExceptionDetails(int status) {
        when(restTemplate.postForEntity(anyString(), any(), eq(Object.class))).thenThrow(new org.springframework.web.client.HttpClientErrorException(org.springframework.http.HttpStatus.valueOf(status), "sensitive upstream details"));
        var response = controller().createAccount(input);
        assertEquals(status, response.getStatusCode().value());
        assertFalse(response.getBody().toString().contains("sensitive"));
    }
    @Test void rejectsDirectDeletionThatWouldBypassEmailVerification() {
        var response = controller().deleteAccount("ACC-001");
        assertEquals(403, response.getStatusCode().value());
        assertTrue(response.getBody().toString().contains("email-verified"));
        verifyNoInteractions(service);
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

    @Test void nonAdminGetAllAccountsReturnsOnlyOwnAccount() {
        Account account = mock(Account.class);
        AccountController controller = controller();
        when(securityUtilsService.isAdmin()).thenReturn(false);
        when(securityUtilsService.getAuthenticatedUserAccount()).thenReturn(account);
        var response = controller.getAllAccounts();
        assertEquals(200, response.getStatusCode().value());
        assertTrue(((List<?>) response.getBody()).contains(account));
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
        assertEquals(BigDecimal.TEN, controller.getBalance("ACC-001").getBody());
        assertEquals("AAPL", controller.getPositions("ACC-001").getBody().get(0).symbol());
        assertEquals(OrderStatus.FILLED, controller.getOrders("ACC-001").getBody().status());
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
