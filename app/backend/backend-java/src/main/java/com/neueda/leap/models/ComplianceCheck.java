package com.neueda.leap.models;

import com.neueda.leap.compliance.ComplianceOutcome;
import com.neueda.leap.enums.OrderSide;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Audit trail: one row per rule per order submission, kept whether the order was accepted,
 * warned or blocked. Rows from one submission share a check group id.
 */
@Entity
@Table(name = "compliance_checks", indexes = {
        @Index(name = "idx_compliance_checks_group", columnList = "check_group_id"),
        @Index(name = "idx_compliance_checks_account", columnList = "account_id, checked_at"),
        @Index(name = "idx_compliance_checks_order", columnList = "order_id")
})
public class ComplianceCheck {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "check_group_id", nullable = false)
    private UUID checkGroupId;

    /** Set once the order is accepted; stays empty when it was blocked or warned */
    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "account_id", nullable = false, length = 32)
    private String accountId;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private OrderSide side;

    @Column(nullable = false)
    private long quantity;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal price;

    @Column(name = "rule_id", nullable = false, length = 50)
    private String ruleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private ComplianceOutcome outcome;

    @Column(length = 500)
    private String reason;

    /** The figures behind the decision as JSON, e.g. {"share":0.32,"limit":0.25} */
    @Column(columnDefinition = "TEXT")
    private String details;

    /** The customer confirmed they understood this warning */
    @Column(nullable = false)
    private boolean acknowledged;

    /** Reserved for admin overrides of blocks (not used yet) */
    @Column(name = "overridden_by", length = 100)
    private String overriddenBy;

    @Column(name = "override_reason", length = 500)
    private String overrideReason;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    protected ComplianceCheck() {
    }

    public ComplianceCheck(UUID checkGroupId, String accountId, String symbol, OrderSide side, long quantity,
                           BigDecimal price, String ruleId, ComplianceOutcome outcome, String reason, String details,
                           boolean acknowledged, LocalDateTime checkedAt) {
        this.checkGroupId = checkGroupId;
        this.accountId = accountId;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.ruleId = ruleId;
        this.outcome = outcome;
        this.reason = reason;
        this.details = details;
        this.acknowledged = acknowledged;
        this.checkedAt = checkedAt;
    }

    public Long getId() { return id; }
    public UUID getCheckGroupId() { return checkGroupId; }
    public UUID getOrderId() { return orderId; }
    public String getAccountId() { return accountId; }
    public String getSymbol() { return symbol; }
    public OrderSide getSide() { return side; }
    public long getQuantity() { return quantity; }
    public BigDecimal getPrice() { return price; }
    public String getRuleId() { return ruleId; }
    public ComplianceOutcome getOutcome() { return outcome; }
    public String getReason() { return reason; }
    public String getDetails() { return details; }
    public boolean isAcknowledged() { return acknowledged; }
    public String getOverriddenBy() { return overriddenBy; }
    public String getOverrideReason() { return overrideReason; }
    public LocalDateTime getCheckedAt() { return checkedAt; }
}
