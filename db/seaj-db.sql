-- SEAJ OLTP schema (Postgres)
-- Core tables: ACCOUNTS, INSTRUMENTS, ORDERS, POSITIONS

CREATE EXTENSION IF NOT EXISTS pgcrypto; -- for gen_random_uuid()

-- Enums (section 6.4)
CREATE TYPE account_status AS ENUM ('ACTIVE', 'SUSPENDED', 'CLOSED');
CREATE TYPE order_side     AS ENUM ('BUY', 'SELL');
CREATE TYPE order_status   AS ENUM ('NEW', 'FILLED', 'REJECTED', 'CANCELLED');

-- ACCOUNTS: trading accounts and cash balances
-- Note: holder_name is kept inline per spec; a separate clients table (1 client : many accounts)
-- would remove this duplication (but we can add that later as we progress w/ defining the db!)
CREATE TABLE accounts (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id      VARCHAR(32) NOT NULL UNIQUE,
    holder_name     VARCHAR(255) NOT NULL,
    cash_balance    NUMERIC(18, 2) NOT NULL DEFAULT 0 CHECK (cash_balance >= 0),
    status          account_status NOT NULL DEFAULT 'ACTIVE',
    version         INT NOT NULL DEFAULT 0,
    last_updated    TIMESTAMP NOT NULL DEFAULT NOW()
);

-- INSTRUMENTS: tradable instruments / securities
CREATE TABLE instruments (
    symbol          VARCHAR(20) PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    asset_class     VARCHAR(50) NOT NULL,
    currency        CHAR(3) NOT NULL,
    tradable        BOOLEAN NOT NULL DEFAULT TRUE
);

-- ORDERS: placed and executed orders (the audit trail)
CREATE TABLE orders (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id          BIGINT NOT NULL REFERENCES accounts(id),
    symbol              VARCHAR(20) NOT NULL REFERENCES instruments(symbol),
    side                order_side NOT NULL,
    quantity            INT NOT NULL CHECK (quantity > 0),
    price               NUMERIC(18, 2) NOT NULL CHECK (price > 0),
    status              order_status NOT NULL DEFAULT 'NEW',
    idempotency_key     VARCHAR(100) NOT NULL UNIQUE,
    created_on          TIMESTAMP NOT NULL DEFAULT NOW()
);

-- POSITIONS: holdings per account and instrument
CREATE TABLE positions (
    account_id      BIGINT NOT NULL REFERENCES accounts(id),
    symbol          VARCHAR(20) NOT NULL REFERENCES instruments(symbol),
    quantity        INT NOT NULL DEFAULT 0,
    average_cost    NUMERIC(18, 2) NOT NULL DEFAULT 0,
    PRIMARY KEY (account_id, symbol)
);

-- ORDER_HISTORY: historical structure recording every status transition of an order
CREATE TABLE order_history (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id        UUID NOT NULL REFERENCES orders(id),
    status          order_status NOT NULL,
    changed_on      TIMESTAMP NOT NULL DEFAULT NOW(),
    note            TEXT
);

-- Indexes for query performance
CREATE INDEX idx_orders_account_id ON orders(account_id);
CREATE INDEX idx_orders_symbol ON orders(symbol);
CREATE INDEX idx_orders_created_on ON orders(created_on);
CREATE INDEX idx_positions_account_id ON positions(account_id);
CREATE INDEX idx_order_history_order_id ON order_history(order_id);
CREATE INDEX idx_instruments_tradable ON instruments(tradable) WHERE tradable = TRUE;

-- Seed data: representative accounts, instruments, trades

INSERT INTO accounts (account_id, holder_name, cash_balance, status) VALUES
    ('ACC-0001', 'Alice Johnson', 10000.00, 'ACTIVE'),
    ('ACC-0002', 'Brian Osei', 5000.00, 'ACTIVE'),
    ('ACC-0003', 'Carla Mendes', 0.00, 'SUSPENDED'),
    ('ACC-0004', 'Diane Carter', 25000.00, 'ACTIVE'),
    ('ACC-0005', 'Ethan Brooks', 8000.00, 'ACTIVE');

INSERT INTO instruments (symbol, name, asset_class, currency, tradable) VALUES
    ('GLBEQ1', 'Global Equity Index Fund', 'Equity', 'GBP', TRUE),
    ('CORPB1', 'Sterling Corporate Bond Fund', 'Bond', 'GBP', TRUE),
    ('GILT10', 'UK 10-Year Gilt', 'Bond', 'GBP', TRUE),
    ('CASHGBP', 'Cash (GBP)', 'Cash', 'GBP', FALSE),
    ('AAPL', 'Apple Inc.', 'Equity', 'USD', TRUE),
    ('MSFT', 'Microsoft Corporation', 'Equity', 'USD', TRUE),
    ('UST10', 'US 10-Year Treasury Note', 'Bond', 'USD', TRUE),
    ('CASHUSD', 'Cash (USD)', 'Cash', 'USD', FALSE);

INSERT INTO orders (account_id, symbol, side, quantity, price, status, idempotency_key) VALUES
    (1, 'GLBEQ1', 'BUY', 100, 12.50, 'FILLED', 'IDEMP-0001'),
    (1, 'CORPB1', 'BUY', 50, 25.00, 'FILLED', 'IDEMP-0002'),
    (2, 'GLBEQ1', 'BUY', 200, 12.75, 'NEW', 'IDEMP-0003'),
    (3, 'GILT10', 'BUY', 75, 98.20, 'REJECTED', 'IDEMP-0004'),
    (4, 'AAPL', 'BUY', 40, 190.25, 'FILLED', 'IDEMP-0005'),
    (4, 'MSFT', 'BUY', 20, 410.00, 'FILLED', 'IDEMP-0006'),
    (5, 'UST10', 'BUY', 60, 97.50, 'NEW', 'IDEMP-0007'),
    (5, 'AAPL', 'SELL', 10, 195.00, 'CANCELLED', 'IDEMP-0008');

INSERT INTO positions (account_id, symbol, quantity, average_cost) VALUES
    (1, 'GLBEQ1', 100, 12.50),
    (1, 'CORPB1', 50, 25.00),
    (2, 'GLBEQ1', 0, 0.00),
    (4, 'AAPL', 40, 190.25),
    (4, 'MSFT', 20, 410.00),
    (5, 'UST10', 0, 0.00);

INSERT INTO order_history (order_id, status, note)
    SELECT id, status, 'Initial seed load' FROM orders;
