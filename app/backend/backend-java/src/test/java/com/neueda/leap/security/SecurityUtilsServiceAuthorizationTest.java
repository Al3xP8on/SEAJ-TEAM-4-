package com.neueda.leap.security;

import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.models.Account;
import com.neueda.leap.models.Order;
import com.neueda.leap.repositories.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityUtilsService Authorization Tests")
public class SecurityUtilsServiceAuthorizationTest {

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @Mock
    private Jwt jwt;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private SecurityUtilsService securityUtilsService;

    private Account aliceAccount;
    private Account brianAccount;
    private Order aliceOrder;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);

        // Create test accounts
        aliceAccount = new Account("ACC-0001", "alice", "SecurePass123!", "Alice Johnson",
                "alice@example.com", null, new BigDecimal("10000.00"), AccountStatus.ACTIVE);

        brianAccount = new Account("ACC-0002", "brian", "SecurePass123!", "Brian Smith",
                "brian@example.com", null, new BigDecimal("5000.00"), AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should get authenticated username from JWT")
    void testGetAuthenticatedUsernameFromJwt() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaimAsString("username")).thenReturn("alice");

        String username = securityUtilsService.getAuthenticatedUsername();

        assertEquals("alice", username);
    }

    @Test
    @DisplayName("Should validate account ownership - success")
    void testValidateAccountOwnershipSuccess() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaimAsString("username")).thenReturn("alice");
        when(accountRepository.findByUsername("alice")).thenReturn(Optional.of(aliceAccount));

        // Should not throw exception
        assertDoesNotThrow(() -> securityUtilsService.validateAccountOwnership("ACC-0001"));
    }

    @Test
    @DisplayName("Should throw exception when accessing different account")
    void testValidateAccountOwnershipThrowsForDifferentAccount() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaimAsString("username")).thenReturn("alice");
        when(accountRepository.findByUsername("alice")).thenReturn(Optional.of(aliceAccount));

        // Should throw exception when trying to access Brian's account
        assertThrows(AuthorizationException.class, 
            () -> securityUtilsService.validateAccountOwnership("ACC-0002"));
    }

    @Test
    @DisplayName("Should throw exception when user account not found")
    void testValidateAccountOwnershipThrowsWhenUserNotFound() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaimAsString("username")).thenReturn("unknown");
        when(accountRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThrows(AuthorizationException.class,
            () -> securityUtilsService.validateAccountOwnership("ACC-0001"));
    }

    @Test
    @DisplayName("Should throw exception when not authenticated")
    void testValidateAccountOwnershipThrowsWhenNotAuthenticated() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(false);

        assertThrows(AuthorizationException.class,
            () -> securityUtilsService.validateAccountOwnership("ACC-0001"));
    }
}
