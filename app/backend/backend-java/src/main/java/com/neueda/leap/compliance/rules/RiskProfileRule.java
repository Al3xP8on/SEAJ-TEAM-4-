package com.neueda.leap.compliance.rules;

import com.neueda.leap.compliance.ComplianceRule;
import com.neueda.leap.compliance.OrderContext;
import com.neueda.leap.compliance.RuleResult;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.RiskProfile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.neueda.leap.compliance.Money.usd;

/**
 * Checks a buy's size against the account's risk profile: blocked above the limit for a
 * conservative profile, a warning for a balanced one, no limit for an aggressive one.
 */
@Component
@Order(50)
public class RiskProfileRule implements ComplianceRule {
    public static final String ID = "RISK_PROFILE";

    @Override public String id() { return ID; }

    @Override
    public RuleResult check(OrderContext order) {
        RiskProfile profile = order.account().getRiskProfile();
        if (order.side() != OrderSide.BUY || profile.maxOrderValue() == null
                || order.orderValue().compareTo(profile.maxOrderValue()) <= 0) {
            return RuleResult.pass(ID);
        }
        String reason = "This order is " + usd(order.orderValue()) + ". Orders above " + usd(profile.maxOrderValue())
                + " are outside a " + profile.label() + " risk profile.";
        Map<String, Object> details = Map.of("orderValue", order.orderValue(), "limit", profile.maxOrderValue(),
                "riskProfile", profile.name());
        return profile == RiskProfile.CONSERVATIVE
                ? RuleResult.block(ID, reason, details)
                : RuleResult.warn(ID, reason, details);
    }
}
