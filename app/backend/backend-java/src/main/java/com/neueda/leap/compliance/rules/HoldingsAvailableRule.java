package com.neueda.leap.compliance.rules;

import com.neueda.leap.compliance.ComplianceRule;
import com.neueda.leap.compliance.OrderContext;
import com.neueda.leap.compliance.RuleResult;
import com.neueda.leap.enums.OrderSide;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Blocks selling more shares than the account holds, less those already in pending sell orders. */
@Component
@Order(30)
public class HoldingsAvailableRule implements ComplianceRule {
    public static final String ID = "HOLDINGS_AVAILABLE";

    @Override public String id() { return ID; }

    @Override
    public RuleResult check(OrderContext order) {
        if (order.side() != OrderSide.SELL) return RuleResult.pass(ID);
        long reserved = order.reservedQuantity(OrderSide.SELL);
        long available = Math.max(0, order.heldQuantity() - reserved);
        if (available >= order.quantity()) return RuleResult.pass(ID);
        return RuleResult.block(ID,
                "You can sell up to " + available + " " + order.symbol() + " shares: you hold " + order.heldQuantity()
                        + (reserved > 0 ? " and " + reserved + " are already in pending sell orders." : "."),
                Map.of("held", order.heldQuantity(), "reserved", reserved, "requested", order.quantity()));
    }
}
