from data_processing.constants import(
    DEFAULT_TICKERS,
    BATCH_PAGE_SIZE
)

START_DATE = "2016-01-01"
END_DATE = None
INTERVAL = "1d"

TRADING_DAYS = 252
ROLLING_WINDOW = 30

PRICE_COLUMNS = [
    "open",
    "high",
    "low",
    "close"
]

MARKET_COLUMNS = [
    "symbol",
    "date",
    "open",
    "high",
    "low",
    "close",
    "volume"
]

METADATA_COLUMNS = [
    "symbol",
    "name",
    "asset_class",
    "currency",
    "tradable"
]

FEATURE_COLUMNS = [
    "daily_return",
    "cumulative_return",
    "normalised_price",
    "rolling_volatility",
    "drawdown"
]

ANALYSIS_COLUMNS = [
    "symbol",
    "name",
    "asset_class",
    "currency",
    "tradable",
    "date",
    "open",
    "high",
    "low",
    "close",
    "volume",
    "daily_return",
    "cumulative_return",
    "normalised_price",
    "rolling_volatility",
    "drawdown"
]