-- SEAJ OLTP schema - Positions Table
-- holdings per account and instrument

CREATE TABLE positions (
    account_id      BIGINT NOT NULL REFERENCES accounts(id),
    symbol          VARCHAR(20) NOT NULL REFERENCES instruments(symbol),
    quantity        INT NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    average_cost    NUMERIC(18, 2) NOT NULL DEFAULT 0,
    PRIMARY KEY (account_id, symbol)
);

-- Indexes
CREATE INDEX idx_positions_account_id ON positions(account_id);
CREATE INDEX idx_positions_symbol ON positions(symbol);

-- Seed data
INSERT INTO positions (account_id, symbol, quantity, average_cost) VALUES
    (1, 'GLBEQ1', 100, 12.50),
    (1, 'CORPB1', 50, 25.00),
    (2, 'GLBEQ1', 0, 0.00),
    (4, 'AAPL', 40, 190.25),
    (4, 'MSFT', 20, 410.00),
    (5, 'UST10', 0, 0.00);
