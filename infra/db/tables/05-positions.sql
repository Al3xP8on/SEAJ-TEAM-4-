-- SEAJ OLTP schema - Positions Table
-- holdings per account and instrument

CREATE TABLE positions (
    position_id BIGSERIAL PRIMARY KEY,
    account_id      BIGINT NOT NULL REFERENCES accounts(id),
    symbol          VARCHAR(20) NOT NULL REFERENCES instruments(symbol),
    quantity        INT NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    average_cost    NUMERIC(18, 2) NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    opened_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMP,
    realized_pnl NUMERIC(18, 2),
    UNIQUE (account_id, symbol)
);

-- Indexes
CREATE INDEX idx_positions_account_id ON positions(account_id);
CREATE INDEX idx_positions_symbol ON positions(symbol);

-- Seed data - Comprehensive portfolio for dashboard display
-- Alice (Account ID 1) - 10 positions
INSERT INTO positions (account_id, symbol, quantity, average_cost) VALUES
    (1, 'AAPL', 150, 185.50),
    (1, 'MSFT', 80, 420.25),
    (1, 'GLBEQ1', 200, 50.75),
    (1, 'CORPB1', 100, 95.60),
    (1, 'UST10', 50, 98.40),
    (1, 'GOOGL', 45, 142.30),
    (1, 'NVDA', 60, 875.50),
    (1, 'JPM', 75, 185.20),
    (1, 'SPY', 100, 485.60),
    (1, 'GLD', 35, 195.40);

-- Brian (Account ID 2) - 10 positions
INSERT INTO positions (account_id, symbol, quantity, average_cost) VALUES
    (2, 'AAPL', 75, 188.00),
    (2, 'MSFT', 50, 425.00),
    (2, 'GLBEQ1', 150, 52.00),
    (2, 'CORPB1', 80, 93.50),
    (2, 'UST10', 100, 97.80),
    (2, 'AMZN', 30, 175.50),
    (2, 'META', 45, 325.60),
    (2, 'VOO', 80, 490.25),
    (2, 'QQQ', 40, 380.90),
    (2, 'EEM', 120, 42.60);

-- Diane (Account ID 4) - 12 positions (largest portfolio)
INSERT INTO positions (account_id, symbol, quantity, average_cost) VALUES
    (4, 'AAPL', 200, 180.00),
    (4, 'MSFT', 120, 415.00),
    (4, 'GLBEQ1', 300, 49.50),
    (4, 'CORPB1', 150, 92.00),
    (4, 'UST10', 200, 96.50),
    (4, 'GOOGL', 80, 140.75),
    (4, 'AMZN', 60, 172.00),
    (4, 'NVDA', 100, 870.00),
    (4, 'TSLA', 45, 240.50),
    (4, 'JNJ', 90, 160.40),
    (4, 'VOO', 150, 485.30),
    (4, 'GLD', 75, 192.50);

-- Ethan (Account ID 5) - 10 positions
INSERT INTO positions (account_id, symbol, quantity, average_cost) VALUES
    (5, 'AAPL', 100, 190.00),
    (5, 'MSFT', 60, 430.00),
    (5, 'GLBEQ1', 250, 51.25),
    (5, 'CORPB1', 120, 94.80),
    (5, 'UST10', 150, 97.20),
    (5, 'PG', 85, 145.20),
    (5, 'KO', 100, 62.75),
    (5, 'VTI', 110, 250.40),
    (5, 'GILT10', 180, 96.40),
    (5, 'UST5', 200, 99.10);
