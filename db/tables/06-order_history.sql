-- SEAJ OLTP schema - Order History Table
-- historical structure recording every status transition of an order

CREATE TABLE order_history (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id        UUID NOT NULL REFERENCES orders(id),
    status          order_status NOT NULL,
    changed_on      TIMESTAMP NOT NULL DEFAULT NOW(),
    note            TEXT
);

-- Indexes
CREATE INDEX idx_order_history_order_id ON order_history(order_id);
CREATE INDEX idx_order_history_changed_on ON order_history(changed_on);

-- Seed data
INSERT INTO order_history (order_id, status, note)
    SELECT id, status, 'Initial seed load' FROM orders;
