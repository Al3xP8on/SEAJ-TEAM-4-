package com.neueda.leap.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT");

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        
        logger.error("Unexpected error occurred", ex);
        auditLogger.error("INTERNAL_ERROR | Exception: {} | Message: {} | StackTrace: ", 
            ex.getClass().getSimpleName(), ex.getMessage(), ex);
        
        Map<String, String> response = new HashMap<>();
        response.put("status", String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()));
        response.put("message", "Internal error");
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}