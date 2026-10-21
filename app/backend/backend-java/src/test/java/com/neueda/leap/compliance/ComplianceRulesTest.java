package com.neueda.leap.compliance;

import com.neueda.leap.compliance.rules.*;
import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.RiskProfile;
import com.neueda.leap.models.*;
import com.neueda.leap.repositories.RestrictedInstrumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Each rule on its own: given an account and an order, the expected decision and reason */
class ComplianceRulesTest {
    Account account;
    Instrument aapl;
    Instrument tsla;

    @BeforeEach void setup() {
        account = new Account("ACC-0001", "Jay", "jay@example.com", null, new BigDecimal("10000.00"), AccountStatus.ACTIVE);
        aapl = new Instrument("AAPL", "Apple", "EQUITY", "USD", true);
        tsla = new Instrument("TSLA", "Tesla", "EQUITY", "USD", true);
    }

    OrderContext buy(Instrument instrument, long quantity, String price) {
        return context(instrument, OrderSide.BUY, quantity, price, 0, List.of(), List.of());
    }

    OrderContext context(Instrument instrument, OrderSide side, long quantity, String price, long held,
                         List<Order> pending, List<Positions> positions) {
        return new OrderContext(account, instrument, side, quantity, new BigDecimal(price), held, pending, positions,
                symbol -> Optional.empty());
    }

    // ---- Restricted list ----

    @Test void restrictedInstrumentCannotBeBought() {
        var repository = mock(RestrictedInstrumentRepository.class);
        when(repository.findById("TSLA")).thenReturn(Optional.of(new RestrictedInstrument("TSLA", "under internal compliance review", "system")));
        RuleResult result = new RestrictedListRule(repository).check(buy(tsla, 1, "200.00"));
        assertEquals(ComplianceOutcome.BLOCK, result.outcome());
        assertEquals("You can't buy TSLA. It's on the restricted list: under internal compliance review", result.reason());
    }

    @Test void restrictedInstrumentCanStillBeSold() {
        var repository = mock(RestrictedInstrumentRepository.class);
        var sell = context(tsla, OrderSide.SELL, 1, "200.00", 5, List.of(), List.of());
        assertEquals(ComplianceOutcome.PASS, new RestrictedListRule(repository).check(sell).outcome());
        verifyNoInteractions(repository);
    }

    @Test void unrestrictedInstrumentPasses() {
        var repository = mock(RestrictedInstrumentRepository.class);
        assertEquals(ComplianceOutcome.PASS, new RestrictedListRule(repository).check(buy(aapl, 1, "100.00")).outcome());
    }

    // ---- Cash available ----

    @Test void buyWithinAvailableCashPasses() {
        assertEquals(ComplianceOutcome.PASS, new CashAvailableRule().check(buy(aapl, 100, "100.00")).outcome());
    }

    @Test void cashPromisedToPendingBuysIsNotSpentTwice() {
        var pending = new Order(account, aapl, 95, new BigDecimal("100.00"), OrderSide.BUY, "earlier");
        RuleResult result = new CashAvailableRule().check(context(aapl, OrderSide.BUY, 10, "100.00", 0, List.of(pending), List.of()));
        assertEquals(ComplianceOutcome.BLOCK, result.outcome());
        assertEquals("Not enough cash: this order needs $1,000.00, and $500.00 is available after your pending buy orders.", result.reason());
    }

    // ---- Holdings available ----

    @Test void cannotSellMoreThanHeldLessPendingSells() {
        var pending = new Order(account, aapl, 2, new BigDecimal("100.00"), OrderSide.SELL, "earlier");
        RuleResult result = new HoldingsAvailableRule().check(context(aapl, OrderSide.SELL, 2, "100.00", 3, List.of(pending), List.of()));
        assertEquals(ComplianceOutcome.BLOCK, result.outcome());
        assertEquals("You can sell up to 1 AAPL shares: you hold 3 and 2 are already in pending sell orders.", result.reason());
    }

    @Test void sellingHeldSharesPasses() {
        assertEquals(ComplianceOutcome.PASS,
                new HoldingsAvailableRule().check(context(aapl, OrderSide.SELL, 3, "100.00", 3, List.of(), List.of())).outcome());
    }

    // ---- Single-stock limit (balanced: warn above 25%, block above 50%) ----

    @Test void smallShareOfPortfolioPasses() {
        assertEquals(ComplianceOutcome.PASS, new SingleStockLimitRule().check(buy(aapl, 20, "100.00")).outcome());
    }

    @Test void shareAboveTheProfileLimitWarns() {
        RuleResult result = new SingleStockLimitRule().check(buy(aapl, 30, "100.00"));
        assertEquals(ComplianceOutcome.WARN, result.outcome());
        assertEquals("After this order, AAPL would be 30% of your portfolio. The limit for a balanced risk profile is 25%.", result.reason());
    }

    @Test void shareAboveTheMaximumBlocks() {
        RuleResult result = new SingleStockLimitRule().check(buy(aapl, 60, "100.00"));
        assertEquals(ComplianceOutcome.BLOCK, result.outcome());
        assertTrue(result.reason().contains("60% of your portfolio, above the 50% maximum"));
    }

    @Test void sharesAlreadyHeldCountTowardsTheLimit() {
        var held = new Positions(1L, "AAPL", 20, new BigDecimal("90.00"));
        var order = context(aapl, OrderSide.BUY, 10, "100.00", 20, List.of(), List.of(held));
        // 30 shares x $100 against $10,000 cash + $2,000 holdings = 25%: right at the limit, so it passes
        assertEquals(ComplianceOutcome.PASS, new SingleStockLimitRule().check(order).outcome());
    }

    @Test void conservativeProfileHasATighterLimit() {
        account.setRiskProfile(RiskProfile.CONSERVATIVE);
        assertEquals(ComplianceOutcome.BLOCK, new SingleStockLimitRule().check(buy(aapl, 25, "100.00")).outcome());
    }

    @Test void verySmallPortfoliosAreNotLimited() {
        account = new Account("ACC-0002", "Brian", "b@example.com", null, new BigDecimal("500.00"), AccountStatus.ACTIVE);
        assertEquals(ComplianceOutcome.PASS, new SingleStockLimitRule().check(buy(aapl, 4, "100.00")).outcome());
    }

    @Test void otherHoldingsAreValuedAtTheirLatestPrice() {
        var msft = new Positions(1L, "MSFT", 100, new BigDecimal("10.00"));
        var order = new OrderContext(account, aapl, OrderSide.BUY, 30, new BigDecimal("100.00"), 0, List.of(), List.of(msft),
                symbol -> symbol.equals("MSFT") ? Optional.of(new BigDecimal("100.00")) : Optional.empty());
        // $10,000 cash + 100 MSFT at $100 = $20,000, so 30 AAPL ($3,000) is 15%
        assertEquals(ComplianceOutcome.PASS, new SingleStockLimitRule().check(order).outcome());
    }

    // ---- Risk profile: order size ----

    @Test void balancedProfileWarnsOnVeryLargeOrders() {
        account = new Account("ACC-0004", "Diane", "d@example.com", null, new BigDecimal("100000.00"), AccountStatus.ACTIVE);
        RuleResult result = new RiskProfileRule().check(buy(aapl, 300, "100.00"));
        assertEquals(ComplianceOutcome.WARN, result.outcome());
        assertEquals("This order is $30,000.00. Orders above $25,000.00 are outside a balanced risk profile.", result.reason());
    }

    @Test void conservativeProfileBlocksLargeOrders() {
        account.setRiskProfile(RiskProfile.CONSERVATIVE);
        assertEquals(ComplianceOutcome.BLOCK, new RiskProfileRule().check(buy(aapl, 60, "100.00")).outcome());
    }

    @Test void aggressiveProfileHasNoOrderSizeLimit() {
        account.setRiskProfile(RiskProfile.AGGRESSIVE);
        assertEquals(ComplianceOutcome.PASS, new RiskProfileRule().check(buy(aapl, 100_000, "100.00")).outcome());
    }

    @Test void accountsWithoutAProfileAreBalanced() {
        assertEquals(RiskProfile.BALANCED, account.getRiskProfile());
    }

    @Test void detailsAreKeptForTheAuditTrail() {
        RuleResult result = new SingleStockLimitRule().check(buy(aapl, 30, "100.00"));
        assertEquals(Map.of("share", new BigDecimal("0.3000"), "warnAt", new BigDecimal("0.25"),
                "blockAt", new BigDecimal("0.50"), "riskProfile", "BALANCED"), result.details());
    }
}
