package com.neueda.leap.controllers;

import com.neueda.leap.config.TestSecurityConfig;
import com.neueda.leap.dtos.AccountResponse;
import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.exceptions.AccountNotActiveException;
import com.neueda.leap.exceptions.InsufficientFundsException;
import com.neueda.leap.models.Account;
import com.neueda.leap.security.AuthorizationException;
import com.neueda.leap.security.SecurityUtilsService;
import com.neueda.leap.services.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
@Import(TestSecurityConfig.class)
@DisplayName("Account deposit and withdraw endpoints")
class AccountCashControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountService accountService;

    @MockBean
    private SecurityUtilsService securityUtilsService;

    @MockBean
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() throws Exception {
        Account account = mock(Account.class);
        when(account.getAccountId()).thenReturn("ACC-001");
        when(accountService.getAccount(1L)).thenReturn(account);
    }

    private static AccountResponse responseWithBalance(String balance) {
        return new AccountResponse(1L, "ACC-001", "Alice", new BigDecimal(balance), AccountStatus.ACTIVE, Optional.empty());
    }

    private static String body(String amount) {
        return "{\"amount\": " + amount + "}";
    }

    @Test
    @DisplayName("Deposit returns the account with its new balance")
    void depositSucceeds() throws Exception {
        when(accountService.deposit(1L, new BigDecimal("50.00"))).thenReturn(responseWithBalance("150.00"));

        mockMvc.perform(post("/v1/accounts/1/deposit").contentType(MediaType.APPLICATION_JSON).content(body("50.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashBalance").value(150.00));

        verify(securityUtilsService).validateAccountOwnership("ACC-001");
    }

    @Test
    @DisplayName("Withdraw returns the account with its new balance")
    void withdrawSucceeds() throws Exception {
        when(accountService.withdraw(1L, new BigDecimal("40.00"))).thenReturn(responseWithBalance("60.00"));

        mockMvc.perform(post("/v1/accounts/1/withdraw").contentType(MediaType.APPLICATION_JSON).content(body("40.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashBalance").value(60.00));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-10", "10.555", "1000000.01", "null"})
    @DisplayName("Invalid amounts are rejected with 400 before touching the balance")
    void invalidAmountRejected(String amount) throws Exception {
        for (String action : new String[] {"deposit", "withdraw"}) {
            mockMvc.perform(post("/v1/accounts/1/" + action).contentType(MediaType.APPLICATION_JSON).content(body(amount)))
                    .andExpect(status().isBadRequest());
        }
        verify(accountService, never()).deposit(any(), any());
        verify(accountService, never()).withdraw(any(), any());
    }

    @Test
    @DisplayName("Withdrawing more than the balance returns 400")
    void insufficientFunds() throws Exception {
        when(accountService.withdraw(eq(1L), any())).thenThrow(new InsufficientFundsException("Insufficient funds: balance is 10.00"));

        mockMvc.perform(post("/v1/accounts/1/withdraw").contentType(MediaType.APPLICATION_JSON).content(body("50.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Insufficient funds: balance is 10.00"));
    }

    @Test
    @DisplayName("Inactive accounts get 400")
    void inactiveAccount() throws Exception {
        when(accountService.deposit(eq(1L), any())).thenThrow(new AccountNotActiveException("Account is SUSPENDED and cannot move cash"));

        mockMvc.perform(post("/v1/accounts/1/deposit").contentType(MediaType.APPLICATION_JSON).content(body("50.00")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Moving cash in someone else's account returns 403 and changes nothing")
    void notOwner() throws Exception {
        doThrow(new AuthorizationException("User does not have permission to access account: ACC-001"))
                .when(securityUtilsService).validateAccountOwnership("ACC-001");

        for (String action : new String[] {"deposit", "withdraw"}) {
            mockMvc.perform(post("/v1/accounts/1/" + action).contentType(MediaType.APPLICATION_JSON).content(body("50.00")))
                    .andExpect(status().isForbidden());
        }
        verify(accountService, never()).deposit(any(), any());
        verify(accountService, never()).withdraw(any(), any());
    }
}
