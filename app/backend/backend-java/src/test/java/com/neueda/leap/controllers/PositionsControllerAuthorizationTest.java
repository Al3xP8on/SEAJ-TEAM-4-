package com.neueda.leap.controllers;

import com.neueda.leap.dtos.PositionResponse;
import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.enums.PositionStatus;
import com.neueda.leap.exceptions.GlobalExceptionHandler;
import com.neueda.leap.models.Account;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.security.SecurityConfig;
import com.neueda.leap.security.SecurityUtilsService;
import com.neueda.leap.services.PositionsService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PositionsControllerAuthorizationTest.TestApplication.class)
@WebAppConfiguration
@TestPropertySource(properties = "jwt.shared-secret=test-only-secret-with-at-least-32-bytes")
class PositionsControllerAuthorizationTest {
    private static final String SECRET = "test-only-secret-with-at-least-32-bytes";
    @Autowired private WebApplicationContext context;
    @Autowired private AccountRepository accounts;
    @MockitoBean private PositionsService positions;
    private MockMvc mvc;

    @BeforeEach
    void setup() throws Exception {
        reset(accounts, positions);
        Account user = new Account("ACC-0001", "jay", "test-password", "Jay", "jay@example.com",
                null, BigDecimal.TEN, AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 1L);
        when(accounts.findById(1L)).thenReturn(Optional.of(user));
        when(accounts.findByUsername("jay")).thenReturn(Optional.of(user));
        PositionResponse response = new PositionResponse(2L, 1L, "AAPL", 3, BigDecimal.TEN,
                PositionStatus.OPEN, LocalDateTime.of(2026, 1, 1, 12, 0), null, BigDecimal.ZERO);
        when(positions.getPositions(anyLong())).thenReturn(List.of(response));
        when(positions.getPosition(anyLong(), anyLong())).thenReturn(response);
        when(positions.getPositionBySymbol(anyLong(), anyString())).thenReturn(Optional.of(response));
        when(positions.closePosition(anyLong(), anyLong(), any())).thenReturn(response);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    private MockHttpServletRequestBuilder request(String action, long accountId) {
        String path = "/v1/positions/" + accountId + "/positions";
        return switch (action) {
            case "list" -> get(path);
            case "one" -> get(path + "/2");
            case "symbol" -> get(path + "/symbol/AAPL");
            case "close" -> put(path + "/close/2").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"closingPrice\":10,\"closingQuantity\":3}");
            default -> throw new IllegalArgumentException(action);
        };
    }

    private String token(String role) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder()
                .subject("1").claim("username", "jay").claim("roles", List.of(role))
                .claim("amr", List.of("pwd", "email_otp"))
                .expirationTime(Date.from(Instant.now().plusSeconds(60))).build());
        jwt.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));
        return "Bearer " + jwt.serialize();
    }

    @ParameterizedTest @ValueSource(strings = {"list", "one", "symbol", "close"})
    void anonymousRequestsCannotReadOrChangePositions(String action) throws Exception {
        mvc.perform(request(action, 1)).andExpect(status().isUnauthorized());
        verifyNoInteractions(positions);
    }

    @ParameterizedTest @ValueSource(strings = {"list", "one", "symbol", "close"})
    void ownersCanAccessTheirOwnPositions(String action) throws Exception {
        mvc.perform(request(action, 1).header(HttpHeaders.AUTHORIZATION, token("USER")))
                .andExpect(status().isOk());
    }

    @ParameterizedTest @ValueSource(strings = {"list", "one", "symbol", "close"})
    void otherAccountsAreForbiddenBeforeReadingOrChangingPositions(String action) throws Exception {
        mvc.perform(request(action, 2).header(HttpHeaders.AUTHORIZATION, token("USER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(positions);
    }

    @ParameterizedTest @ValueSource(strings = {"list", "one", "symbol", "close"})
    void adminsCanAccessAnotherAccountsPositions(String action) throws Exception {
        mvc.perform(request(action, 2).header(HttpHeaders.AUTHORIZATION, token("ADMIN")))
                .andExpect(status().isOk());
    }

    @TestConfiguration @EnableWebSecurity @EnableWebMvc
    @Import({SecurityConfig.class, SecurityUtilsService.class, PositionsController.class, GlobalExceptionHandler.class})
    static class TestApplication {
        @Bean AccountRepository accountRepository() { return mock(AccountRepository.class); }
    }
}
