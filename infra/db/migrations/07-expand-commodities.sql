-- Expand commodity exposure through Alpaca-connected, USD-listed funds/trusts.
-- These are exchange-traded securities, not spot commodity or futures contracts.
-- Additive and safe to rerun; existing records and portfolio relationships stay intact.
BEGIN;
INSERT INTO instruments (symbol, name, asset_class, currency, tradable) VALUES
    ('SLV', 'iShares Silver Trust', 'Commodity', 'USD', TRUE),
    ('IAU', 'iShares Gold Trust', 'Commodity', 'USD', TRUE),
    ('PPLT', 'abrdn Physical Platinum Shares ETF', 'Commodity', 'USD', TRUE),
    ('PALL', 'abrdn Physical Palladium Shares ETF', 'Commodity', 'USD', TRUE),
    ('CPER', 'United States Copper Index Fund', 'Commodity', 'USD', TRUE),
    ('UNG', 'United States Natural Gas Fund, LP Unit', 'Commodity', 'USD', TRUE),
    ('BNO', 'United States Brent Oil Fund, LP', 'Commodity', 'USD', TRUE),
    ('DBA', 'Invesco DB Agriculture Fund', 'Commodity', 'USD', TRUE),
    ('PDBC', 'Invesco Optimum Yield Diversified Commodity Strategy No K-1 ETF', 'Commodity', 'USD', TRUE),
    ('COMT', 'iShares GSCI Commodity Dynamic Roll Strategy ETF', 'Commodity', 'USD', TRUE),
    ('GSG', 'iShares S&P GSCI Commodity-Indexed Trust', 'Commodity', 'USD', TRUE),
    ('USCI', 'United States Commodity Index Fund', 'Commodity', 'USD', TRUE)
ON CONFLICT (symbol) DO NOTHING;
COMMIT;
