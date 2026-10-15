-- Apply to an existing database without resetting its data.
ALTER TYPE order_status ADD VALUE IF NOT EXISTS 'PENDING';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS order_type VARCHAR(16) NOT NULL DEFAULT 'LIMIT' CHECK (order_type IN ('MARKET', 'LIMIT'));
ALTER TABLE orders ADD COLUMN IF NOT EXISTS execution_price NUMERIC(18, 2) CHECK (execution_price > 0);
ALTER TABLE order_history ADD COLUMN IF NOT EXISTS reason VARCHAR(500);
