-- Pre-trade compliance: restricted list, audit trail of every check, and account risk profiles.
-- Additive migration. Preserves all existing accounts, orders and positions; safe to run twice.

ALTER TABLE accounts ADD COLUMN IF NOT EXISTS risk_profile VARCHAR(20) NOT NULL DEFAULT 'BALANCED'
    CHECK (risk_profile IN ('CONSERVATIVE', 'BALANCED', 'AGGRESSIVE'));

-- Instruments no customer may buy; selling stays allowed so holders can exit
CREATE TABLE IF NOT EXISTS restricted_instruments (
    symbol      VARCHAR(20) PRIMARY KEY REFERENCES instruments(symbol),
    reason      VARCHAR(255) NOT NULL,
    added_by    VARCHAR(100) NOT NULL,
    added_on    TIMESTAMP NOT NULL DEFAULT NOW()
);

-- One row per rule per order submission, whether the order was accepted, warned or blocked
CREATE TABLE IF NOT EXISTS compliance_checks (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    check_group_id   UUID NOT NULL,
    order_id         UUID,
    account_id       VARCHAR(32) NOT NULL,
    symbol           VARCHAR(20) NOT NULL,
    side             VARCHAR(4) NOT NULL,
    quantity         BIGINT NOT NULL,
    price            NUMERIC(18, 2) NOT NULL,
    rule_id          VARCHAR(50) NOT NULL,
    outcome          VARCHAR(5) NOT NULL CHECK (outcome IN ('PASS', 'WARN', 'BLOCK')),
    reason           VARCHAR(500),
    details          TEXT,
    acknowledged     BOOLEAN NOT NULL DEFAULT FALSE,
    overridden_by    VARCHAR(100),
    override_reason  VARCHAR(500),
    checked_at       TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_compliance_checks_group ON compliance_checks (check_group_id);
CREATE INDEX IF NOT EXISTS idx_compliance_checks_account ON compliance_checks (account_id, checked_at);
CREATE INDEX IF NOT EXISTS idx_compliance_checks_order ON compliance_checks (order_id);

-- Demo restriction for the showcase: try to buy TSLA and show the block plus its audit entry
INSERT INTO restricted_instruments (symbol, reason, added_by)
VALUES ('TSLA', 'under internal compliance review', 'system')
ON CONFLICT (symbol) DO NOTHING;
