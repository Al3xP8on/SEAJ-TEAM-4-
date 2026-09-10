import yfinance as yf
import psycopg2
from typing import List, Dict, Tuple, Optional
from psycopg2.extras import execute_batch
from psycopg2.extensions import connection

from backend.data_processing.db_config import DBConfig
from backend.data_processing.logger import logger
from backend.data_processing.constants import (
    SELECT_EXISTING_SYMBOLS,
    INSERT_INSTRUMENTS_SQL,
    ASSET_CLASS_MAP,
    BATCH_PAGE_SIZE,
    DEFAULT_TICKERS
)


class InstrumentDataProcessor:
    """Fetches financial instrument data from yfinance and inserts into database."""
    
    def __init__(self, db_config: DBConfig):
        """
        Initialize processor with database configuration.
        
        Args:
            db_config: DBConfig instance with connection parameters
        """
        self.db_config = db_config
        self.conn: Optional[connection] = None
    
    def connect(self) -> None:
        """Establish connection to PostgreSQL database."""
        try:
            self.conn = psycopg2.connect(
                host=self.db_config.host,
                database=self.db_config.database,
                user=self.db_config.user,
                password=self.db_config.password,
                port=self.db_config.port
            )
            logger.info("[OK] Connected to PostgreSQL database")
        except psycopg2.Error as e:
            logger.error(f"[FAIL] Database connection failed: {e}")
            raise
    
    def disconnect(self) -> None:
        """Close database connection."""
        if self.conn:
            self.conn.close()
            logger.info("[OK] Database connection closed")
    
    @staticmethod
    def _extract_raw_info(ticker: str) -> Dict | None:
        """
        Args:
            ticker: Stock/ETF/Fund ticker symbol

        Returns:
            Raw yfinance info dict, or None if the fetch failed.
        """
        try:
            return yf.Ticker(ticker).info
        except Exception as e:
            logger.warning(f"[SKIP] Error fetching {ticker}: {str(e)}")
            return None

    @staticmethod
    def _transform_instrument(ticker: str, info: Dict) -> Dict | None:
        """
        Args:
            ticker: Stock/ETF/Fund ticker symbol
            info: Raw yfinance info dict

        Returns:
            Dict with keys: symbol, name, asset_class, currency, tradable
            Returns None if the data is incomplete or the asset class is unsupported.
        """
        if not info or 'longName' not in info or 'currency' not in info:
            logger.warning(f"[SKIP] Incomplete data for {ticker}, skipping")
            return None

        # Only accept stocks, ETFs and mutual funds
        quote_type = info.get('quoteType', 'EQUITY')
        if quote_type not in ASSET_CLASS_MAP:
            logger.warning(f"[SKIP] Unsupported asset class '{quote_type}' for {ticker}, skipping")
            return None

        return {
            'symbol': ticker,
            'name': info['longName'][:255],  # Cap at VARCHAR(255)
            'asset_class': ASSET_CLASS_MAP[quote_type],
            'currency': info.get('currency', 'USD')[:3],  # Ensure CHAR(3)
            'tradable': True # yfinance doesn't have an explicit "tradable" field - may change in the future for a given ticker.
        }

    @staticmethod
    def _build_insert_rows(instruments: List[Dict], existing_symbols: set) -> List[Tuple]:
        """
        Turn instrument dicts into insert tuples & exclude existing symbols.

        Args:
            instruments: List of instrument data dicts
            existing_symbols: Symbols already present in the database

        Returns:
            List of (symbol, name, asset_class, currency, tradable) tuples to insert
        """
        return [
            (i['symbol'], i['name'], i['asset_class'], i['currency'], i['tradable'])
            for i in instruments
            if i['symbol'] not in existing_symbols
        ]
        
    def fetch_instrument_data(self, ticker: str) -> Dict | None:
        """
        Fetch and transform instrument data from yfinance for a given ticker.

        Args:
            ticker: Stock/ETF/Fund ticker symbol

        Returns:
            Dict with keys: symbol, name, asset_class, currency, tradable
            Returns None if data fetch fails or invalid
        """
        info = self._extract_raw_info(ticker)
        if info is None:
            return None
        return self._transform_instrument(ticker, info)
    
    def fetch_all_instruments(self, tickers: List[str]) -> List[Dict]:
        """
        Fetch instrument data for multiple tickers.

        Args:
            tickers: List of ticker symbols
            
        Returns:
            List of successfully fetched instrument data dicts
        """
        instruments = []
        total = len(tickers)
        
        for idx, ticker in enumerate(tickers, 1):
            logger.info(f"[{idx}/{total}] Fetching {ticker}...")
            data = self.fetch_instrument_data(ticker)
            
            if data:
                instruments.append(data)
                logger.info(f"  - {data['name']} ({data['asset_class']}, {data['currency']})")
        
        logger.info(f"\n[OK] Successfully fetched {len(instruments)}/{total} instruments")
        return instruments
    
    def get_existing_symbols(self) -> set:
        """Fetch existing symbols from instruments table to avoid duplicates."""
        assert self.conn is not None, "Database connection not established"
        try:
            cur = self.conn.cursor()
            cur.execute(SELECT_EXISTING_SYMBOLS)
            existing = {row[0] for row in cur.fetchall()}
            cur.close()
            return existing
        except psycopg2.Error as e:
            logger.error(f"[FAIL] Failed to query existing symbols: {e}")
            return set()

    def insert_instruments(self, instruments: List[Dict]) -> Tuple[int, int]:
        """
        Insert instruments into database, & skip existing symbols.

        Args:
            instruments: List of instrument data dicts

        Returns:
            Tuple (inserted_count, skipped_count)
        """
        assert self.conn is not None, "Database connection not established"
        if not instruments:
            logger.warning("No instruments to insert")
            return 0, 0

        existing_symbols = self.get_existing_symbols()
        to_insert = self._build_insert_rows(instruments, existing_symbols)

        if not to_insert:
            logger.info("All instruments already exist in database")
            return 0, len(instruments)
        
        try:
            cur = self.conn.cursor()
            execute_batch(cur, INSERT_INSTRUMENTS_SQL, to_insert, page_size=BATCH_PAGE_SIZE)
            self.conn.commit()
            inserted = len(to_insert)
            skipped = len(instruments) - inserted
            
            logger.info(f"[OK] Inserted {inserted} new instruments, {skipped} already existed")
            cur.close()
            return inserted, skipped

        except psycopg2.Error as e:
            self.conn.rollback()
            logger.error(f"[FAIL] Insert failed: {e}")
            raise
    
    def populate_instruments(self, tickers: Optional[List[str]] = None) -> None:
        """
        Main orchestration method: fetch and insert instruments.
        
        Args:
            tickers: Optional custom ticker list (defaults to DEFAULT_TICKERS)
        """
        if tickers is None:
            tickers = DEFAULT_TICKERS

        try:
            self.connect()
            existing_symbols = self.get_existing_symbols()
            new_tickers = [t for t in tickers if t not in existing_symbols]
            if len(new_tickers) < len(tickers):
                logger.info(f"[SKIP] {len(tickers) - len(new_tickers)} ticker(s) already in database, skipping fetch")

            instruments = self.fetch_all_instruments(new_tickers)
            inserted, skipped = self.insert_instruments(instruments)
            logger.info(f"\n{'-'*50}")
            logger.info(f"Summary: {inserted} inserted, {skipped} skipped")
            logger.info(f"{'-'*50}")
        
        finally:
            self.disconnect()
