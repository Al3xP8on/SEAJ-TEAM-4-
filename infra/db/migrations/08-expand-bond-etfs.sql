-- Group existing bond ETFs under Bond and add five more USD-listed bond ETFs.
-- Safe to rerun. Preserve symbols, orders, holdings, balances and recorded prices.
BEGIN;
UPDATE instruments SET asset_class = 'Bond'
WHERE symbol IN ('BND', 'AGG', 'TLT', 'IEF', 'SHY', 'LQD', 'HYG', 'TIP')
  AND asset_class = 'ETF';

INSERT INTO instruments (symbol, name, asset_class, currency, tradable) VALUES
    ('BNDX', 'Vanguard Total International Bond ETF', 'Bond', 'USD', TRUE),
    ('IAGG', 'iShares Core International Aggregate Bond ETF', 'Bond', 'USD', TRUE),
    ('JNK', 'State Street SPDR Bloomberg High Yield Bond ETF', 'Bond', 'USD', TRUE),
    ('MUB', 'iShares National Muni Bond ETF', 'Bond', 'USD', TRUE),
    ('EMB', 'iShares J.P. Morgan USD Emerging Markets Bond ETF', 'Bond', 'USD', TRUE)
ON CONFLICT (symbol) DO NOTHING;
COMMIT;
