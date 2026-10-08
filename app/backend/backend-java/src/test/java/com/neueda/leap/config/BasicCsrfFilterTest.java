package com.neueda.leap.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.mockito.Mockito.*;

class BasicCsrfFilterTest {
    private final BasicCsrfFilter filter = new BasicCsrfFilter();
    private final FilterChain chain = mock(FilterChain.class);
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private MockHttpServletRequest request(String method, String path, String origin) {
        ReflectionTestUtils.setField(filter, "csrfEnabled", true);
        ReflectionTestUtils.setField(filter, "allowedOriginsList", "https://example.com, https://trusted.com ");
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        if (origin != null) request.addHeader("Origin", origin);
        return request;
    }
    @ParameterizedTest @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH"})
    void blocksUntrustedOriginsOnMutatingRequests(String method) throws Exception {
        filter.doFilter(request(method, "/v1/orders", "https://evil.com"), response, chain);
        assertEquals(403, response.getStatus());
        assertEquals("application/json", response.getContentType());
        assertEquals("{\"status\":403,\"message\":\"Origin not allowed\"}", response.getContentAsString());
        verifyNoInteractions(chain);
    }
    @ParameterizedTest @ValueSource(strings = {"https://example.com", "HTTPS://TRUSTED.COM"})
    void permitsConfiguredOriginsIncludingTrimmedCaseInsensitiveEntries(String origin) throws Exception {
        MockHttpServletRequest request = request("POST", "/v1/orders", origin);
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }
    @ParameterizedTest @ValueSource(strings = {"GET", "HEAD", "OPTIONS"})
    void permitsReadOnlyRequests(String method) throws Exception {
        MockHttpServletRequest request = request(method, "/v1/orders", "https://evil.com");
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }
    @ParameterizedTest @ValueSource(strings = {"/api/v1/auth/login", "/actuator/health", "/public/info"})
    void skipsExcludedPaths(String path) throws Exception {
        MockHttpServletRequest request = request("POST", path, "https://evil.com");
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }
    @Test void permitsRequestsWithoutOriginAndWhenDisabled() throws Exception {
        MockHttpServletRequest request = request("POST", "/v1/orders", null);
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        MockHttpServletRequest disabledRequest = request("POST", "/v1/orders", "https://evil.com");
        ReflectionTestUtils.setField(filter, "csrfEnabled", false);
        filter.doFilter(disabledRequest, response, chain);
        verify(chain).doFilter(disabledRequest, response);
    }
}
