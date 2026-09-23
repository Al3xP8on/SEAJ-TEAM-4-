-- SEAJ OLTP schema - Price History Table
-- historical price data from yfinance (OHLCV)

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

-- Indexes
CREATE INDEX idx_price_history_symbol ON price_history(symbol);
CREATE INDEX idx_price_history_date ON price_history(price_date);
CREATE INDEX idx_price_history_symbol_date ON price_history(symbol, price_date DESC);
