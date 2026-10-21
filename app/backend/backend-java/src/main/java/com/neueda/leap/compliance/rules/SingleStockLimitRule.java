package com.neueda.leap.compliance.rules;

import com.neueda.leap.compliance.ComplianceRule;
import com.neueda.leap.compliance.OrderContext;
import com.neueda.leap.compliance.RuleResult;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.RiskProfile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import static com.neueda.leap.compliance.Money.percent;

/**
 * Warns, then blocks, when a buy would put too much of the portfolio into one instrument.
 * The limits come from the account's risk profile.
 */
@Component
@Order(40)
public class SingleStockLimitRule implements ComplianceRule {
    public static final String ID = "SINGLE_STOCK_LIMIT";
    /** Below this, any single purchase is a large share, so the limit would only get in the way */
    static final BigDecimal MINIMUM_PORTFOLIO = new BigDecimal("1000");

    @Override public String id() { return ID; }

    @Override
    public RuleResult check(OrderContext order) {
        if (order.side() != OrderSide.BUY) return RuleResult.pass(ID);
        BigDecimal portfolio = order.portfolioValue();
        if (portfolio.compareTo(MINIMUM_PORTFOLIO) < 0) return RuleResult.pass(ID);

        long sharesAfter = order.heldQuantity() + order.reservedQuantity(OrderSide.BUY) + order.quantity();
        BigDecimal share = order.price().multiply(BigDecimal.valueOf(sharesAfter)).divide(portfolio, 4, RoundingMode.HALF_UP);
        RiskProfile profile = order.account().getRiskProfile();
        Map<String, Object> details = Map.of("share", share, "warnAt", profile.concentrationWarn(),
                "blockAt", profile.concentrationBlock(), "riskProfile", profile.name());

        if (share.compareTo(profile.concentrationBlock()) > 0) {
            return RuleResult.block(ID, "After this order, " + order.symbol() + " would be " + percent(share)
                    + " of your portfolio, above the " + percent(profile.concentrationBlock()) + " maximum for a "
                    + profile.label() + " risk profile.", details);
        }
        if (share.compareTo(profile.concentrationWarn()) > 0) {
            return RuleResult.warn(ID, "After this order, " + order.symbol() + " would be " + percent(share)
                    + " of your portfolio. The limit for a " + profile.label() + " risk profile is "
                    + percent(profile.concentrationWarn()) + ".", details);
        }
        return RuleResult.pass(ID);
    }
}
