package com.neueda.leap.exceptions;

import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import com.neueda.leap.dtos.ErrorResponse;
import com.neueda.leap.exceptions.*;
import com.neueda.leap.exceptions.DuplicateAccountException;
import com.neueda.leap.security.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(OrderException.class)
    ResponseEntity<ErrorResponse> handleOrderException(OrderException exception, HttpServletRequest request){
        logger.error("OrderException occurred at {}: {}", request.getRequestURI(), exception.getMessage(), exception);
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                exception.getMessage(),
                LocalDateTime.now(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(DuplicateAccountException.class)
    ResponseEntity<ErrorResponse> handleDuplicateAccount(DuplicateAccountException exception, HttpServletRequest request){
        logger.error("DuplicateAccountException occurred at {}: {}", request.getRequestURI(), exception.getMessage(), exception);
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                exception.getMessage(),
                LocalDateTime.now(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    @ExceptionHandler(AccountNotFoundException.class)
    ResponseEntity<ErrorResponse> handleAccountNotFound(AccountNotFoundException exception, HttpServletRequest request){
        logger.error("AccountNotFoundException occurred at {}: {}", request.getRequestURI(), exception.getMessage(), exception);
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                exception.getMessage(),
                LocalDateTime.now(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(InstrumentNotFoundException.class)
    ResponseEntity<ErrorResponse> handleInstrumentNotFound(InstrumentNotFoundException exception, HttpServletRequest request){
        logger.error("InstrumentNotFoundException occurred at {}: {}", request.getRequestURI(), exception.getMessage(), exception);
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                exception.getMessage(),
                LocalDateTime.now(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    ResponseEntity<ErrorResponse> handleInsufficientFunds(InsufficientFundsException exception, HttpServletRequest request){
        logger.error("InsufficientFundsException occurred at {}: {}", request.getRequestURI(), exception.getMessage(), exception);
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                exception.getMessage(),
                LocalDateTime.now(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(InsufficientHoldingsException.class)
    ResponseEntity<ErrorResponse> handleInsufficientHoldings(InsufficientHoldingsException exception, HttpServletRequest request){
        logger.error("InsufficientHoldingsException occurred at {}: {}", request.getRequestURI(), exception.getMessage(), exception);
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                exception.getMessage(),
                LocalDateTime.now(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(DuplicateOrderException.class)
    ResponseEntity<ErrorResponse> handleDuplicateOrder(DuplicateOrderException exception, HttpServletRequest request){
        logger.error("DuplicateOrderException occurred at {}: {}", request.getRequestURI(), exception.getMessage(), exception);
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                exception.getMessage(),
                LocalDateTime.now(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    @ExceptionHandler(AccountNotActiveException.class)
    ResponseEntity<ErrorResponse> handleAccountNotActive(AccountNotActiveException exception, HttpServletRequest request){
        logger.error("AccountNotActiveException occurred at {}: {}", request.getRequestURI(), exception.getMessage(), exception);
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                exception.getMessage(),
                LocalDateTime.now(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(InvalidSymbolException.class)
    ResponseEntity<ErrorResponse> handleInvalidSymbol(InvalidSymbolException exception, HttpServletRequest request){
        logger.error("InvalidSymbolException occurred at {}: {}", request.getRequestURI(), exception.getMessage(), exception);
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                exception.getMessage(),
                LocalDateTime.now(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(InvalidPriceException.class)
    ResponseEntity<ErrorResponse> handleInvalidPrice(InvalidPriceException exception, HttpServletRequest request){
        logger.error("InvalidPriceException occurred at {}: {}", request.getRequestURI(), exception.getMessage(), exception);
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                exception.getMessage(),
                LocalDateTime.now(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(InvalidSymbolPriceException.class)
    ResponseEntity<ErrorResponse> handleInvalidSymbolPrice(InvalidSymbolPriceException exception, HttpServletRequest request){
        logger.error("InvalidSymbolPriceException occurred at {}: {}", request.getRequestURI(), exception.getMessage(), exception);
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                exception.getMessage(),
                LocalDateTime.now(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(AuthorizationException.class)
    ResponseEntity<ErrorResponse> handleAuthorizationException(AuthorizationException exception, HttpServletRequest request){
        logger.error("AuthorizationException occurred at {}: {}", request.getRequestURI(), exception.getMessage(), exception);
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                exception.getMessage(),
                LocalDateTime.now(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException exception, HttpServletRequest request){
        logger.error("AccessDeniedException occurred at {}: {}", request.getRequestURI(), exception.getMessage(), exception);
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "Forbidden: " + exception.getMessage(),
                LocalDateTime.now(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
        }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException exception, HttpServletRequest request){
        // Field names and constraint messages only, so rejected input values never reach the logs
        String details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .sorted()
                .collect(java.util.stream.Collectors.joining("; "));
        logger.warn("Validation failed at {}: {}", request.getRequestURI(), details);
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Validation failed: " + details,
                LocalDateTime.now(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception, HttpServletRequest request){
                logger.warn("Malformed request body at {}", request.getRequestURI());
                ErrorResponse errorResponse = new ErrorResponse(
                                HttpStatus.BAD_REQUEST.value(),
                                "Malformed request body",
                                LocalDateTime.now(),
                                request.getRequestURI()
                );
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleStatusException(org.springframework.web.server.ResponseStatusException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.getStatusCode()).body(new ErrorResponse(exception.getStatusCode().value(),
            exception.getReason(), LocalDateTime.now(), request.getRequestURI()));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleOrderConflict(org.springframework.dao.DataIntegrityViolationException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(409,
            "This request conflicts with existing data. Refresh and retry.", LocalDateTime.now(), request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception exception, HttpServletRequest request){
    logger.error("Unexpected exception occurred at {}: {}", request.getRequestURI(), exception.getMessage(), exception);
    ErrorResponse errorResponse = new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Internal server error: " + exception.getMessage(),
            LocalDateTime.now(),
            request.getRequestURI()
    );
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
}
    
}
