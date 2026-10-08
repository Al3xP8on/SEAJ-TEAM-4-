package com.neueda.leap.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import jakarta.servlet.FilterChain;
import static org.mockito.Mockito.*;

class SecurityHeadersFilterTest {
    @Test void setsSecurityHeadersBeforeContinuingChain() throws Exception {
        var request = new MockHttpServletRequest("GET", "/v1/accounts");
        var response = new MockHttpServletResponse();
        response.setHeader("Server", "internal-server");
        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
            assertEquals("DENY", response.getHeader("X-Frame-Options"));
            assertEquals("1; mode=block", response.getHeader("X-XSS-Protection"));
            assertEquals("default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'", response.getHeader("Content-Security-Policy"));
            assertEquals("no-referrer", response.getHeader("Referrer-Policy"));
            assertEquals("", response.getHeader("Server"));
            assertEquals("max-age=300; includeSubDomains", response.getHeader("Strict-Transport-Security"));
            assertEquals("geolocation=(), microphone=(), camera=()", response.getHeader("Permissions-Policy"));
            return null;
        }).when(chain).doFilter(request, response);
        new SecurityHeadersFilter().doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }
}
