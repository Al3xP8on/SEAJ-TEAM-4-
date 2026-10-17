package com.neueda.leap.compliance.rules;

import com.neueda.leap.compliance.ComplianceRule;
import com.neueda.leap.compliance.OrderContext;
import com.neueda.leap.compliance.RuleResult;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.repositories.RestrictedInstrumentRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Blocks buying an instrument on the restricted list. Selling stays allowed so holders can exit. */
@Component
@Order(10)
public class RestrictedListRule implements ComplianceRule {
    public static final String ID = "RESTRICTED_LIST";
    private final RestrictedInstrumentRepository restricted;

    public RestrictedListRule(RestrictedInstrumentRepository restricted) {
        this.restricted = restricted;
    }

    @Override public String id() { return ID; }

    @Override
    public RuleResult check(OrderContext order) {
        if (order.side() != OrderSide.BUY) return RuleResult.pass(ID);
        return restricted.findById(order.symbol())
                .map(entry -> RuleResult.block(ID,
                        "You can't buy " + order.symbol() + ". It's on the restricted list: " + entry.getReason(),
                        Map.of("restrictedReason", entry.getReason())))
                .orElseGet(() -> RuleResult.pass(ID));
    }
}
