import pandas as pd
import yfinance as yf
import psycopg2

from typing import Optional

from backend.data_analysis.constants import (
    START_DATE,
    END_DATE,
    INTERVAL,
    BATCH_PAGE_SIZE
)

from backend.data_processing.logger import logger
from backend.data_processing.db_config import DBConfig
from dotenv import load_dotenv



class marketDataLoader:
    @staticmethod
    def download_batch(
        tickers: list[str],
        start: str = START_DATE,
        end: Optional[str] = END_DATE,
        
    ) -> pd.DataFrame:
        
        logger.info(
            f"Downloading historical data for "
            f"{len(tickers)} instruments"
        )
        
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
        return data
        
  
    @classmethod
    def download_all(
        cls,
        tickers: list[str]
    ) -> pd.DataFrame:
        
        batches = []
        
        for start in range(0, len(tickers), BATCH_PAGE_SIZE):
            batch = tickers [
                start:start + BATCH_PAGE_SIZE
            ]
            
            logger.info(
                f"Downloading batch of "
                f"{start // BATCH_PAGE_SIZE + 1}"
            )
            
            result = cls.download_batch(batch)
            
            tidy = cls.to_long_format(
                result,
                batch
            )
            batches.append(tidy)
            
        return pd.concat(batches, ignore_index=True)
    
    @staticmethod
    def to_long_format(
        data: pd.DataFrame,
        tickers: list[str]
    ) -> pd.DataFrame:
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
            
            ticker_data.columns = [
                str(col)
                .lower()
                .replace(" ", "_")
                for col in ticker_data.columns
            ]
            frames.append(ticker_data)
        
        if not frames:
            return pd.DataFrame()
        
        return pd.concat(frames, ignore_index=True)
    
    @staticmethod
    def load_instrument_metadata() -> pd.DataFrame:
        """Load instrument metadata from the database."""
        
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
                    tradeable
                FROM instruments
                ORDER BY symbol
            """
            cursor = conn.cursor()
            cursor.execute(query)
            
            rows = cursor.fetchall()
            
            columns = ["symbol", "name", "asset_class", "currency", "tradeable"]
            
            metadata = pd.DataFrame(rows, columns=columns)
            cursor.close()
            
            logger.info(
                f"Loaded metadata for "
                f"{len(metadata)} instruments"
            )
            
            return metadata
            
        except psycopg2.Error as e:
            logger.error(f"Failed to load instrument metadata: {e}")
            raise
        
        finally:
            if conn is not None:
                conn.close()
    
    @staticmethod
    def attach_instrument_metadata(
        market_data: pd.DataFrame,
        marketdata: pd.DataFrame
    ) -> pd.DataFrame:
        """Attach instrument metadata to market data."""
        
        enriched_data = market_data.merge(
            marketdata,
            how="left",
            on="symbol"
        )
        return enriched_data


if __name__ == "__main__":
    load_dotenv()
    loader = MarketDataLoader()
    df = loader.download_all(['AAPL', 'MSFT', 'GOOGL'])
    
    print("\nFirst 5 rows: ")
    print(df.head())
    
    metadata = loader.load_instrument_metadata()
    print("\nInstrument metadata: ")
    print(metadata.head())
    
    df = loader.attach_instrument_metadata(df, metadata)
    print("\nEnriched dataset: ")
    print(df.head())
    
    missing_metadata = df[
        df["asset_class"].isna()
    ]["symbol"].unique()
    
    print("\nSymbols with missing metadata: ")
    print(missing_metadata)
    
    print("\nColumns: ")
    print(df.columns.tolist())
    
    print("\nSymbols: ")
    print(df['symbol'].unique())
    
    print("\nRows per symbol: ")
    print(df.groupby('symbol').value_counts())
    
    print("\nDate range: ") 
    print(df['date'].min(), "to", df['date'].max())