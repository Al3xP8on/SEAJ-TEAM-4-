"""Constants for instrument data processing."""

# Database table and column names
INSTRUMENTS_TABLE = 'instruments'
INSTRUMENTS_COLUMNS = ('symbol', 'name', 'asset_class', 'currency', 'tradable')

# SQL Queries
SELECT_EXISTING_SYMBOLS = "SELECT symbol FROM instruments;"

INSERT_INSTRUMENTS_SQL = (
    "INSERT INTO instruments (symbol, name, asset_class, currency, tradable) "
    "VALUES (%s, %s, %s, %s, %s) "
    "ON CONFLICT (symbol) DO NOTHING;"
)

# yfinance asset classes accepted for ingestion (stocks, ETFs, mutual funds only)
ASSET_CLASS_MAP = {
    'EQUITY': 'Equity',
    'ETF': 'ETF',
    'MUTUALFUND': 'Mutual Fund'
}

# Batch insert page size
BATCH_PAGE_SIZE = 100

# Default S&P 500 and diversified tickers (stocks, ETFs, mutual funds)
DEFAULT_TICKERS = [
    'AAPL', 'MSFT', 'GOOGL', 'AMZN', 'NVDA', 'META', 'TSLA',  # Large-cap tech
    'JPM', 'BAC', 'WFC', 'GS', 'BLK', 'AXP', 'MA', 'V',  # Large-cap financials & industrials
    'JNJ', 'PG', 'KO', 'PEP', 'MCD', 'WMT', 'HD', 'LOW',  # Dividend aristocrats & stable
    'XOM', 'CVX', 'COP',  # Energy
    'UNH', 'LLY', 'PFE',  # Healthcare
    'BA', 'LMT', 'RTX', 'NOC',  # Defense/Aerospace
    'NFLX', 'DIS',  # Media/Entertainment
    'VOO', 'VTI', 'SPY', 'QQQ', 'IVV', 'BND', 'LQD', 'VNQ',  # ETFs for diversification
    'EWJ', 'EWG', 'GLD', 'USO'  # International & alternatives
]