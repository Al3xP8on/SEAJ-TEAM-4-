-- SEAJ OLTP schema - Orders Table
-- placed and executed orders (the audit trail)

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

-- Indexes
CREATE INDEX idx_orders_account_id ON orders(account_id);
CREATE INDEX idx_orders_symbol ON orders(symbol);
CREATE INDEX idx_orders_created_on ON orders(created_on);
CREATE INDEX idx_orders_status ON orders(status);

-- Seed data
INSERT INTO orders (account_id, symbol, side, quantity, price, status, idempotency_key) VALUES
    (1, 'GLBEQ1', 'BUY', 100, 12.50, 'FILLED', 'IDEMP-0001'),
    (1, 'CORPB1', 'BUY', 50, 25.00, 'FILLED', 'IDEMP-0002'),
    (2, 'GLBEQ1', 'BUY', 200, 12.75, 'NEW', 'IDEMP-0003'),
    (3, 'GILT10', 'BUY', 75, 98.20, 'REJECTED', 'IDEMP-0004'),
    (4, 'AAPL', 'BUY', 40, 190.25, 'FILLED', 'IDEMP-0005'),
    (4, 'MSFT', 'BUY', 20, 410.00, 'FILLED', 'IDEMP-0006'),
    (5, 'UST10', 'BUY', 60, 97.50, 'NEW', 'IDEMP-0007'),
    (5, 'AAPL', 'SELL', 10, 195.00, 'CANCELLED', 'IDEMP-0008');
