-- SEAJ OLTP schema - Instruments Table
-- tradable instruments / securities

CREATE TABLE instruments (
    symbol          VARCHAR(20) PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    asset_class     VARCHAR(50) NOT NULL,
    currency        CHAR(3) NOT NULL,
    tradable        BOOLEAN NOT NULL DEFAULT TRUE
);

-- Indexes
CREATE INDEX idx_instruments_tradable ON instruments(tradable) WHERE tradable = TRUE;
CREATE INDEX idx_instruments_asset_class ON instruments(asset_class);
CREATE INDEX idx_instruments_currency ON instruments(currency);

-- Seed data
INSERT INTO instruments (symbol, name, asset_class, currency, tradable) VALUES
    ('GLBEQ1', 'Global Equity Index Fund', 'Equity', 'GBP', TRUE),
    ('CORPB1', 'Sterling Corporate Bond Fund', 'Bond', 'GBP', TRUE),
    ('GILT10', 'UK 10-Year Gilt', 'Bond', 'GBP', TRUE),
    ('CASHGBP', 'Cash (GBP)', 'Cash', 'GBP', FALSE),
    ('AAPL', 'Apple Inc.', 'Equity', 'USD', TRUE),
    ('MSFT', 'Microsoft Corporation', 'Equity', 'USD', TRUE),
    ('UST10', 'US 10-Year Treasury Note', 'Bond', 'USD', TRUE),
    ('CASHUSD', 'Cash (USD)', 'Cash', 'USD', FALSE);
