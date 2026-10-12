-- SEAJ OLTP schema - Instruments Table
-- tradable instruments / securities

CREATE TABLE instruments (
    symbol          VARCHAR(20) PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    asset_class     VARCHAR(50) NOT NULL,
    currency        CHAR(3) NOT NULL,
    tradable        BOOLEAN NOT NULL DEFAULT TRUE
);

-- Indexes
CREATE INDEX idx_instruments_tradable ON instruments(tradable) WHERE tradable = TRUE;
CREATE INDEX idx_instruments_asset_class ON instruments(asset_class);
CREATE INDEX idx_instruments_currency ON instruments(currency);

-- Seed data - 35 instruments across multiple asset classes
INSERT INTO instruments (symbol, name, asset_class, currency, tradable) VALUES
    -- Original instruments
    ('GLBEQ1', 'Global Equity Index Fund', 'Equity', 'GBP', TRUE),
    ('CORPB1', 'Sterling Corporate Bond Fund', 'Bond', 'GBP', TRUE),
    ('GILT10', 'UK 10-Year Gilt', 'Bond', 'GBP', TRUE),
    ('CASHGBP', 'Cash (GBP)', 'Cash', 'GBP', FALSE),
    ('AAPL', 'Apple Inc.', 'Equity', 'USD', TRUE),
    ('MSFT', 'Microsoft Corporation', 'Equity', 'USD', TRUE),
    ('UST10', 'US 10-Year Treasury Note', 'Bond', 'USD', TRUE),
    ('CASHUSD', 'Cash (USD)', 'Cash', 'USD', FALSE),
    
    -- US Tech Giants
    ('GOOGL', 'Alphabet Inc.', 'Equity', 'USD', TRUE),
    ('AMZN', 'Amazon.com Inc.', 'Equity', 'USD', TRUE),
    ('NVDA', 'NVIDIA Corporation', 'Equity', 'USD', TRUE),
    ('META', 'Meta Platforms Inc.', 'Equity', 'USD', TRUE),
    ('TSLA', 'Tesla Inc.', 'Equity', 'USD', TRUE),
    
    -- US Financial & Industrials
    ('JPM', 'JPMorgan Chase & Co.', 'Equity', 'USD', TRUE),
    ('BAC', 'Bank of America Corp', 'Equity', 'USD', TRUE),
    ('GS', 'The Goldman Sachs Group', 'Equity', 'USD', TRUE),
    ('JNJ', 'Johnson & Johnson', 'Equity', 'USD', TRUE),
    ('PG', 'Procter & Gamble Co.', 'Equity', 'USD', TRUE),
    ('WMT', 'Walmart Inc.', 'Equity', 'USD', TRUE),
    ('KO', 'The Coca-Cola Company', 'Equity', 'USD', TRUE),
    
    -- US Fixed Income
    ('UST2', 'US 2-Year Treasury Note', 'Bond', 'USD', TRUE),
    ('UST5', 'US 5-Year Treasury Note', 'Bond', 'USD', TRUE),
    ('UST30', 'US 30-Year Treasury Bond', 'Bond', 'USD', TRUE),
    ('CORP', 'US Corporate Bond ETF', 'Bond', 'USD', TRUE),
    
    -- ETFs & Index Funds
    ('SPY', 'SPDR S&P 500 ETF', 'ETF', 'USD', TRUE),
    ('VOO', 'Vanguard S&P 500 ETF', 'ETF', 'USD', TRUE),
    ('VTI', 'Vanguard Total Stock Market ETF', 'ETF', 'USD', TRUE),
    ('QQQ', 'Invesco QQQ Trust Nasdaq 100', 'ETF', 'USD', TRUE),
    ('IWM', 'iShares Russell 2000 ETF', 'ETF', 'USD', TRUE),
    ('EEM', 'iShares MSCI Emerging Markets ETF', 'ETF', 'USD', TRUE),
    
    -- Commodities & Alternatives
    ('GLD', 'SPDR Gold Shares', 'Commodity', 'USD', TRUE),
    ('USO', 'United States Oil Fund', 'Commodity', 'USD', TRUE),
    ('DBC', 'Commodities ETF', 'Commodity', 'USD', TRUE),
    
    -- International
    ('EWU', 'iShares MSCI United Kingdom ETF', 'ETF', 'USD', TRUE),
    ('EWG', 'iShares MSCI Germany ETF', 'ETF', 'USD', TRUE);
