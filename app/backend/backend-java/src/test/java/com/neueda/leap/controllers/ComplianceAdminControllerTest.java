package com.neueda.leap.controllers;

import com.neueda.leap.compliance.ComplianceDecision;
import com.neueda.leap.dtos.RestrictedInstrumentResponse;
import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.exceptions.GlobalExceptionHandler;
import com.neueda.leap.models.Account;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.security.SecurityConfig;
import com.neueda.leap.security.SecurityUtilsService;
import com.neueda.leap.services.ComplianceAdminService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Only admins can see the compliance audit trail or change the restricted list; the real JWT filter runs */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ComplianceAdminControllerTest.TestApplication.class)
@WebAppConfiguration
@TestPropertySource(properties = "jwt.shared-secret=test-only-secret-with-at-least-32-bytes")
class ComplianceAdminControllerTest {
    private static final String SECRET = "test-only-secret-with-at-least-32-bytes";
    @Autowired private WebApplicationContext context;
    @Autowired private AccountRepository accounts;
    @Autowired private ComplianceAdminService compliance;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        reset(accounts, compliance);
        Account admin = account(1L, "admin", "ADMIN,USER");
        Account customer = account(2L, "customer", "USER");
        when(accounts.findById(1L)).thenReturn(Optional.of(admin));
        when(accounts.findById(2L)).thenReturn(Optional.of(customer));
        when(accounts.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(accounts.findByUsername("customer")).thenReturn(Optional.of(customer));
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

    @Test void anonymousAndCustomersCannotSeeTheAuditTrail() throws Exception {
        mvc.perform(get("/v1/admin/compliance/checks")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/admin/compliance/checks").header("Authorization", token(2, "USER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/v1/admin/compliance/restricted").header("Authorization", token(2, "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"symbol\":\"META\",\"reason\":\"review\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(compliance);
    }

    @Test void adminSeesSubmissionsFilteredByDecision() throws Exception {
        when(compliance.submissions(ComplianceDecision.BLOCKED, "ACC-0002", "tsla", 20)).thenReturn(List.of());
        mvc.perform(get("/v1/admin/compliance/checks").header("Authorization", token(1, "ADMIN"))
                        .param("decision", "BLOCKED").param("accountId", "ACC-0002").param("symbol", "tsla").param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        verify(compliance).submissions(ComplianceDecision.BLOCKED, "ACC-0002", "tsla", 20);
    }

    @Test void adminAddsARestrictionInTheirOwnName() throws Exception {
        when(compliance.restrict("META", "pending corporate action", "admin")).thenReturn(
                new RestrictedInstrumentResponse("META", "Meta Platforms Inc.", "pending corporate action", "admin", LocalDateTime.now()));
        mvc.perform(post("/v1/admin/compliance/restricted").header("Authorization", token(1, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"symbol\":\"META\",\"reason\":\"pending corporate action\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol").value("META"))
                .andExpect(jsonPath("$.addedBy").value("admin"));
    }

    @Test void restrictionNeedsAReason() throws Exception {
        mvc.perform(post("/v1/admin/compliance/restricted").header("Authorization", token(1, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"symbol\":\"META\",\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(compliance);
    }

    @Test void duplicateRestrictionIsAConflict() throws Exception {
        when(compliance.restrict(any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "TSLA is already on the restricted list."));
        mvc.perform(post("/v1/admin/compliance/restricted").header("Authorization", token(1, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"symbol\":\"TSLA\",\"reason\":\"review\"}"))
                .andExpect(status().isConflict());
    }

    @Test void adminRemovesARestriction() throws Exception {
        mvc.perform(delete("/v1/admin/compliance/restricted/TSLA").header("Authorization", token(1, "ADMIN")))
                .andExpect(status().isNoContent());
        verify(compliance).unrestrict("TSLA", "admin");
    }

    @Test void customersCannotChangeRiskProfiles() throws Exception {
        mvc.perform(put("/v1/admin/compliance/risk-profiles/2").header("Authorization", token(2, "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"riskProfile\":\"AGGRESSIVE\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(compliance);
    }

    @Test void adminChangesARiskProfileInTheirOwnName() throws Exception {
        when(compliance.setRiskProfile(2L, com.neueda.leap.enums.RiskProfile.CONSERVATIVE, "admin")).thenReturn(
                new com.neueda.leap.dtos.AccountRiskProfileResponse(2L, "ACC-0002", "customer", "customer",
                        AccountStatus.ACTIVE, com.neueda.leap.enums.RiskProfile.CONSERVATIVE));
        mvc.perform(put("/v1/admin/compliance/risk-profiles/2").header("Authorization", token(1, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"riskProfile\":\"CONSERVATIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskProfile").value("CONSERVATIVE"));
    }

    @Test void riskProfileMustBeAKnownValue() throws Exception {
        mvc.perform(put("/v1/admin/compliance/risk-profiles/2").header("Authorization", token(1, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"riskProfile\":\"RECKLESS\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/v1/admin/compliance/risk-profiles/2").header("Authorization", token(1, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(compliance);
    }

    @TestConfiguration @EnableWebSecurity @EnableWebMvc
    @Import({SecurityConfig.class, SecurityUtilsService.class, ComplianceAdminController.class, GlobalExceptionHandler.class})
    static class TestApplication {
        @Bean AccountRepository accounts() { return mock(AccountRepository.class); }
        @Bean ComplianceAdminService compliance() { return mock(ComplianceAdminService.class); }
    }
}
