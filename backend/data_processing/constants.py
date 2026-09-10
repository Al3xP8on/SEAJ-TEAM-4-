"""Constants for instrument data processing."""

INSTRUMENTS_TABLE = 'instruments'
INSTRUMENTS_COLUMNS = ('symbol', 'name', 'asset_class', 'currency', 'tradable')

SELECT_EXISTING_SYMBOLS = "SELECT symbol FROM instruments;"

INSERT_INSTRUMENTS_SQL = (
    "INSERT INTO instruments (symbol, name, asset_class, currency, tradable) "
    "VALUES (%s, %s, %s, %s, %s) "
    "ON CONFLICT (symbol) DO NOTHING;"
)

ASSET_CLASS_MAP = {
    'EQUITY': 'Equity',
    'ETF': 'ETF',
    'MUTUALFUND': 'Mutual Fund'
}

BATCH_PAGE_SIZE = 100

# S&P 500 based tickers.
DEFAULT_TICKERS = [
    'AAPL', 'MSFT', 'GOOGL', 'AMZN', 'NVDA', 'META', 'TSLA', 
    'JPM', 'BAC', 'WFC', 'GS', 'BLK', 'AXP', 'MA', 'V',  
    'JNJ', 'PG', 'KO', 'PEP', 'MCD', 'WMT', 'HD', 'LOW', 
    'XOM', 'CVX', 'COP', 
    'UNH', 'LLY', 'PFE', 
    'BA', 'LMT', 'RTX', 'NOC', 
    'NFLX', 'DIS', 
    'VOO', 'VTI', 'SPY', 'QQQ', 'IVV', 'BND', 'LQD', 'VNQ', 
    'EWJ', 'EWG', 'GLD', 'USO'
]