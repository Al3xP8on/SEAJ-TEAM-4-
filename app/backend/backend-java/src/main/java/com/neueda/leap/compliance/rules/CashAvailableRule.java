package com.neueda.leap.compliance.rules;

import com.neueda.leap.compliance.ComplianceRule;
import com.neueda.leap.compliance.OrderContext;
import com.neueda.leap.compliance.RuleResult;
import com.neueda.leap.enums.OrderSide;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

import static com.neueda.leap.compliance.Money.usd;

/** Blocks a buy the account can't pay for once cash promised to its pending buys is set aside. */
@Component
@Order(20)
public class CashAvailableRule implements ComplianceRule {
    public static final String ID = "CASH_AVAILABLE";

    @Override public String id() { return ID; }

    @Override
    public RuleResult check(OrderContext order) {
        if (order.side() != OrderSide.BUY) return RuleResult.pass(ID);
        BigDecimal available = order.account().getCashBalance().subtract(order.reservedCash());
        if (available.compareTo(order.orderValue()) >= 0) return RuleResult.pass(ID);
        return RuleResult.block(ID,
                "Not enough cash: this order needs " + usd(order.orderValue()) + ", and " + usd(available.max(BigDecimal.ZERO))
                        + " is available after your pending buy orders.",
                Map.of("required", order.orderValue(), "available", available));
    }
}
