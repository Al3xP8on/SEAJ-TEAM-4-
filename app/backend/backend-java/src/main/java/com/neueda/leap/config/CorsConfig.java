package main.java.com.neueda.leap.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.util.Arrays;
import java.util.List;


@Configuration
public class CorsConfig implements WebMvcConfigurer {
    
    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:8080,http://127.0.0.1:3000,http://127.0.0.1:8080}")
    private String allowedOrigins;
    
    @Value("${app.cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS}")
    private String allowedMethods;
    
    @Value("${app.cors.allowed-headers:Content-Type,Accept}")
    private String allowedHeaders;
    
    @Value("${app.cors.max-age:3600}")
    private Long maxAge;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .toArray(String[]::new);
        
        String[] methods = Arrays.stream(allowedMethods.split(","))
            .map(String::trim)
            .toArray(String[]::new);
        
        String[] headers = Arrays.stream(allowedHeaders.split(","))
            .map(String::trim)
            .toArray(String[]::new);

        registry.addMapping("/api/**")
            .allowedOrigins(origins)
            .allowedMethods(methods)
            .allowedHeaders(headers)
            .exposedHeaders("Content-Type", "Authorization")
            // false for development, enable for production
            .allowCredentials(false)
            .maxAge(maxAge);
    }
}