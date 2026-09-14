from backend.data_processing.constants import(
    DEFAULT_TICKERS,
    BATCH_PAGE_SIZE
)

START_DATE = "2016-01-01"
END_DATE = "2026-12-31"
INTERVAL = "1d"

TRADING_DAYS = 252

PRICE_COLUMNS = [
    "open",
    "high",
    "low",
    "close"
]

ANALYSIS_COLUMNS = [
    "symbol",
    "date",
    "open",
    "high",
    "low",
    "close",
    "volume"
]