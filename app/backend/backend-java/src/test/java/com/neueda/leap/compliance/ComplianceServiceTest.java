package com.neueda.leap.compliance;

import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.enums.OrderErrorCode;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.exceptions.ComplianceException;
import com.neueda.leap.models.Account;
import com.neueda.leap.models.Instrument;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The engine: every rule runs, every result is recorded, and blocks or unacknowledged warnings stop the order */
class ComplianceServiceTest {
    final ComplianceAuditRecorder audit = mock(ComplianceAuditRecorder.class);
    final OrderContext order = new OrderContext(
            new Account("ACC-0001", "Jay", "jay@example.com", null, new BigDecimal("10000.00"), AccountStatus.ACTIVE),
            new Instrument("AAPL", "Apple", "EQUITY", "USD", true), OrderSide.BUY, 1, new BigDecimal("100.00"),
            0, List.of(), List.of(), symbol -> Optional.empty());

    static ComplianceRule rule(String id, RuleResult result) {
        return new ComplianceRule() {
            @Override public String id() { return id; }
            @Override public RuleResult check(OrderContext ignored) { return result; }
        };
    }

    static ComplianceRule warning(String id) {
        return rule(id, RuleResult.warn(id, id + " warning", Map.of()));
    }

    @Test void cleanOrderIsRecordedAndAllowed() {
        var service = new ComplianceService(List.of(rule("A", RuleResult.pass("A")), rule("B", RuleResult.pass("B"))), audit);
        ComplianceReport report = service.enforce(order, List.of());
        assertEquals(2, report.results().size());
        verify(audit).record(eq(report), eq(order), eq(Set.of()));
    }

    @Test void blockedOrderIsRecordedBeforeItIsStopped() {
        var service = new ComplianceService(List.of(
                rule("RESTRICTED_LIST", RuleResult.block("RESTRICTED_LIST", "Restricted", Map.of())),
                rule("OTHER", RuleResult.pass("OTHER"))), audit);
        var error = assertThrows(ComplianceException.class, () -> service.enforce(order, List.of()));
        assertEquals(OrderErrorCode.COMPLIANCE_BLOCKED, error.getErrorCode());
        assertEquals("Restricted", error.getMessage());
        assertEquals(List.of("RESTRICTED_LIST"), error.getReasons().stream().map(RuleResult::ruleId).toList());
        // Every rule still ran and was recorded, not just the one that blocked
        verify(audit).record(argThat(r -> r.results().size() == 2), eq(order), any());
    }

    @Test void acknowledgingAWarningCannotOverrideABlock() {
        var service = new ComplianceService(List.of(warning("W"),
                rule("B", RuleResult.block("B", "Blocked", Map.of()))), audit);
        var error = assertThrows(ComplianceException.class, () -> service.enforce(order, List.of("W", "B")));
        assertEquals(OrderErrorCode.COMPLIANCE_BLOCKED, error.getErrorCode());
    }

    @Test void warningStopsTheOrderUntilAcknowledged() {
        var service = new ComplianceService(List.of(warning("SINGLE_STOCK_LIMIT")), audit);
        var error = assertThrows(ComplianceException.class, () -> service.enforce(order, List.of()));
        assertEquals(OrderErrorCode.COMPLIANCE_WARNING, error.getErrorCode());
        assertEquals("SINGLE_STOCK_LIMIT warning", error.getReasons().get(0).reason());
        assertEquals("SINGLE_STOCK_LIMIT warning Confirm to place the order anyway.", error.getMessage());
        verify(audit).record(any(), eq(order), eq(Set.of()));
    }

    @Test void acknowledgedWarningLetsTheOrderThroughAndIsRecordedAsAcknowledged() {
        var service = new ComplianceService(List.of(warning("SINGLE_STOCK_LIMIT")), audit);
        assertDoesNotThrow(() -> service.enforce(order, List.of("SINGLE_STOCK_LIMIT")));
        verify(audit).record(any(), eq(order), eq(Set.of("SINGLE_STOCK_LIMIT")));
    }

    @Test void everyWarningMustBeAcknowledged() {
        var service = new ComplianceService(List.of(warning("A"), warning("B")), audit);
        var error = assertThrows(ComplianceException.class, () -> service.enforce(order, List.of("A")));
        assertEquals(List.of("B"), error.getReasons().stream().map(RuleResult::ruleId).toList());
    }

    @Test void aRuleThatFailsBlocksTheOrderInsteadOfLettingItThrough() {
        ComplianceRule broken = new ComplianceRule() {
            @Override public String id() { return "BROKEN"; }
            @Override public RuleResult check(OrderContext ignored) { throw new IllegalStateException("database down"); }
        };
        var service = new ComplianceService(List.of(broken), audit);
        var error = assertThrows(ComplianceException.class, () -> service.enforce(order, List.of()));
        assertEquals("We couldn't complete our pre-trade checks. Please try again shortly.", error.getMessage());
    }

    @Test void acceptedOrderIsLinkedToItsChecks() {
        var service = new ComplianceService(List.of(rule("A", RuleResult.pass("A"))), audit);
        ComplianceReport report = service.enforce(order, null);
        UUID orderId = UUID.randomUUID();
        service.linkOrder(report, orderId);
        verify(audit).linkOrder(report.checkGroupId(), orderId);
    }
}
