import pandas as pd

class MarketDataCleaner:
    @staticmethod
    def remove_duplicates(
        df: pd.DataFrame
    ) -> pd.DataFrame:
        
        return df.drop_duplicates(
            subset=["symbol", "date"]
        )
        
    # summary of missing values
    @staticmethod
    def missing_summary(
        df: pd.DataFrame
    ) -> pd.DataFrame:
        
        missing = pd.DataFrame({
            "missing_count": df.isna().sum(),
            "missing_percentage": df.isna().mean() * 100,
        })
        
        return missing.sort_values(
            "missing_percentage",
            ascending=False
        )
    
    # invalid prices
    @staticmethod
    def invalid_prices(
        df: pd.DataFrame
    ) -> pd.DataFrame:
        
        return df[
            (df["open"] <= 0) |
            (df["high"] <= 0) |
            (df["low"] <= 0) |
            (df["close"] <= 0)
        ]
    
    # invalid volumes
    @staticmethod
    def invalid_volumes(
        df: pd.DataFrame
    ) -> pd.DataFrame:
        
        return df[
            (df["volume"] <= 0)
        ]
    
    #OHLC Validation
    @staticmethod
    def invalid_ohlc(
        df: pd.DataFrame
    ) -> pd.DataFrame:
        
        return df[
            (df["high"] < df["low"]) |
            (df["open"] < df["low"]) |
            (df["close"] < df["low"]) |
            (df["open"] > df["high"]) |
            (df["close"] > df["high"])
        ]
    