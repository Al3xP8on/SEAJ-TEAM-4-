-- Additive migration. Preserve all existing orders, balances and positions.
CREATE TABLE IF NOT EXISTS order_event_outbox (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    topic VARCHAR(100) NOT NULL,
    message_key VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_order_event_outbox_pending
    ON order_event_outbox (id) WHERE published_at IS NULL;
