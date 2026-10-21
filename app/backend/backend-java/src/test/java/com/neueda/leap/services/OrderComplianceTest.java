package com.neueda.leap.services;

import com.neueda.leap.compliance.ComplianceAuditRecorder;
import com.neueda.leap.compliance.ComplianceOutcome;
import com.neueda.leap.compliance.ComplianceReport;
import com.neueda.leap.compliance.ComplianceTestSupport;
import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.enums.*;
import com.neueda.leap.exceptions.ComplianceException;
import com.neueda.leap.messaging.OrderEventOutbox;
import com.neueda.leap.models.*;
import com.neueda.leap.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Pre-trade compliance as seen through placeOrder: blocked, warned and acknowledged orders */
@ExtendWith(MockitoExtension.class)
class OrderComplianceTest {
    @Mock OrderEventOutbox outbox;
    @Mock OrderRepository orders;
    @Mock AccountRepository accounts;
    @Mock InstrumentsRepository instruments;
    @Mock PositionsRepository positions;
    @Mock OrderHistoryRepository history;
    @Mock MarketPriceService prices;
    @Mock OrderSettlementService settlement;
    @Mock jakarta.persistence.EntityManager entityManager;
    @Mock RestrictedInstrumentRepository restricted;
    @Mock ComplianceAuditRecorder audit;
    @InjectMocks OrderService service;
    Account account;

    @BeforeEach void setup() {
        account = new Account("ACC-0002", "Brian", "b@example.com", null, new BigDecimal("10000.00"), AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(account, "id", 2L);
        lenient().when(accounts.findByAccountId("ACC-0002")).thenReturn(Optional.of(account));
        lenient().when(accounts.findByIdForUpdate(2L)).thenReturn(Optional.of(account));
        lenient().when(orders.save(any())).thenAnswer(call -> call.getArgument(0));
        ReflectionTestUtils.setField(service, "complianceService", ComplianceTestSupport.service(restricted, audit));
    }

    PlaceOrderRequest buy(String symbol, int quantity, String price) {
        when(instruments.findBySymbol(symbol)).thenReturn(Optional.of(new Instrument(symbol, symbol, "EQUITY", "USD", true)));
        var request = new PlaceOrderRequest("ACC-0002", symbol, OrderSide.BUY, quantity, new BigDecimal(price));
        request.setIdempotencyKey("key-" + symbol + quantity);
        return request;
    }

    @Test void restrictedStockIsBlockedAndTheAttemptIsAudited() {
        when(restricted.findById("TSLA")).thenReturn(Optional.of(new RestrictedInstrument("TSLA", "under internal compliance review", "system")));
        var error = assertThrows(ComplianceException.class, () -> service.placeOrder(buy("TSLA", 1, "200.00")));

        assertEquals(OrderErrorCode.COMPLIANCE_BLOCKED, error.getErrorCode());
        assertEquals("You can't buy TSLA. It's on the restricted list: under internal compliance review", error.getMessage());
        verify(orders, never()).save(any());
        verify(outbox, never()).command(any(), any());
        // Every rule's result was recorded, the block included
        var report = ArgumentCaptor.forClass(ComplianceReport.class);
        verify(audit).record(report.capture(), argThat(o -> o.symbol().equals("TSLA")), any());
        assertEquals(5, report.getValue().results().size());
        assertEquals(ComplianceOutcome.BLOCK, report.getValue().results().get(0).outcome());
    }

    @Test void concentratedBuyNeedsTheWarningAcknowledged() {
        var error = assertThrows(ComplianceException.class, () -> service.placeOrder(buy("NVDA", 30, "100.00")));
        assertEquals(OrderErrorCode.COMPLIANCE_WARNING, error.getErrorCode());
        assertEquals("SINGLE_STOCK_LIMIT", error.getReasons().get(0).ruleId());
        verify(orders, never()).save(any());
    }

    @Test void acknowledgedWarningPlacesTheOrderAndLinksItsChecks() throws Exception {
        var request = buy("NVDA", 30, "100.00");
        request.setAcknowledgedWarnings(List.of("SINGLE_STOCK_LIMIT"));
        Order order = service.placeOrder(request);
        assertEquals(OrderStatus.PENDING, order.getStatus());
        verify(audit).linkOrder(any(), eq(order.getId()));
        verify(outbox).command(order, "SUBMIT");
    }

    @Test void cleanOrderIsAcceptedAsBefore() throws Exception {
        Order order = service.placeOrder(buy("AAPL", 5, "100.00"));
        assertEquals(OrderStatus.PENDING, order.getStatus());
        verify(audit).record(any(), any(), any());
        verify(audit).linkOrder(any(), eq(order.getId()));
    }

    @Test void complianceErrorsAreStillOrderErrors() {
        // Existing callers that catch OrderException keep working
        when(restricted.findById("TSLA")).thenReturn(Optional.of(new RestrictedInstrument("TSLA", "review", "system")));
        assertThrows(com.neueda.leap.exceptions.OrderException.class, () -> service.placeOrder(buy("TSLA", 1, "200.00")));
    }
}
