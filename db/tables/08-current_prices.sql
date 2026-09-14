-- SEAJ OLTP schema - Current Prices Table
-- latest price snapshot for fast portfolio valuation queries

CREATE TABLE current_prices (
    symbol          VARCHAR(20) PRIMARY KEY REFERENCES instruments(symbol),
    price           NUMERIC(18,2) NOT NULL,
    last_updated    TIMESTAMP NOT NULL
);

-- Indexes
CREATE INDEX idx_current_prices_updated ON current_prices(last_updated);
