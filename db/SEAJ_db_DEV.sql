-- SEAJ OLTP schema (Postgres) - Development database with performance optimizations
-- Core tables: ACCOUNTS, INSTRUMENTS, ORDERS, POSITIONS, PRICE_HISTORY, CURRENT_PRICES, ORDER_HISTORY

CREATE EXTENSION IF NOT EXISTS pgcrypto; -- for gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS pg_prewarm;

-- Enums (section 6.4)
CREATE TYPE account_status AS ENUM ('ACTIVE', 'SUSPENDED', 'CLOSED');
CREATE TYPE order_side     AS ENUM ('BUY', 'SELL');
CREATE TYPE order_status   AS ENUM ('NEW', 'FILLED', 'REJECTED', 'CANCELLED');

-- ACCOUNTS: trading accounts and cash balances
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
    quantity        INT NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    average_cost    NUMERIC(18, 2) NOT NULL DEFAULT 0,
    PRIMARY KEY (account_id, symbol)
);

-- PRICE_HISTORY: historical price data from yfinance (OHLCV)
CREATE TABLE price_history (
    symbol          VARCHAR(20) NOT NULL REFERENCES instruments(symbol),
    price_date      DATE NOT NULL,
    open            NUMERIC(18,2) NOT NULL,
    high            NUMERIC(18,2) NOT NULL,
    low             NUMERIC(18,2) NOT NULL,
    close           NUMERIC(18,2) NOT NULL,
    volume          BIGINT NOT NULL,
    PRIMARY KEY (symbol, price_date)
);

-- CURRENT_PRICES: latest price snapshot for fast portfolio valuation queries
CREATE TABLE current_prices (
    symbol          VARCHAR(20) PRIMARY KEY REFERENCES instruments(symbol),
    price           NUMERIC(18,2) NOT NULL,
    last_updated    TIMESTAMP NOT NULL
);

-- ORDER_HISTORY: historical structure recording every status transition of an order
CREATE TABLE order_history (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id        UUID NOT NULL REFERENCES orders(id),
    status          order_status NOT NULL,
    changed_on      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes for query performance
CREATE INDEX idx_orders_account_id ON orders(account_id);
CREATE INDEX idx_orders_symbol ON orders(symbol);
CREATE INDEX idx_orders_created_on ON orders(created_on);
CREATE INDEX idx_positions_account_id ON positions(account_id);
CREATE INDEX idx_order_history_order_id ON order_history(order_id);
CREATE INDEX idx_instruments_tradable ON instruments(tradable) WHERE tradable = TRUE;
CREATE INDEX idx_price_history_symbol ON price_history(symbol);
CREATE INDEX idx_price_history_date ON price_history(price_date);
CREATE INDEX idx_price_history_symbol_date ON price_history(symbol, price_date DESC);
CREATE INDEX idx_current_prices_updated ON current_prices(last_updated);

-- Performance optimization: prewarming frequently accessed tables
SELECT pg_prewarm('instruments');
SELECT pg_prewarm('idx_orders_account_id');
SELECT pg_prewarm('idx_orders_created_on');
SELECT pg_prewarm('positions');
SELECT pg_prewarm('idx_positions_account_id');
SELECT pg_prewarm('price_history');
SELECT pg_prewarm('current_prices');
SELECT pg_prewarm('idx_price_history_symbol_date');
SELECT pg_prewarm('idx_current_prices_updated');

-- Materialized view: avoids repeated joins, includes current pricing for real portfolio valuation
CREATE MATERIALIZED VIEW IF NOT EXISTS account_positions_summary AS
SELECT
    a.account_id,
    a.holder_name,
    p.symbol,
    i.name                  AS instrument_name,
    i.asset_class,
    i.currency,
    p.quantity,
    p.average_cost,
    (p.quantity * p.average_cost)     AS cost_basis,
    cp.price                AS current_price,
    (p.quantity * cp.price)           AS current_market_value,
    (p.quantity * cp.price) - (p.quantity * p.average_cost) AS unrealized_gain_loss,
    cp.last_updated
FROM positions p
JOIN accounts a         ON a.id = p.account_id
JOIN instruments i      ON i.symbol = p.symbol
LEFT JOIN current_prices cp ON cp.symbol = p.symbol
WHERE p.quantity > 0;

CREATE UNIQUE INDEX IF NOT EXISTS idx_account_positions_summary_pk
    ON account_positions_summary(account_id, symbol);
