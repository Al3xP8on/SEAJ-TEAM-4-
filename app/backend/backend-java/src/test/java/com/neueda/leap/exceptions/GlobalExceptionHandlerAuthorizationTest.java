package com.neueda.leap.exceptions;

import com.neueda.leap.dtos.ErrorResponse;
import com.neueda.leap.security.AuthorizationException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler Authorization Tests")
public class GlobalExceptionHandlerAuthorizationTest {

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        when(request.getRequestURI()).thenReturn("/api/orders/create");
    }

    @Test
    @DisplayName("Should handle AuthorizationException with 403 status")
    void testHandleAuthorizationExceptionReturns403() {
        AuthorizationException exception = new AuthorizationException("User does not have permission");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAuthorizationException(exception, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(403, response.getBody().status());
        assertEquals("User does not have permission", response.getBody().message());
    }

    @Test
    @DisplayName("Should include request URI in error response")
    void testErrorResponseIncludesRequestUri() {
        AuthorizationException exception = new AuthorizationException("Access denied");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAuthorizationException(exception, request);

        assertEquals("/api/orders/create", response.getBody().path());
    }

    @Test
    @DisplayName("Should include timestamp in error response")
    void testErrorResponseIncludesTimestamp() {
        AuthorizationException exception = new AuthorizationException("Access denied");
        LocalDateTime beforeCall = LocalDateTime.now();

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAuthorizationException(exception, request);

        assertNotNull(response.getBody().timestamp());
        assertTrue(response.getBody().timestamp().isAfter(beforeCall.minusSeconds(1)));
    }

    @Test
    @DisplayName("Should preserve exception message in response")
    void testErrorMessagePreserved() {
        String message = "User does not have permission to access account: ACC-0002";
        AuthorizationException exception = new AuthorizationException(message);

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAuthorizationException(exception, request);

        assertEquals(message, response.getBody().message());
    }

    @Test
    @DisplayName("Should always return 403 Forbidden status")
    void testAlwaysReturns403() {
        AuthorizationException exception1 = new AuthorizationException("Error 1");
        AuthorizationException exception2 = new AuthorizationException("Error 2");
        AuthorizationException exception3 = new AuthorizationException("Error 3");

        ResponseEntity<ErrorResponse> response1 = exceptionHandler.handleAuthorizationException(exception1, request);
        ResponseEntity<ErrorResponse> response2 = exceptionHandler.handleAuthorizationException(exception2, request);
        ResponseEntity<ErrorResponse> response3 = exceptionHandler.handleAuthorizationException(exception3, request);

        assertEquals(HttpStatus.FORBIDDEN, response1.getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, response2.getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, response3.getStatusCode());
        assertEquals(403, response1.getBody().status());
        assertEquals(403, response2.getBody().status());
        assertEquals(403, response3.getBody().status());
    }

    @Test
    @DisplayName("Should handle exception with cause")
    void testHandleExceptionWithCause() {
        Throwable cause = new RuntimeException("Underlying cause");
        AuthorizationException exception = new AuthorizationException("Authorization failed", cause);

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAuthorizationException(exception, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Authorization failed", response.getBody().message());
    }

    @Test
    @DisplayName("Should handle empty error message")
    void testHandleEmptyErrorMessage() {
        AuthorizationException exception = new AuthorizationException("");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAuthorizationException(exception, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("", response.getBody().message());
    }

    @Test
    @DisplayName("Should handle null message gracefully")
    void testHandleNullMessage() {
        // Create exception with null message
        AuthorizationException exception = new AuthorizationException(null);

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAuthorizationException(exception, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        // Exception will use null or some default
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("Should not expose sensitive information in error response")
    void testNoSensitiveInfoExposed() {
        AuthorizationException exception = new AuthorizationException(
            "User does not have permission to access account: ACC-0002");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAuthorizationException(exception, request);

        ErrorResponse body = response.getBody();
        // Should not expose stack trace
        assertFalse(body.message().contains("at "));
        assertFalse(body.message().contains("java."));
        // Should not expose passwords or tokens
        assertFalse(body.message().contains("password"));
        assertFalse(body.message().contains("token"));
    }
}
