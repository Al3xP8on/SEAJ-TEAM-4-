import pandas as pd
import yfinance as yf
from typing import Optional

from backend.data_analysis.constants import (
    START_DATE,
    END_DATE,
    INTERVAL,
    BATCH_PAGE_SIZE
)

from backend.data_processing.logger import logger


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
        

if __name__ == "__main__":
    loader = marketDataLoader()
    df = loader.download_all(['AAPL', 'MSFT', 'GOOGL'])
    
    print("\nFirst 5 rows: ")
    print(df.head())
    
    print("\nDataset shape: ")
    print(df.shape)
    
    print("\nSymbols: ")
    print(df['symbol'].unique())
    
    print("\nRows per symbol: ")
    print(df.groupby('symbol').value_counts())
    
    print("\nDate range: ") 
    print(df['date'].min(), "to", df['date'].max())