import pandas as pd
import yfinance as yf
import psycopg2

from typing import Optional

from data_analysis.constants import (
    START_DATE,
    END_DATE,
    INTERVAL,
    BATCH_PAGE_SIZE,
    MARKET_COLUMNS,
    METADATA_COLUMNS,
)

from logger.logger import logger
from data_processing.db_config import DBConfig
from dotenv import load_dotenv

class MarketDataLoader:
    """Handles downloading market data from Yahoo Finance and loading metadata from PostgreSQL."""
    
    @staticmethod
    def download_batch(
        tickers: list[str],
        start: str = START_DATE,
        end: Optional[str] = END_DATE,
    ) -> pd.DataFrame:
        """Download raw market data from Yahoo Finance for a batch of tickers."""
        if not tickers or len(tickers) == 0:
            logger.warning("Empty ticker list provided to download_batch()")
            return pd.DataFrame()
        
        logger.info(
            f"Downloading historical data for "
            f"{len(tickers)} instruments from {start} to {end}"
        )
        
        try:
            data = yf.download(
                tickers=tickers,
                start=start,
                end=end,
                interval=INTERVAL,
                group_by='ticker',
                threads=True,
                auto_adjust=True,
                progress=False
            )
        except Exception as e:
            logger.error(f"Failed to download historical data: {e}")
            return pd.DataFrame()
  
        assert data is not None
        if data.empty:
            logger.warning(f"No historical data downloaded for tickers: {tickers}")
            return pd.DataFrame()
        
        logger.info(f"Successfully downloaded data for batch of {len(tickers)} tickers")
        return data
  
    @classmethod
    def download_all(
        cls,
        tickers: list[str]
    ) -> pd.DataFrame:
        if not tickers or len(tickers) == 0:
            logger.warning("Empty ticker list provided to download_all()")
            return pd.DataFrame()
        
        batches = []
        
        for start in range(0, len(tickers), BATCH_PAGE_SIZE):
            batch = tickers[start:start + BATCH_PAGE_SIZE]
            if not batch:
                continue
            
            logger.info(
                f"Downloading batch {start // BATCH_PAGE_SIZE + 1} "
                f"with {len(batch)} tickers"
            )
            
            result = cls.download_batch(batch)
            
            # Skip batch if no data returned
            if result.empty:
                logger.warning(f"Batch {start // BATCH_PAGE_SIZE + 1} returned no data, skipping")
                continue
            
            tidy = cls.to_long_format(result, batch)

            if not tidy.empty:
                batches.append(tidy)
   
        if not batches:
            logger.error("No valid market data retrieved from any batch")
            return pd.DataFrame()
        
        combined_data = pd.concat(batches, ignore_index=True)
        logger.info(f"Successfully downloaded data for {len(combined_data)} records across all batches")
        return combined_data
    
    @staticmethod
    def to_long_format(
        data: pd.DataFrame,
        tickers: list[str]
    ) -> pd.DataFrame:
        """Convert wide format Yahoo Finance data to long format per ticker  """
        frames = []
        
        for ticker in tickers:
            try:
                ticker_data = data[ticker].copy()
            except KeyError:
                logger.warning(
                    f"No historical data found for ticker: {ticker}"
                )
                continue
            
            ticker_data = ticker_data.reset_index()
            ticker_data['symbol'] = ticker
            
            # Standardise column names to lowercase with underscores
            ticker_data.columns = [
                str(col)
                .lower()
                .replace(" ", "_")
                for col in ticker_data.columns
            ]
            
            if 'date' in ticker_data.columns:
                ticker_data['date'] = pd.to_datetime(ticker_data['date'])
            
            frames.append(ticker_data)
        
        if not frames:
            logger.warning(f"No valid data extracted for tickers: {tickers}")
            return pd.DataFrame()
        
        long_format = pd.concat(frames, ignore_index=True)
        available_columns = [col for col in MARKET_COLUMNS if col in long_format.columns]
        result = long_format[available_columns]
        
        logger.info(f"Converted to long format: {len(result)} records, {len(available_columns)} columns")
        return result
    
    @staticmethod
    def load_instrument_metadata() -> pd.DataFrame:
        config = DBConfig.from_env()
        conn = None
        
        try:
            conn = psycopg2.connect(
                database=config.database,
                user=config.user,
                password=config.password,
                host=config.host,
                port=config.port
            )
            query = """
                SELECT 
                    symbol,
                    name,
                    asset_class,
                    currency,
                    tradable
                FROM instruments
                ORDER BY symbol
            """
            cursor = conn.cursor()
            cursor.execute(query)
            
            rows = cursor.fetchall()
            metadata = pd.DataFrame(rows, columns=METADATA_COLUMNS)
            cursor.close()
        
            return metadata
            
        except psycopg2.Error as e:
            logger.error(f"Failed to load instrument metadata: {e}")
            return pd.DataFrame()
        
        finally:
            if conn is not None:
                conn.close()
    
    @staticmethod
    def attach_instrument_metadata(
        market_data: pd.DataFrame,
        metadata: pd.DataFrame
    ) -> pd.DataFrame:
        """Attach instrument metadata to market data with many-to-one validation  """
        if market_data.empty or metadata.empty:
            logger.warning("Cannot attach metadata: market_data or metadata is empty")
            return market_data
        
        symbol_counts = metadata.groupby('symbol').size()
        duplicate_symbols = symbol_counts[symbol_counts > 1]
        
        if not duplicate_symbols.empty:
            logger.error(f"Duplicate symbols in metadata (many-to-one violation): {duplicate_symbols.to_dict()}")
            logger.warning("Using only first occurrence per symbol to maintain data integrity")
            metadata = metadata.drop_duplicates(subset=['symbol'], keep='first')
        
        enriched_data = market_data.merge(
            metadata,
            how="left",
            on="symbol"
        )
        
        logger.info(f"Attached metadata to {len(enriched_data)} market data records")
        return enriched_data
    
    @classmethod
    def load_analysis_data(
        cls,
        tickers: list[str],
        start: str = START_DATE,
        end: Optional[str] = END_DATE
    ) -> pd.DataFrame:
        """Single entry point for loading complete EDA dataset"""
        
        logger.info(f"Starting data load for {len(tickers)} tickers")
        
        # Download raw market data from Yahoo Finance
        market_data = cls.download_all(tickers)
        
        if market_data.empty:
            logger.error("No market data retrieved")
            return pd.DataFrame()
    
        metadata = cls.load_instrument_metadata()
        
        if metadata.empty:
            logger.warning("No metadata retrieved from PostgreSQL, returning market data only")
            return market_data
        
        # Join market data with instrument metadata
        result = cls.attach_instrument_metadata(market_data, metadata)
        
        # Sort by symbol and date for consistency
        result = result.sort_values(by=['symbol', 'date']).reset_index(drop=True)
        
        logger.info(f"Data load complete: {len(result)} records, {len(result.columns)} columns")
        logger.info(f"Symbols: {result['symbol'].nunique()}, Date range: {result['date'].min()} to {result['date'].max()}")
        
        return result


if __name__ == "__main__":
    load_dotenv()
    loader = MarketDataLoader()
    df = loader.load_analysis_data(['AAPL', 'MSFT', 'GOOGL'])
    
    if not df.empty:
        print("\nFirst 5 rows: ")
        print(df.head())
        
        print("\nDataset shape: ")
        print(df.shape)
        
        print("\nColumns: ")
        print(df.columns.tolist())
        
        print("\nSymbols: ")
        print(df['symbol'].unique())
        
        print("\nRows per symbol: ")
        print(df.groupby('symbol').size())
        
        print("\nDate range: ") 
        print(f"{df['date'].min()} to {df['date'].max()}")
    else:
        print("No data loaded successfully")