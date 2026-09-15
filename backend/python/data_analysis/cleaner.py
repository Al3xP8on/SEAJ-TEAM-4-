import pandas as pd
from logger.logger import logger


class MarketDataCleaner:
    @staticmethod
    def detect_duplicates(df: pd.DataFrame) -> pd.DataFrame:
        """Identify duplicate records by symbol and date for EDA reporting"""
        duplicates = df[df.duplicated(subset=["symbol", "date"], keep=False)]
        if len(duplicates) > 0:
            logger.warning(f"Found {len(duplicates)} duplicate records")
        return duplicates
    
    @staticmethod
    def detect_missing_values(df: pd.DataFrame) -> pd.DataFrame:
        """Summarize missing values by column for EDA reporting """
        missing = pd.DataFrame({
            "missing_count": df.isna().sum(),
            "missing_percentage": (df.isna().sum() / len(df) * 100).round(2),
        })
        missing = missing[missing["missing_count"] > 0].sort_values(
            "missing_percentage",
            ascending=False
        )
        
        if len(missing) > 0:
            logger.warning(f"Found missing values in {len(missing)} columns")
            for col in missing.index:
                logger.warning(f"  {col}: {missing.loc[col, 'missing_count']} rows ({missing.loc[col, 'missing_percentage']:.2f}%)")
        
        return missing
    
    @staticmethod
    def detect_invalid_prices(df: pd.DataFrame) -> pd.DataFrame:
        """Identify rows with invalid prices (price <= 0) for EDA reporting  """
        invalid = df[
            (df["open"] <= 0) |
            (df["high"] <= 0) |
            (df["low"] <= 0) |
            (df["close"] <= 0)
        ]
        
        if len(invalid) > 0:
            logger.warning(f"Found {len(invalid)} rows with invalid prices (price <= 0)")
        
        return invalid
    
    @staticmethod
    def detect_invalid_volumes(df: pd.DataFrame) -> pd.DataFrame:
        """Identify rows with invalid volumes (volume <= 0) for EDA reporting.  """
        invalid = df[df["volume"] <= 0]
        
        if len(invalid) > 0:
            logger.warning(f"Found {len(invalid)} rows with invalid volumes (volume <= 0)")
        
        return invalid
    
    @staticmethod
    def detect_invalid_ohlc(df: pd.DataFrame) -> pd.DataFrame:
        """Identify rows with invalid OHLC relationships for EDA reporting """
        invalid = df[
            (df["high"] < df["low"]) |
            (df["open"] < df["low"]) |
            (df["close"] < df["low"]) |
            (df["open"] > df["high"]) |
            (df["close"] > df["high"])
        ]
        
        if len(invalid) > 0:
            logger.warning(f"Found {len(invalid)} rows with invalid OHLC relationships")
        
        return invalid
    
    @staticmethod
    def detect_missing_symbols(df: pd.DataFrame) -> int:
        """Identify rows with missing symbol values for EDA reporting  """
        missing_symbols = df["symbol"].isna().sum()
        
        if missing_symbols > 0:
            logger.warning(f"Found {missing_symbols} rows with missing symbols")
        
        return missing_symbols
    
    @staticmethod
    def detect_missing_dates(df: pd.DataFrame) -> int:
        """Identify rows with missing date values for EDA reporting  """
        missing_dates = df["date"].isna().sum()
        
        if missing_dates > 0:
            logger.warning(f"Found {missing_dates} rows with missing dates")
        
        return missing_dates

    
    @staticmethod
    def remove_duplicates(df: pd.DataFrame) -> pd.DataFrame:
        """Remove duplicate records by symbol and date, keeping first occurrence  """
        before_count = len(df)
        cleaned = df.drop_duplicates(subset=["symbol", "date"], keep='first')
        removed = before_count - len(cleaned)
        
        if removed > 0:
            logger.info(f"Removed {removed} duplicate records")
        
        return cleaned
    
    @staticmethod
    def remove_rows_with_missing_symbols(df: pd.DataFrame) -> pd.DataFrame:
        """Remove rows with missing symbol values  """
        before_count = len(df)
        cleaned = df.dropna(subset=["symbol"])
        removed = before_count - len(cleaned)
        
        if removed > 0:
            logger.info(f"Removed {removed} rows with missing symbols")
        
        return cleaned
    
    @staticmethod
    def remove_rows_with_missing_dates(df: pd.DataFrame) -> pd.DataFrame:
        """Remove rows with missing date values  """
        before_count = len(df)
        cleaned = df.dropna(subset=["date"])
        removed = before_count - len(cleaned)
        
        if removed > 0:
            logger.info(f"Removed {removed} rows with missing dates")
        
        return cleaned
    
    @staticmethod
    def remove_invalid_prices(df: pd.DataFrame) -> pd.DataFrame:
        """Remove rows with invalid prices (price <= 0)  """
        before_count = len(df)
        cleaned = df[
            (df["open"] > 0) &
            (df["high"] > 0) &
            (df["low"] > 0) &
            (df["close"] > 0)
        ]
        removed = before_count - len(cleaned)
        
        if removed > 0:
            logger.info(f"Removed {removed} rows with invalid prices")
        
        return cleaned
    
    @staticmethod
    def remove_invalid_volumes(df: pd.DataFrame) -> pd.DataFrame:
        """Remove rows with invalid volumes (volume <= 0)  """
        before_count = len(df)
        cleaned = df[df["volume"] > 0]
        removed = before_count - len(cleaned)
        
        if removed > 0:
            logger.info(f"Removed {removed} rows with invalid volumes")
        
        return cleaned
    
    @staticmethod
    def remove_invalid_ohlc(df: pd.DataFrame) -> pd.DataFrame:
        """Remove rows with invalid OHLC relationships.  """
        before_count = len(df)
        cleaned = df[
            (df["high"] >= df["low"]) &
            (df["open"] >= df["low"]) &
            (df["close"] >= df["low"]) &
            (df["open"] <= df["high"]) &
            (df["close"] <= df["high"])
        ]
        removed = before_count - len(cleaned)
        
        if removed > 0:
            logger.info(f"Removed {removed} rows with invalid OHLC relationships")
        
        return cleaned
    
    @staticmethod
    def remove_rows_with_missing_prices(df: pd.DataFrame) -> pd.DataFrame:
        """Remove rows with missing price columns (open, high, low, close).  """
        before_count = len(df)
        cleaned = df.dropna(subset=["open", "high", "low", "close"])
        removed = before_count - len(cleaned)
        
        if removed > 0:
            logger.info(f"Removed {removed} rows with missing price data")
        
        return cleaned
    
    @staticmethod
    def remove_rows_with_missing_volume(df: pd.DataFrame) -> pd.DataFrame:
        """Remove rows with missing volume values.  """
        before_count = len(df)
        cleaned = df.dropna(subset=["volume"])
        removed = before_count - len(cleaned)
        
        if removed > 0:
            logger.info(f"Removed {removed} rows with missing volume data")
        
        return cleaned

    
    @classmethod
    def generate_quality_report(cls, df: pd.DataFrame) -> dict:
        """Generates data quality report """
        report = {
            "total_records": len(df),
            "total_symbols": df["symbol"].nunique() if "symbol" in df.columns else 0,
            "date_range": {
                "min": str(df["date"].min()) if "date" in df.columns and not df["date"].isna().all() else "N/A",
                "max": str(df["date"].max()) if "date" in df.columns and not df["date"].isna().all() else "N/A"
            },
            "duplicates": len(cls.detect_duplicates(df)),
            "missing_symbols": cls.detect_missing_symbols(df),
            "missing_dates": cls.detect_missing_dates(df),
            "invalid_prices": len(cls.detect_invalid_prices(df)),
            "invalid_volumes": len(cls.detect_invalid_volumes(df)),
            "invalid_ohlc": len(cls.detect_invalid_ohlc(df)),
            "missing_values_summary": cls.detect_missing_values(df).to_dict()
        }
        
        logger.info("Data Quality Report")
        logger.info(f"Total records: {report['total_records']}")
        logger.info(f"Unique symbols: {report['total_symbols']}")
        logger.info(f"Issues found: {sum([report['duplicates'], report['missing_symbols'], report['missing_dates'], report['invalid_prices'], report['invalid_volumes'], report['invalid_ohlc']])}")
        
        return report

    
    @classmethod
    def clean(cls, df: pd.DataFrame, remove_invalid_ohlc: bool = True) -> pd.DataFrame:
        """Apply all safe cleaning operations to market data.  """
        
        logger.info(f"Starting data cleaning pipeline: {len(df)} records input")
        
        # Generate report before cleaning
        report_before = cls.generate_quality_report(df)
        
        # Apply cleaning operations in order
        cleaned = df.copy()
        cleaned = cls.remove_duplicates(cleaned)
        cleaned = cls.remove_rows_with_missing_symbols(cleaned)
        cleaned = cls.remove_rows_with_missing_dates(cleaned)
        cleaned = cls.remove_rows_with_missing_prices(cleaned)
        cleaned = cls.remove_rows_with_missing_volume(cleaned)
        cleaned = cls.remove_invalid_prices(cleaned)
        cleaned = cls.remove_invalid_volumes(cleaned)
        
        if remove_invalid_ohlc:
            cleaned = cls.remove_invalid_ohlc(cleaned)
        
        # Log cleaning summary
        records_removed = len(df) - len(cleaned)
        logger.info(f"Cleaning complete: {records_removed} records removed, {len(cleaned)} records remaining")
        logger.info(f"Data retention rate: {len(cleaned) / len(df) * 100:.2f}%")
        
        # Generate report after cleaning
        report_after = cls.generate_quality_report(cleaned)
        
        return cleaned
    