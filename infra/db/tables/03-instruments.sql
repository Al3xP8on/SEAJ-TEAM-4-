-- SEAJ OLTP schema - Instruments Table
-- tradable instruments / securities

CREATE TABLE instruments (
    symbol          VARCHAR(20) PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    asset_class     VARCHAR(50) NOT NULL,
    currency        CHAR(3) NOT NULL,
    tradable        BOOLEAN NOT NULL DEFAULT TRUE,
    catalog_visible BOOLEAN NOT NULL DEFAULT TRUE
);

-- Indexes
CREATE INDEX idx_instruments_tradable ON instruments(tradable) WHERE tradable = TRUE;
CREATE INDEX idx_instruments_asset_class ON instruments(asset_class);
CREATE INDEX idx_instruments_currency ON instruments(currency);

-- Seed data - 132 instruments across multiple asset classes
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
    ('EWG', 'iShares MSCI Germany ETF', 'ETF', 'USD', TRUE),

    -- Additional US equities
    ('AMD', 'Advanced Micro Devices', 'Equity', 'USD', TRUE),
    ('AVGO', 'Broadcom Inc.', 'Equity', 'USD', TRUE),
    ('ORCL', 'Oracle Corporation', 'Equity', 'USD', TRUE),
    ('CRM', 'Salesforce Inc.', 'Equity', 'USD', TRUE),
    ('ADBE', 'Adobe Inc.', 'Equity', 'USD', TRUE),
    ('CSCO', 'Cisco Systems Inc.', 'Equity', 'USD', TRUE),
    ('INTC', 'Intel Corporation', 'Equity', 'USD', TRUE),
    ('QCOM', 'Qualcomm Inc.', 'Equity', 'USD', TRUE),
    ('TXN', 'Texas Instruments Inc.', 'Equity', 'USD', TRUE),
    ('AMAT', 'Applied Materials Inc.', 'Equity', 'USD', TRUE),
    ('MU', 'Micron Technology Inc.', 'Equity', 'USD', TRUE),
    ('LRCX', 'Lam Research Corporation', 'Equity', 'USD', TRUE),
    ('KLAC', 'KLA Corporation', 'Equity', 'USD', TRUE),
    ('ADI', 'Analog Devices Inc.', 'Equity', 'USD', TRUE),
    ('PANW', 'Palo Alto Networks Inc.', 'Equity', 'USD', TRUE),
    ('CRWD', 'CrowdStrike Holdings Inc.', 'Equity', 'USD', TRUE),
    ('NOW', 'ServiceNow Inc.', 'Equity', 'USD', TRUE),
    ('INTU', 'Intuit Inc.', 'Equity', 'USD', TRUE),
    ('NFLX', 'Netflix Inc.', 'Equity', 'USD', TRUE),
    ('DIS', 'The Walt Disney Company', 'Equity', 'USD', TRUE),
    ('UBER', 'Uber Technologies Inc.', 'Equity', 'USD', TRUE),
    ('ABNB', 'Airbnb Inc.', 'Equity', 'USD', TRUE),
    ('BKNG', 'Booking Holdings Inc.', 'Equity', 'USD', TRUE),
    ('PYPL', 'PayPal Holdings Inc.', 'Equity', 'USD', TRUE),
    ('V', 'Visa Inc.', 'Equity', 'USD', TRUE),
    ('MA', 'Mastercard Inc.', 'Equity', 'USD', TRUE),
    ('AXP', 'American Express Company', 'Equity', 'USD', TRUE),
    ('WFC', 'Wells Fargo & Company', 'Equity', 'USD', TRUE),
    ('C', 'Citigroup Inc.', 'Equity', 'USD', TRUE),
    ('MS', 'Morgan Stanley', 'Equity', 'USD', TRUE),
    ('BLK', 'BlackRock Inc.', 'Equity', 'USD', TRUE),
    ('SCHW', 'The Charles Schwab Corporation', 'Equity', 'USD', TRUE),
    ('USB', 'U.S. Bancorp', 'Equity', 'USD', TRUE),
    ('PNC', 'The PNC Financial Services Group Inc.', 'Equity', 'USD', TRUE),
    ('UNH', 'UnitedHealth Group Inc.', 'Equity', 'USD', TRUE),
    ('LLY', 'Eli Lilly and Company', 'Equity', 'USD', TRUE),
    ('ABBV', 'AbbVie Inc.', 'Equity', 'USD', TRUE),
    ('MRK', 'Merck & Co. Inc.', 'Equity', 'USD', TRUE),
    ('PFE', 'Pfizer Inc.', 'Equity', 'USD', TRUE),
    ('TMO', 'Thermo Fisher Scientific Inc.', 'Equity', 'USD', TRUE),
    ('ABT', 'Abbott Laboratories', 'Equity', 'USD', TRUE),
    ('AMGN', 'Amgen Inc.', 'Equity', 'USD', TRUE),
    ('GILD', 'Gilead Sciences Inc.', 'Equity', 'USD', TRUE),
    ('ISRG', 'Intuitive Surgical Inc.', 'Equity', 'USD', TRUE),
    ('CVS', 'CVS Health Corporation', 'Equity', 'USD', TRUE),
    ('XOM', 'Exxon Mobil Corporation', 'Equity', 'USD', TRUE),
    ('CVX', 'Chevron Corporation', 'Equity', 'USD', TRUE),
    ('COP', 'ConocoPhillips', 'Equity', 'USD', TRUE),
    ('SLB', 'SLB', 'Equity', 'USD', TRUE),
    ('CAT', 'Caterpillar Inc.', 'Equity', 'USD', TRUE),
    ('DE', 'Deere & Company', 'Equity', 'USD', TRUE),
    ('HON', 'Honeywell International Inc.', 'Equity', 'USD', TRUE),
    ('GE', 'GE Aerospace', 'Equity', 'USD', TRUE),
    ('BA', 'The Boeing Company', 'Equity', 'USD', TRUE),
    ('RTX', 'RTX Corporation', 'Equity', 'USD', TRUE),
    ('LMT', 'Lockheed Martin Corporation', 'Equity', 'USD', TRUE),
    ('NOC', 'Northrop Grumman Corporation', 'Equity', 'USD', TRUE),
    ('UPS', 'United Parcel Service Inc.', 'Equity', 'USD', TRUE),
    ('FDX', 'FedEx Corporation', 'Equity', 'USD', TRUE),
    ('HD', 'The Home Depot Inc.', 'Equity', 'USD', TRUE),
    ('LOW', 'Lowe''s Companies Inc.', 'Equity', 'USD', TRUE),
    ('COST', 'Costco Wholesale Corporation', 'Equity', 'USD', TRUE),
    ('PEP', 'PepsiCo Inc.', 'Equity', 'USD', TRUE),
    ('MCD', 'McDonald''s Corporation', 'Equity', 'USD', TRUE),
    ('SBUX', 'Starbucks Corporation', 'Equity', 'USD', TRUE),

    -- Additional exchange-traded funds
    ('IVV', 'iShares Core S&P 500 ETF', 'ETF', 'USD', TRUE),
    ('DIA', 'SPDR Dow Jones Industrial Average ETF Trust', 'ETF', 'USD', TRUE),
    ('VUG', 'Vanguard Growth ETF', 'ETF', 'USD', TRUE),
    ('VTV', 'Vanguard Value ETF', 'ETF', 'USD', TRUE),
    ('SCHD', 'Schwab U.S. Dividend Equity ETF', 'ETF', 'USD', TRUE),
    ('VYM', 'Vanguard High Dividend Yield ETF', 'ETF', 'USD', TRUE),
    ('VXUS', 'Vanguard Total International Stock ETF', 'ETF', 'USD', TRUE),
    ('VEA', 'Vanguard FTSE Developed Markets ETF', 'ETF', 'USD', TRUE),
    ('VWO', 'Vanguard FTSE Emerging Markets ETF', 'ETF', 'USD', TRUE),
    ('EWJ', 'iShares MSCI Japan ETF', 'ETF', 'USD', TRUE),
    ('BND', 'Vanguard Total Bond Market ETF', 'ETF', 'USD', TRUE),
    ('AGG', 'iShares Core U.S. Aggregate Bond ETF', 'ETF', 'USD', TRUE),
    ('TLT', 'iShares 20+ Year Treasury Bond ETF', 'ETF', 'USD', TRUE),
    ('IEF', 'iShares 7-10 Year Treasury Bond ETF', 'ETF', 'USD', TRUE),
    ('SHY', 'iShares 1-3 Year Treasury Bond ETF', 'ETF', 'USD', TRUE),
    ('LQD', 'iShares iBoxx Investment Grade Corporate Bond ETF', 'ETF', 'USD', TRUE),
    ('HYG', 'iShares iBoxx High Yield Corporate Bond ETF', 'ETF', 'USD', TRUE),
    ('TIP', 'iShares TIPS Bond ETF', 'ETF', 'USD', TRUE),
    ('VNQ', 'Vanguard Real Estate ETF', 'ETF', 'USD', TRUE),
    ('XLK', 'Technology Select Sector SPDR Fund', 'ETF', 'USD', TRUE),

    -- Additional commodity funds and trusts (Alpaca market-data connected)
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
    ('USCI', 'United States Commodity Index Fund', 'Commodity', 'USD', TRUE);
