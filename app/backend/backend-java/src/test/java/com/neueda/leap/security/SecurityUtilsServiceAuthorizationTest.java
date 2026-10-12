package com.neueda.leap.security;

import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.models.Account;
import com.neueda.leap.models.Order;
import com.neueda.leap.repositories.AccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);

        // Create test accounts
        aliceAccount = new Account("ACC-0001", "alice", "SecurePass123!", "Alice Johnson",
                "alice@example.com", null, new BigDecimal("10000.00"), AccountStatus.ACTIVE);

        brianAccount = new Account("ACC-0002", "brian", "SecurePass123!", "Brian Smith",
                "brian@example.com", null, new BigDecimal("5000.00"), AccountStatus.ACTIVE);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
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
    @DisplayName("Should return no username when authentication is missing")
    void testGetAuthenticatedUsernameWithoutAuthentication() {
        assertNull(securityUtilsService.getAuthenticatedUsername());
        verifyNoInteractions(accountRepository);
    }

    @Test
    @DisplayName("Should return no username for an unauthenticated principal")
    void testGetAuthenticatedUsernameWhenUnauthenticated() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(false);

        assertNull(securityUtilsService.getAuthenticatedUsername());
        verifyNoInteractions(accountRepository);
    }

    @Test
    @DisplayName("Should use authentication name when the JWT has no username claim")
    void testGetAuthenticatedUsernameFromJwtNameFallback() {
        authenticateWithJwt(null);
        when(authentication.getName()).thenReturn("alice");
        when(accountRepository.findByUsername("alice")).thenReturn(Optional.of(aliceAccount));

        assertEquals("alice", securityUtilsService.getAuthenticatedUsername());
        assertSame(aliceAccount, securityUtilsService.getAuthenticatedUserAccount());
        verify(accountRepository).findByUsername("alice");
    }

    @Test
    @DisplayName("Should resolve an account for a non-JWT authentication")
    void testGetAuthenticatedUsernameFromNonJwtPrincipal() {
        Authentication principal = new UsernamePasswordAuthenticationToken(
                "alice", "unused", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(securityContext.getAuthentication()).thenReturn(principal);
        when(accountRepository.findByUsername("alice")).thenReturn(Optional.of(aliceAccount));

        assertEquals("alice", securityUtilsService.getAuthenticatedUsername());
        assertSame(aliceAccount, securityUtilsService.getAuthenticatedUserAccount());
        verify(accountRepository).findByUsername("alice");
    }

    @Test
    @DisplayName("Should return the account belonging to the JWT username")
    void testGetAuthenticatedUserAccount() {
        authenticateWithJwt("alice");
        when(accountRepository.findByUsername("alice")).thenReturn(Optional.of(aliceAccount));

        assertSame(aliceAccount, securityUtilsService.getAuthenticatedUserAccount());
        verify(accountRepository).findByUsername("alice");
    }

    @Test
    @DisplayName("Should reject account lookup without authentication before querying the database")
    void testGetAuthenticatedUserAccountWithoutAuthentication() {
        AuthorizationException exception = assertThrows(AuthorizationException.class,
                () -> securityUtilsService.getAuthenticatedUserAccount());

        assertEquals("User is not authenticated", exception.getMessage());
        verifyNoInteractions(accountRepository);
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
        verify(accountRepository).findByUsername("alice");
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
        AuthorizationException exception = assertThrows(AuthorizationException.class,
            () -> securityUtilsService.validateAccountOwnership("ACC-0002"));
        assertEquals("User does not have permission to access account: ACC-0002", exception.getMessage());
        verify(accountRepository).findByUsername("alice");
    }

    @Test
    @DisplayName("Should throw exception when user account not found")
    void testValidateAccountOwnershipThrowsWhenUserNotFound() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaimAsString("username")).thenReturn("unknown");
        when(accountRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        AuthorizationException exception = assertThrows(AuthorizationException.class,
            () -> securityUtilsService.validateAccountOwnership("ACC-0001"));
        assertEquals("User account not found", exception.getMessage());
        verify(accountRepository).findByUsername("unknown");
    }

    @Test
    @DisplayName("Should throw exception when not authenticated")
    void testValidateAccountOwnershipThrowsWhenNotAuthenticated() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(false);

        AuthorizationException exception = assertThrows(AuthorizationException.class,
            () -> securityUtilsService.validateAccountOwnership("ACC-0001"));
        assertEquals("User is not authenticated", exception.getMessage());
        verifyNoInteractions(accountRepository);
    }

    @Test
    @DisplayName("Should allow an authenticated user to access their own order")
    void testValidateOrderOwnershipSuccess() {
        authenticateWithJwt("alice");
        when(accountRepository.findByUsername("alice")).thenReturn(Optional.of(aliceAccount));
        Order order = orderFor(aliceAccount);

        assertDoesNotThrow(() -> securityUtilsService.validateOrderOwnership(order));
        verify(accountRepository).findByUsername("alice");
        verify(order).getAccount();
    }

    @Test
    @DisplayName("Should reject an order that belongs to another account")
    void testValidateOrderOwnershipForDifferentAccount() {
        authenticateWithJwt("alice");
        when(accountRepository.findByUsername("alice")).thenReturn(Optional.of(aliceAccount));
        Order order = orderFor(brianAccount);

        AuthorizationException exception = assertThrows(AuthorizationException.class,
                () -> securityUtilsService.validateOrderOwnership(order));

        assertEquals("User does not have permission to access this order", exception.getMessage());
        verify(accountRepository).findByUsername("alice");
    }

    @Test
    @DisplayName("Should reject a missing order without querying the database")
    void testValidateOrderOwnershipWhenOrderIsMissing() {
        AuthorizationException exception = assertThrows(AuthorizationException.class,
                () -> securityUtilsService.validateOrderOwnership(null));

        assertEquals("Order not found", exception.getMessage());
        verifyNoInteractions(accountRepository);
    }

    @Test
    @DisplayName("Should reject order access without authentication before inspecting ownership")
    void testValidateOrderOwnershipWithoutAuthentication() {
        Order order = mock(Order.class);

        AuthorizationException exception = assertThrows(AuthorizationException.class,
                () -> securityUtilsService.validateOrderOwnership(order));

        assertEquals("User is not authenticated", exception.getMessage());
        verifyNoInteractions(accountRepository, order);
    }

    @Test
    @DisplayName("Should grant admin status only to an authenticated ROLE_ADMIN principal")
    void testIsAdminWithAdminAuthority() {
        Authentication principal = new UsernamePasswordAuthenticationToken("alice", "unused",
                List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(securityContext.getAuthentication()).thenReturn(principal);

        assertTrue(securityUtilsService.isAdmin());
        verifyNoInteractions(accountRepository);
    }

    @Test
    @DisplayName("Should deny admin status when the user has only ordinary authorities")
    void testIsAdminWithoutAdminAuthority() {
        Authentication principal = new UsernamePasswordAuthenticationToken("alice", "unused",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(securityContext.getAuthentication()).thenReturn(principal);

        assertFalse(securityUtilsService.isAdmin());
        verifyNoInteractions(accountRepository);
    }

    @Test
    @DisplayName("Should require the full ROLE_ADMIN authority rather than ADMIN")
    void testIsAdminWithUnprefixedAuthority() {
        Authentication principal = new UsernamePasswordAuthenticationToken("alice", "unused",
                List.of(new SimpleGrantedAuthority("ADMIN")));
        when(securityContext.getAuthentication()).thenReturn(principal);

        assertFalse(securityUtilsService.isAdmin());
    }

    @Test
    @DisplayName("Should deny admin status for an authenticated user with no authorities")
    void testIsAdminWithoutAuthorities() {
        Authentication principal = new UsernamePasswordAuthenticationToken("alice", "unused", List.of());
        when(securityContext.getAuthentication()).thenReturn(principal);

        assertFalse(securityUtilsService.isAdmin());
    }

    @Test
    @DisplayName("Should deny admin status when authentication is missing")
    void testIsAdminWithoutAuthentication() {
        assertFalse(securityUtilsService.isAdmin());
        verifyNoInteractions(accountRepository);
    }

    @Test
    @DisplayName("Should deny admin status when the ROLE_ADMIN principal is not authenticated")
    void testIsAdminWhenUnauthenticated() {
        UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken(
                "alice", "unused", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        principal.setAuthenticated(false);
        when(securityContext.getAuthentication()).thenReturn(principal);

        assertFalse(securityUtilsService.isAdmin());
        verifyNoInteractions(accountRepository);
    }

    private void authenticateWithJwt(String username) {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaimAsString("username")).thenReturn(username);
    }

    private Order orderFor(Account account) {
        Order order = mock(Order.class);
        when(order.getAccount()).thenReturn(account);
        return order;
    }
}
