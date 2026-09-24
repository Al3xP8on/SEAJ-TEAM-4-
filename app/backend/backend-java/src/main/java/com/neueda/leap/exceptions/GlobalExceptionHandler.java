package com.neueda.leap.exceptions;

import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import com.neueda.leap.dtos.ErrorResponse;
import com.neueda.leap.exceptions.*;
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
    
}
