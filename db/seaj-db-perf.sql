CREATE EXTENSION IF NOT EXISTS pg_prewarm;

SELECT pg_prewarm('instruments');
SELECT pg_prewarm('idx_orders_account_id');
SELECT pg_prewarm('idx_orders_created_on');
SELECT pg_prewarm('positions');
SELECT pg_prewarm('idx_positions_account_id');
SELECT pg_prewarm('price_history');
SELECT pg_prewarm('current_prices');
SELECT pg_prewarm('idx_price_history_symbol_date');
SELECT pg_prewarm('idx_current_prices_updated');

-- avoids repeated joins, includes current pricing for real portfolio valuation
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
