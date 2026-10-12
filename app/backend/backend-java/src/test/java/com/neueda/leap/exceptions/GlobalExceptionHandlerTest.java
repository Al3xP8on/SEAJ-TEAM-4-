package com.neueda.leap.exceptions;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.neueda.leap.dtos.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import java.time.LocalDateTime;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/orders");
    private void assertError(ResponseEntity<ErrorResponse> response, int status, String message, LocalDateTime before) {
        assertEquals(status, response.getStatusCode().value());
        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(status, body.status());
        assertEquals(message, body.message());
        assertEquals("/v1/orders", body.path());
        assertFalse(body.timestamp().isBefore(before));
        assertFalse(body.timestamp().isAfter(LocalDateTime.now()));
    }
    @Test void handleOrderExceptionPreservesMessageAndRequestPath() {
        LocalDateTime before = LocalDateTime.now();
        assertError(handler.handleOrderException(new OrderException("failure"), request), 400, "failure", before);
    }
    @Test void handleDuplicateAccountPreservesMessageAndRequestPath() {
        LocalDateTime before = LocalDateTime.now();
        assertError(handler.handleDuplicateAccount(new DuplicateAccountException("failure"), request), 409, "failure", before);
    }
    @Test void handleAccountNotFoundPreservesMessageAndRequestPath() {
        LocalDateTime before = LocalDateTime.now();
        assertError(handler.handleAccountNotFound(new AccountNotFoundException("failure"), request), 404, "failure", before);
    }
    @Test void handleInstrumentNotFoundPreservesMessageAndRequestPath() {
        LocalDateTime before = LocalDateTime.now();
        assertError(handler.handleInstrumentNotFound(new InstrumentNotFoundException("failure"), request), 404, "failure", before);
    }
    @Test void handleInsufficientFundsPreservesMessageAndRequestPath() {
        LocalDateTime before = LocalDateTime.now();
        assertError(handler.handleInsufficientFunds(new InsufficientFundsException("failure"), request), 400, "failure", before);
    }
    @Test void handleInsufficientHoldingsPreservesMessageAndRequestPath() {
        LocalDateTime before = LocalDateTime.now();
        assertError(handler.handleInsufficientHoldings(new InsufficientHoldingsException("failure"), request), 400, "failure", before);
    }
    @Test void handleDuplicateOrderPreservesMessageAndRequestPath() {
        LocalDateTime before = LocalDateTime.now();
        assertError(handler.handleDuplicateOrder(new DuplicateOrderException("failure"), request), 409, "failure", before);
    }
    @Test void handleAccountNotActivePreservesMessageAndRequestPath() {
        LocalDateTime before = LocalDateTime.now();
        assertError(handler.handleAccountNotActive(new AccountNotActiveException("failure"), request), 400, "failure", before);
    }
    @Test void handleInvalidSymbolPreservesMessageAndRequestPath() {
        LocalDateTime before = LocalDateTime.now();
        assertError(handler.handleInvalidSymbol(new InvalidSymbolException("failure"), request), 400, "failure", before);
    }
    @Test void handleInvalidPricePreservesMessageAndRequestPath() {
        LocalDateTime before = LocalDateTime.now();
        assertError(handler.handleInvalidPrice(new InvalidPriceException("failure"), request), 400, "failure", before);
    }
    @Test void handleInvalidSymbolPricePreservesMessageAndRequestPath() {
        LocalDateTime before = LocalDateTime.now();
        assertError(handler.handleInvalidSymbolPrice(new InvalidSymbolPriceException("failure"), request), 400, "failure", before);
    }
    @Test void unexpectedExceptionReturnsServerErrorWithContext() {
        LocalDateTime before = LocalDateTime.now();
        assertError(handler.handleGenericException(new IllegalStateException("offline"), request), 500, "Internal server error: offline", before);
    }
}
