package com.neueda.leap.security;

import com.neueda.leap.models.Account;
import com.neueda.leap.repositories.AccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Service to handle security context operations and user authorization checks.
 * 
 * This service is responsible for:
 * 1. Extracting the authenticated user from SecurityContext
 * 2. Looking up the account associated with the authenticated user
 * 3. Validating that a user has authorization to perform operations on a specific account
 */
@Service
public class SecurityUtilsService {
    
    private static final Logger logger = LoggerFactory.getLogger(SecurityUtilsService.class);
    
    @Autowired
    private AccountRepository accountRepository;
    
    /**
     * Gets the username of the currently authenticated user.
     * 
     * @return The username of the authenticated user, or null if not authenticated
     */
    public String getAuthenticatedUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            // For JWT authentication, extract the username claim
            if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt) {
                org.springframework.security.oauth2.jwt.Jwt jwt = (org.springframework.security.oauth2.jwt.Jwt) authentication.getPrincipal();
                String username = jwt.getClaimAsString("username");
                if (username != null) {
                    return username;
                }
            }
            // Fallback to authentication name
            return authentication.getName();
        }
        return null;
    }
    
    /**
     * Gets the Account object for the currently authenticated user.
     * 
     * @return Optional containing the user's Account if found
     * @throws AuthorizationException if user is not authenticated or account not found
     */
    public Account getAuthenticatedUserAccount() {
        String username = getAuthenticatedUsername();
        if (username == null) {
            logger.error("Attempted to get authenticated user account, but no user is authenticated");
            throw new AuthorizationException("User is not authenticated");
        }
        
        Optional<Account> account = accountRepository.findByUsername(username);
        if (account.isEmpty()) {
            logger.error("Authenticated user '{}' does not have an associated account", username);
            throw new AuthorizationException("User account not found");
        }
        
        return account.get();
    }
    
    /**
     * Validates that the authenticated user owns the specified account.
     * 
     * @param targetAccountId The account ID to check ownership for
     * @throws AuthorizationException if the user does not own this account
     */
    public void validateAccountOwnership(String targetAccountId) {
        Account userAccount = getAuthenticatedUserAccount();
        
        if (!userAccount.getAccountId().equals(targetAccountId)) {
            logger.warn("User '{}' (account: {}) attempted unauthorized access to account: {}",
                    userAccount.getUsername(), userAccount.getAccountId(), targetAccountId);
            throw new AuthorizationException(
                    String.format("User does not have permission to access account: %s", targetAccountId)
            );
        }
    }
    
    /**
     * Validates that the authenticated user owns the account associated with the given order ID.
     * 
     * @param order The order object to check ownership for
     * @throws AuthorizationException if the user does not own this order's account
     */
    public void validateOrderOwnership(com.neueda.leap.models.Order order) {
        if (order == null) {
            throw new AuthorizationException("Order not found");
        }
        
        Account userAccount = getAuthenticatedUserAccount();
        String orderAccountId = order.getAccount().getAccountId();
        
        if (!userAccount.getAccountId().equals(orderAccountId)) {
            logger.warn("User '{}' (account: {}) attempted unauthorized access to order: {} (account: {})",
                    userAccount.getUsername(), userAccount.getAccountId(), order.getId(), orderAccountId);
            throw new AuthorizationException(
                    String.format("User does not have permission to access this order")
            );
        }
    }
    
    /**
     * Checks if the authenticated user is an admin.
     * 
     * @return true if user has ROLE_ADMIN, false otherwise
     */
    public boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getAuthorities().stream()
                    .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
        }
        return false;
    }
}
