package com.neueda.leap.controllers;

import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.exceptions.GlobalExceptionHandler;
import com.neueda.leap.models.Account;
import com.neueda.leap.models.Positions;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.repositories.PositionsRepository;
import com.neueda.leap.repositories.AccountStatusHistoryRepository;
import com.neueda.leap.security.SecurityConfig;
import com.neueda.leap.security.SecurityUtilsService;
import com.neueda.leap.services.AdminAccountService;
import com.neueda.leap.services.AdminObservabilityService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AdminAccountControllerTest.TestApplication.class)
@WebAppConfiguration
@TestPropertySource(properties = "jwt.shared-secret=test-only-secret-with-at-least-32-bytes")
class AdminAccountControllerTest {
    @MockitoBean private AdminObservabilityService monitoring;
    private static final String SECRET = "test-only-secret-with-at-least-32-bytes";
    @Autowired private WebApplicationContext context;
    @Autowired private AccountRepository accounts;
    @Autowired private PositionsRepository positions;
    @Autowired private OrderRepository orders;
    @Autowired private AccountStatusHistoryRepository statusHistory;
    private MockMvc mvc;
    private Account target;

    @BeforeEach
    void setup() {
        reset(accounts, positions, orders, statusHistory, monitoring);
        Account admin = account(1L, "admin", "ADMIN,USER");
        target = account(2L, "customer", "USER");
        when(accounts.findById(1L)).thenReturn(Optional.of(admin));
        when(accounts.findById(2L)).thenReturn(Optional.of(target));
        when(accounts.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(accounts.findByUsername("customer")).thenReturn(Optional.of(target));
        when(accounts.findByIdForUpdate(2L)).thenReturn(Optional.of(target));
        when(accounts.findAll()).thenReturn(List.of(admin, target));
        when(accounts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    private Account account(Long id, String username, String roles) {
        Account account = new Account("ACC-000" + id, username, "test-password", roles,
                username, username + "@example.com", null, BigDecimal.ZERO, AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    private String token(long id, String role) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder()
                .subject(String.valueOf(id)).claim("username", id == 1 ? "admin" : "customer")
                .claim("roles", List.of(role)).claim("amr", List.of("pwd", "email_otp"))
                .expirationTime(Date.from(Instant.now().plusSeconds(60))).build());
        jwt.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));
        return "Bearer " + jwt.serialize();
    }

    private MockHttpServletRequestBuilder statusRequest(long id, String status) {
        return put("/v1/admin/accounts/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\",\"reason\":\"test administrator reason\"}");
    }

    @ParameterizedTest @ValueSource(strings = {"list", "status"})
    void anonymousRequestsAreRejected(String action) throws Exception {
        mvc.perform(action.equals("list") ? get("/v1/admin/accounts") : statusRequest(2, "SUSPENDED"))
                .andExpect(status().isUnauthorized());
        verify(accounts, never()).findByIdForUpdate(any());
    }

    @ParameterizedTest @ValueSource(strings = {"list", "status"})
    void regularUsersCannotUseAdminEndpointsEvenForTheirOwnAccount(String action) throws Exception {
        mvc.perform((action.equals("list") ? get("/v1/admin/accounts") : statusRequest(2, "SUSPENDED"))
                .header("Authorization", token(2, "USER"))).andExpect(status().isForbidden());
        verify(accounts, never()).findAll();
        verify(accounts, never()).findByIdForUpdate(any());
    }

    @Test void adminsSeeManagementDataWithoutCredentials() throws Exception {
        mvc.perform(get("/v1/admin/accounts").header("Authorization", token(1, "ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].email").value("customer@example.com"))
                .andExpect(jsonPath("$[1].cashBalance").value(0))
                .andExpect(jsonPath("$[1].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[1].password").doesNotExist());
    }

    @Test void suspensionBlocksPreviouslyIssuedAccessTokens() throws Exception {
        String customerToken = token(2, "USER");
        mvc.perform(get("/v1/admin/accounts").header("Authorization", customerToken)).andExpect(status().isForbidden());
        mvc.perform(statusRequest(2, "SUSPENDED").header("Authorization", token(1, "ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));
        mvc.perform(get("/v1/admin/accounts").header("Authorization", customerToken)).andExpect(status().isUnauthorized());
        verify(accounts).findByIdForUpdate(2L);
        verify(accounts).save(target);
    }

    @Test void adminsCanReactivateSuspendedAccounts() throws Exception {
        target.suspendAccount();
        mvc.perform(statusRequest(2, "ACTIVE").header("Authorization", token(1, "ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test void adminsCannotSuspendThemselves() throws Exception {
        mvc.perform(statusRequest(1, "SUSPENDED").header("Authorization", token(1, "ADMIN")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message", containsString("own admin account")));
        verify(accounts, never()).save(any());
    }

    @Test void closureRequiresZeroCash() throws Exception {
        target.credit(BigDecimal.TEN);
        mvc.perform(statusRequest(2, "CLOSED").header("Authorization", token(1, "ADMIN")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message", containsString("remaining cash")));
        verify(accounts, never()).save(any());
    }

    @Test void closureRequiresNoHoldings() throws Exception {
        Positions holding = mock(Positions.class);
        when(holding.getQuantity()).thenReturn(1);
        when(positions.findByAccountId(2L)).thenReturn(List.of(holding));
        mvc.perform(statusRequest(2, "CLOSED").header("Authorization", token(1, "ADMIN")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message", containsString("holdings")));
        verify(accounts, never()).save(any());
    }

    @Test void closureRequiresNoPendingOrders() throws Exception {
        when(orders.existsByAccount_IdAndStatusIn(2L, List.of(OrderStatus.NEW, OrderStatus.PENDING))).thenReturn(true);
        mvc.perform(statusRequest(2, "CLOSED").header("Authorization", token(1, "ADMIN")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message", containsString("pending orders")));
        verify(accounts, never()).save(any());
    }

    @Test void emptyAccountsCanBeClosedWithoutDeletingTheirHistory() throws Exception {
        mvc.perform(statusRequest(2, "CLOSED").header("Authorization", token(1, "ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
        verify(accounts).save(target);
        verify(accounts, never()).deleteById(any());
        verify(orders).existsByAccount_IdAndStatusIn(2L, List.of(OrderStatus.NEW, OrderStatus.PENDING));
        verifyNoMoreInteractions(orders);
    }

    @Test void closedAccountsCannotBeReopened() throws Exception {
        target.setStatus(AccountStatus.CLOSED);
        mvc.perform(statusRequest(2, "ACTIVE").header("Authorization", token(1, "ADMIN")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message", containsString("cannot be reopened")));
        verify(accounts, never()).save(any());
    }

    @Test void repeatedStatusRequestsAreIdempotent() throws Exception {
        target.suspendAccount();
        mvc.perform(statusRequest(2, "SUSPENDED").header("Authorization", token(1, "ADMIN"))).andExpect(status().isOk());
        verify(accounts, never()).save(any());
    }

    @Test void unknownAccountsReturnNotFound() throws Exception {
        mvc.perform(statusRequest(99, "SUSPENDED").header("Authorization", token(1, "ADMIN"))).andExpect(status().isNotFound());
    }

    @ParameterizedTest @ValueSource(strings = {"{\"status\":null}", "{\"status\":\"INVALID\"}", "{}"})
    void invalidStatusesCannotReachTheService(String body) throws Exception {
        mvc.perform(put("/v1/admin/accounts/2/status").contentType(MediaType.APPLICATION_JSON).content(body)
                .header("Authorization", token(1, "ADMIN"))).andExpect(status().isBadRequest());
        verify(accounts, never()).findByIdForUpdate(any());
    }

    @ParameterizedTest @ValueSource(strings = {"ANONYMOUS", "USER", "ADMIN"})
    void observabilityIsAvailableOnlyToAdmins(String role) throws Exception {
        when(monitoring.snapshot()).thenReturn(new AdminObservabilityService.Snapshot("UNAVAILABLE", "Monitoring is unavailable.",
                Instant.now(), 3600, 30, "", List.of(), List.of(), List.of(), List.of(), java.util.Map.of()));
        var request = get("/v1/admin/observability");
        if (!role.equals("ANONYMOUS")) request.header("Authorization", token(1, role));
        mvc.perform(request).andExpect(status().is(role.equals("ADMIN") ? 200 : role.equals("USER") ? 403 : 401));
        if (role.equals("ADMIN")) verify(monitoring).snapshot();
        else verifyNoInteractions(monitoring);
    }

    @TestConfiguration @EnableWebSecurity @EnableWebMvc
    @Import({SecurityConfig.class, SecurityUtilsService.class, AdminAccountController.class,
            AdminAccountService.class, AdminObservabilityController.class, GlobalExceptionHandler.class})
    static class TestApplication {
        @Bean AccountRepository accounts() { return mock(AccountRepository.class); }
        @Bean PositionsRepository positions() { return mock(PositionsRepository.class); }
        @Bean OrderRepository orders() { return mock(OrderRepository.class); }
        @Bean AccountStatusHistoryRepository statusHistory() { return mock(AccountStatusHistoryRepository.class); }
    }
}
