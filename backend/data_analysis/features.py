import pandas as pd
import numpy as np
from backend.data_processing.logger import logger


class MarketFeatureEngineer:
    """Creates derived financial variables from cleaned market data""" 
    
    @staticmethod
    def _validate_input(df: pd.DataFrame, required_columns: list) -> None:
        if df is None or df.empty:
            raise ValueError("Input DataFrame is None or empty")
        
        missing = set(required_columns) - set(df.columns)
        if missing:
            raise ValueError(f"Missing required columns: {missing}")
        
        if "symbol" not in df.columns or "date" not in df.columns:
            raise ValueError("DataFrame must contain 'symbol' and 'date' columns")
    
    @staticmethod
    def add_returns(df: pd.DataFrame) -> pd.DataFrame:
        required = ["symbol", "date", "close"]
        MarketFeatureEngineer._validate_input(df, required)
        
        result = df.copy()
        
        # Ensure data is sorted by symbol and date
        result = result.sort_values(["symbol", "date"])
        
        # Calculate daily returns by symbol
        result["daily_return"] = result.groupby("symbol")["close"].pct_change()
        
        logger.info("Calculated daily_return for all symbols")
        return result
    
    @staticmethod
    def add_cumulative_return(df: pd.DataFrame) -> pd.DataFrame:
        required = ["symbol", "daily_return"]
        MarketFeatureEngineer._validate_input(df, required)
        
        result = df.copy()
        
        # Calculate cumulative returns by symbol
        result["cumulative_return"] = result.groupby("symbol")["daily_return"].cumsum()
        
        logger.info("Calculated cumulative_return for all symbols")
        return result
    
    @staticmethod
    def add_rolling_volatility(df: pd.DataFrame, window: int = 30) -> pd.DataFrame:
        required = ["symbol", "daily_return"]
        MarketFeatureEngineer._validate_input(df, required)
        
        if window < 2:
            raise ValueError(f"Window size must be >= 2, got {window}")
        
        result = df.copy()
        
        # Calculate rolling volatility by symbol
        result["rolling_volatility"] = (
            result.groupby("symbol")["daily_return"]
            .rolling(window=window, min_periods=1)
            .std()
            .reset_index(level=0, drop=True)
        )
        
        logger.info(f"Calculated rolling_volatility (window={window}) for all symbols")
        return result
    
    @staticmethod
    def add_drawdown(df: pd.DataFrame) -> pd.DataFrame:
        required = ["symbol", "close"]
        MarketFeatureEngineer._validate_input(df, required)
        
        result = df.copy()
        
        # Calculate running maximum by symbol
        running_max = result.groupby("symbol")["close"].cummax()
        
        # Drawdown: (current_price - running_max) / running_max
        result["drawdown"] = (result["close"] / running_max) - 1
        
        logger.info("Calculated drawdown for all symbols")
        return result
    
    @staticmethod
    def add_price_range(df: pd.DataFrame) -> pd.DataFrame:
        required = ["symbol", "high", "low", "close"]
        MarketFeatureEngineer._validate_input(df, required)
        
        result = df.copy()
        
        result["price_range"] = (result["high"] - result["low"]) / result["close"]
        
        logger.info("Calculated price_range for all symbols")
        return result
    
    @staticmethod
    def add_volume_ma(df: pd.DataFrame, window: int = 20) -> pd.DataFrame:
        """Calculate moving average of volume."""
        required = ["symbol", "volume"]
        MarketFeatureEngineer._validate_input(df, required)
        
        if window < 2:
            raise ValueError(f"Window size must be >= 2, got {window}")
        
        result = df.copy()
        
        result["volume_ma"] = (
            result.groupby("symbol")["volume"]
            .rolling(window=window, min_periods=1)
            .mean()
            .reset_index(level=0, drop=True)
        )
        
        logger.info(f"Calculated volume_ma (window={window}) for all symbols")
        return result
    
    @staticmethod
    def add_log_returns(df: pd.DataFrame) -> pd.DataFrame:
        required = ["symbol", "close"]
        MarketFeatureEngineer._validate_input(df, required)
        
        result = df.copy()
        result = result.sort_values(["symbol", "date"])
        
        result["log_return"] = result.groupby("symbol")["close"].transform(
            lambda prices: np.log(prices / prices.shift(1))
        )
        
        logger.info("Calculated log_return for all symbols")
        return result
    
    @classmethod
    def add_all_features(cls, df: pd.DataFrame, volatility_window: int = 30, volume_window: int = 20) -> pd.DataFrame:
        logger.info("Starting feature engineering pipeline")
        
        result = df.copy()
        result = cls.add_returns(result)
        result = cls.add_cumulative_return(result)
        result = cls.add_log_returns(result)
        result = cls.add_rolling_volatility(result, window=volatility_window)
        result = cls.add_drawdown(result)
        result = cls.add_price_range(result)
        result = cls.add_volume_ma(result, window=volume_window)
        
        logger.info(f"Feature engineering complete: {result.shape[1]} columns, {result.shape[0]} rows")
        return result