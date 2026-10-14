// Tests protected-route access, JWT validation and authentication through the Spring security filters.
// Covers email-factor claims, invalid tokens and anonymous access to public authentication endpoints.
package com.neueda.leap.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Base64;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SecurityConfigTest.TestApplication.class)
@WebAppConfiguration
@TestPropertySource(properties = "jwt.shared-secret=test-only-secret-with-at-least-32-bytes")
class SecurityConfigTest {
    private static final String SECRET = "test-only-secret-with-at-least-32-bytes";

    @Autowired
    private WebApplicationContext applicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .addFilters(applicationContext.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private SecurityConfig config() {
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "sharedSecret", SECRET);
        return config;
    }
    private String token(String secret, Instant expiresAt) throws Exception {
        return token(secret, expiresAt, List.of("TRADER"));
    }

    private String token(String secret, Instant expiresAt, List<String> roles) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("alice")
                .claim("roles", roles)
                .claim("amr", List.of("pwd", "email_otp"))
                .expirationTime(Date.from(expiresAt))
                .build();
        SignedJWT token = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        token.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return token.serialize();
    }
    @Test void validatesSignedTokenAndPreservesSubjectAndRoleClaims() throws Exception {
        var jwt = config().jwtDecoder().decode(token(SECRET, Instant.now().plusSeconds(3600)));
        assertEquals("alice", jwt.getSubject());
        assertEquals(List.of("TRADER"), jwt.getClaimAsStringList("roles"));
    }
    @Test void rejectsSignedTokenWithoutEmailSecondFactor() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("alice").expirationTime(Date.from(Instant.now().plusSeconds(3600))).build();
        SignedJWT signed = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        signed.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));
        assertThrows(JwtException.class, () -> config().jwtDecoder().decode(signed.serialize()));
    }
    @Test void rejectsTokenSignedWithDifferentSecret() throws Exception {
        String token = token("different-test-only-secret-at-least-32-bytes", Instant.now().plusSeconds(3600));
        assertThrows(JwtException.class, () -> config().jwtDecoder().decode(token));
    }
    @Test void rejectsExpiredAndMalformedTokens() throws Exception {
        String expired = token(SECRET, Instant.now().minusSeconds(3600));
        assertThrows(JwtException.class, () -> config().jwtDecoder().decode(expired));
        assertThrows(JwtException.class, () -> config().jwtDecoder().decode("not-a-jwt"));
    }

    @Test
    void permitsAnonymousPublicAndApiDocumentationRequests() throws Exception {
        for (String path : List.of("/public", "/swagger-ui/index.html", "/v3/api-docs")) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().string("public"));
        }
    }

    @Test
    void permitsAnonymousSignupAndLoginWithoutACsrfToken() throws Exception {
        for (String path : List.of("/v1/accounts", "/v1/accounts/login", "/v1/accounts/login/verify", "/v1/accounts/register/verify")) {
            mockMvc.perform(post(path))
                    .andExpect(status().isOk())
                    .andExpect(content().string("public"));
        }
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /v1/accounts",
            "PUT, /v1/accounts",
            "PATCH, /v1/accounts",
            "DELETE, /v1/accounts",
            "HEAD, /v1/accounts",
            "GET, /v1/accounts/login",
            "PUT, /v1/accounts/login",
            "PATCH, /v1/accounts/login",
            "DELETE, /v1/accounts/login",
            "HEAD, /v1/accounts/login",
            "GET, /v1/accounts/login/verify",
            "GET, /v1/accounts/register/verify"
    })
    void rejectsAnonymousNonPostRequestsToSignupAndLogin(String method, String path) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), path))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertNull(result.getHandler()));
    }

    // Browsers send an unauthenticated OPTIONS preflight before cross-origin calls, so it must not get 401
    @ParameterizedTest
    @CsvSource({"/v1/accounts", "/v1/accounts/login", "/v1/orders"})
    void permitsAnonymousCorsPreflightRequests(String path) throws Exception {
        mockMvc.perform(request(HttpMethod.OPTIONS, path))
                .andExpect(result -> assertNotEquals(401, result.getResponse().getStatus()));
    }

    @Test
    void permitsCorsPreflightForAnonymousAuthenticationRequests() throws Exception {
        mockMvc.perform(options("/v1/accounts")
                .header("Origin", "http://localhost:8000")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk());
    }

    @Test
    void permitsAuthenticatedAccountListing() throws Exception {
        String bearer = token(SECRET, Instant.now().plusSeconds(3600));

        mockMvc.perform(get("/v1/accounts").header("Authorization", "Bearer " + bearer))
                .andExpect(status().isOk())
                .andExpect(content().string("alice:ROLE_TRADER"));
    }

    @Test
    void rejectsAnonymousRequestsToProtectedRoutes() throws Exception {
        mockMvc.perform(get("/v1/orders"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/v1/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatesBearerTokensAndConvertsRoleClaimsToSpringAuthorities() throws Exception {
        String bearer = token(SECRET, Instant.now().plusSeconds(3600), List.of("TRADER", "ADMIN"));

        mockMvc.perform(get("/v1/orders").header("Authorization", "Bearer " + bearer))
                .andExpect(status().isOk())
                .andExpect(content().string("alice:ROLE_ADMIN,ROLE_TRADER"));
    }

    @Test
    void acceptsAuthenticatedUsersWithNoRoles() throws Exception {
        String bearer = token(SECRET, Instant.now().plusSeconds(3600), List.of());

        mockMvc.perform(get("/v1/orders").header("Authorization", "Bearer " + bearer))
                .andExpect(status().isOk())
                .andExpect(content().string("alice:"));
    }

    @Test
    void rejectsInvalidBearerTokensThroughTheFilterChain() throws Exception {
        String wrongSignature = token("different-test-only-secret-at-least-32-bytes",
                Instant.now().plusSeconds(3600));

        mockMvc.perform(get("/v1/orders").header("Authorization", "Bearer " + wrongSignature))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"access_token", "JSESSIONID"})
    void rejectsCookieOnlyAuthenticationOnProtectedWrites(String cookieName) throws Exception {
        String signedToken = token(SECRET, Instant.now().plusSeconds(3600));

        mockMvc.perform(post("/v1/orders").cookie(new Cookie(cookieName, signedToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertNull(result.getHandler()));
    }

    @Test
    void ignoresAuthenticatedSecurityContextSavedInHttpSessionOnProtectedWrites() throws Exception {
        var savedContext = SecurityContextHolder.createEmptyContext();
        savedContext.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "alice", "unused", List.of(new SimpleGrantedAuthority("ROLE_TRADER"))));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, savedContext);

        mockMvc.perform(post("/v1/orders").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertNull(result.getHandler()));
    }

    @Test
    void permitsBearerAuthenticatedWritesWithoutCsrfTokenOrCreatingSession() throws Exception {
        String signedToken = token(SECRET, Instant.now().plusSeconds(3600));

        mockMvc.perform(post("/v1/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + signedToken))
                .andExpect(status().isOk())
                .andExpect(content().string("alice:ROLE_TRADER"))
                .andExpect(result -> assertNull(result.getRequest().getSession(false)))
                .andExpect(result -> assertTrue(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).isEmpty()));

        mockMvc.perform(post("/v1/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertNull(result.getHandler()));
    }

    @Test
    void doesNotSaveBearerAuthenticationInExistingSessionOrReuseItForNextWrite() throws Exception {
        String signedToken = token(SECRET, Instant.now().plusSeconds(3600));
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/v1/orders")
                        .session(session)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + signedToken))
                .andExpect(status().isOk())
                .andExpect(content().string("alice:ROLE_TRADER"))
                .andExpect(result -> assertNull(session.getAttribute(
                        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)))
                .andExpect(result -> assertTrue(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).isEmpty()));

        mockMvc.perform(post("/v1/orders").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertNull(result.getHandler()));
    }

    @Test
    void rejectsBearerTokensInQueryOrFormParametersOnProtectedWrites() throws Exception {
        String signedToken = token(SECRET, Instant.now().plusSeconds(3600));

        mockMvc.perform(post("/v1/orders").queryParam("access_token", signedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertNull(result.getHandler()));

        mockMvc.perform(post("/v1/orders")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("access_token", signedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertNull(result.getHandler()));
    }

    @Test
    void rejectsHttpBasicAuthenticationOnProtectedWrites() throws Exception {
        String basicCredentials = Base64.getEncoder().encodeToString("alice:password".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(post("/v1/orders").header(HttpHeaders.AUTHORIZATION, "Basic " + basicCredentials))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertNull(result.getHandler()));
    }

    @Test
    void rejectsMalformedExpiredAndWronglySignedBearerTokensOnProtectedWrites() throws Exception {
        String expired = token(SECRET, Instant.now().minusSeconds(3600));
        String wrongSignature = token("different-test-only-secret-at-least-32-bytes",
                Instant.now().plusSeconds(3600));

        for (String invalidToken : List.of("not-a-jwt", expired, wrongSignature)) {
            mockMvc.perform(post("/v1/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + invalidToken))
                    .andExpect(status().isUnauthorized())
                    .andExpect(result -> assertNull(result.getHandler()));
        }
    }

    @TestConfiguration
    @EnableWebSecurity
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class TestApplication {
        @Bean
        TestEndpoints testEndpoints() {
            return new TestEndpoints();
        }
    }

    @RestController
    static class TestEndpoints {
        @GetMapping({"/public", "/swagger-ui/index.html", "/v3/api-docs"})
        String publicEndpoint() {
            return "public";
        }

        @PostMapping({"/v1/accounts", "/v1/accounts/login", "/v1/accounts/login/verify", "/v1/accounts/register/verify"})
        String authenticationEndpoint() {
            return "public";
        }

        @GetMapping({"/v1/orders", "/v1/accounts"})
        String protectedEndpoint(Authentication authentication) {
            String roles = authentication.getAuthorities().stream()
                    .map(authority -> authority.getAuthority())
                    .sorted()
                    .collect(java.util.stream.Collectors.joining(","));
            return authentication.getName() + ":" + roles;
        }

        @PostMapping("/v1/orders")
        String protectedWriteEndpoint(Authentication authentication) {
            return protectedEndpoint(authentication);
        }
    }
}
