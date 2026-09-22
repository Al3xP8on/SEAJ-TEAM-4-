package main.java.com.neueda.leap.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class BasicCsrfFilter extends OncePerRequestFilter {
    
    private static final Logger logger = LoggerFactory.getLogger(BasicCsrfFilter.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT");
    
    private static final Set<String> PROTECTED_METHODS = new HashSet<>(
        Arrays.asList("POST", "PUT", "DELETE", "PATCH")
    );
    
    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:8080,http://127.0.0.1:3000,http://127.0.0.1:8080}")
    private String allowedOriginsList;
    
    @Value("${app.csrf.enabled:true}")
    private boolean csrfEnabled;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if (!csrfEnabled) {
            filterChain.doFilter(request, response);
            return;
        }
        
        String method = request.getMethod();
        
        if (PROTECTED_METHODS.contains(method)) {
            String origin = request.getHeader("Origin");
            
            // Validate origin header if present
            if (origin != null && !isOriginAllowed(origin)) {
                //Log CSRF attempt
                auditLogger.warn("CSRF_ATTEMPT_BLOCKED | Origin: {} | Method: {} | Path: {} | RemoteAddr: {}",
                    origin, method, request.getRequestURI(), request.getRemoteAddr());
                
                logger.warn("CSRF protection: Origin not allowed. Origin: {}, Method: {}, Path: {}",
                    origin, method, request.getRequestURI());
                
                // Return 403 Forbidden
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"status\":403,\"message\":\"Origin not allowed\"}");
                return;
            }
        }
        
        filterChain.doFilter(request, response);
    }

    private boolean isOriginAllowed(String origin) {
        Set<String> allowedOrigins = new HashSet<>(
            Arrays.asList(allowedOriginsList.split(","))
        );
        
        return allowedOrigins.stream()
            .map(String::trim)
            .anyMatch(allowed -> origin.equalsIgnoreCase(allowed));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        // Skip CSRF check for certain paths
        String path = request.getRequestURI();
        

        return path.startsWith("/api/v1/auth/") || 
               path.startsWith("/actuator/") ||
               path.startsWith("/public/");
    }
}