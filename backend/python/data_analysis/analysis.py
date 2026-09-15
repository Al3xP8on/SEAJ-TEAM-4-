import numpy as np
import pandas as pd
from backend.python.data_analysis.constants import TRADING_DAYS, START_DATE, END_DATE
from backend.python.data_processing.logger import logger


def instrument_summary(df: pd.DataFrame) -> pd.DataFrame:
    """Generate summary statistics for each instrument """
    if df.empty:
        logger.warning("Empty DataFrame passed to instrument_summary()")
        return pd.DataFrame()
    
    summary = (
        df.groupby(["symbol", "name", "asset_class"])
        .agg(
            observations=("date", "count"),
            date_min=("date", "min"),
            date_max=("date", "max"),
            avg_close=("close", "mean"),
            median_close=("close", "median"),
            avg_volume=("volume", "mean"),
            median_volume=("volume", "median"),
            daily_mean_return=("daily_return", "mean"),
            daily_std_return=("daily_return", "std"),
            final_cumulative_return=("cumulative_return", "last"),
            max_drawdown=("drawdown", "min"),
            avg_drawdown=("drawdown", "mean")
        )
        .reset_index()
    )
    
    # Calculate data completeness
    summary["date_range_days"] = (summary["date_max"] - summary["date_min"]).dt.days
    summary["expected_observations"] = summary["date_range_days"] + 1  # Approximation
    summary["data_completeness_pct"] = (summary["observations"] / summary["expected_observations"] * 100).round(2)
    
    logger.info(f"Instrument summary: {len(summary)} instruments analyzed")
    return summary


def risk_return_summary(df: pd.DataFrame) -> pd.DataFrame:
    """Calculate daily and annualized returns and volatility for each instrument   """
    if df.empty:
        logger.warning("Empty DataFrame passed to risk_return_summary()")
        return pd.DataFrame()
    
    summary = (
        df.groupby(["symbol", "asset_class"])
        ["daily_return"]
        .agg(
            daily_mean=("daily_return", "mean"),
            daily_std=("daily_return", "std")
        )
        .reset_index()
    )
    
    # Annualize returns and volatility using trading days constant
    summary["annual_return"] = summary["daily_mean"] * TRADING_DAYS
    summary["annual_volatility"] = summary["daily_std"] * np.sqrt(TRADING_DAYS)
    
    logger.info(f"Risk-return summary: {len(summary)} instruments")
    return summary


def asset_class_summary(risk_return: pd.DataFrame) -> pd.DataFrame:
    """Summarize returns and volatility by asset class """
    
    if risk_return.empty:
        logger.warning("Empty DataFrame passed to asset_class_summary()")
        return pd.DataFrame()
    
    summary = (
        risk_return.groupby("asset_class")
        .agg(
            avg_annual_return=("annual_return", "mean"),
            median_annual_return=("annual_return", "median"),
            avg_annual_volatility=("annual_volatility", "mean"),
            median_annual_volatility=("annual_volatility", "median"),
            instrument_count=("symbol", "nunique")
        )
        .reset_index()
    )
    
    # Add descriptive labels
    summary["return_label"] = summary["avg_annual_return"].apply(
        lambda x: "High" if x > 0.15 else ("Moderate" if x > 0.05 else ("Low" if x > 0 else "Negative"))
    )
    summary["risk_label"] = summary["avg_annual_volatility"].apply(
        lambda x: "High" if x > 0.25 else ("Moderate" if x > 0.12 else "Low")
    )
    
    logger.info(f"Asset class summary: {len(summary)} asset classes")
    return summary


def return_distribution_analysis(df: pd.DataFrame) -> pd.DataFrame:
    """Analyze distribution of daily returns by symbol and asset class """
    
    if df.empty or "daily_return" not in df.columns:
        logger.warning("Cannot perform return distribution analysis: missing data")
        return pd.DataFrame()
    
    analysis = (
        df.groupby(["symbol", "asset_class"])
        ["daily_return"]
        .agg([
            ("count", "count"),
            ("mean", "mean"),
            ("std", "std"),
            ("min", "min"),
            ("p25", lambda x: x.quantile(0.25)),
            ("p50", lambda x: x.quantile(0.50)),
            ("p75", lambda x: x.quantile(0.75)),
            ("max", "max"),
            ("skewness", lambda x: x.skew()),
            ("kurtosis", lambda x: x.kurtosis())
        ])
        .reset_index()
    )
    
    logger.info(f"Return distribution analysis: {len(analysis)} symbols")
    return analysis


def drawdown_summary(df: pd.DataFrame) -> pd.DataFrame:
    """Summarize drawdown statistics by symbol """

    if df.empty or "drawdown" not in df.columns:
        logger.warning("Cannot generate drawdown summary: missing drawdown column")
        return pd.DataFrame()
    
    summary = (
        df.groupby(["symbol", "asset_class"])
        ["drawdown"]
        .agg([
            ("max_drawdown", "min"),  # Most negative value
            ("avg_drawdown", "mean"),
            ("median_drawdown", "median"),
            ("recovery_periods", lambda x: (x > -0.01).sum())  # Approximate recovery events
        ])
        .reset_index()
    )
    
    # Convert to percentage
    for col in ["max_drawdown", "avg_drawdown", "median_drawdown"]:
        summary[f"{col}_pct"] = (summary[col] * 100).round(2)
    
    # Add risk label
    summary["drawdown_risk"] = summary["max_drawdown_pct"].apply(
        lambda x: "Severe" if x < -30 else ("High" if x < -15 else ("Moderate" if x < -5 else "Low"))
    )
    
    logger.info(f"Drawdown summary: {len(summary)} symbols")
    return summary


def correlation_matrix(df: pd.DataFrame) -> pd.DataFrame:
    """Calculate correlation matrix of daily returns across all symbols """
    
    if df.empty or "daily_return" not in df.columns:
        logger.warning("Cannot calculate correlation matrix: missing data")
        return pd.DataFrame()
    
    # Pivot to get daily returns per symbol
    pivot_returns = df.pivot_table(
        index="date",
        columns="symbol",
        values="daily_return"
    )
    
    # Calculate correlation
    corr_matrix = pivot_returns.corr()
    
    logger.info(f"Correlation matrix: {corr_matrix.shape[0]} symbols")
    return corr_matrix


def asset_class_correlation(df: pd.DataFrame) -> pd.DataFrame:
    """Calculate average correlation between asset classes """

    if df.empty or "daily_return" not in df.columns or "asset_class" not in df.columns:
        logger.warning("Cannot calculate asset class correlation: missing data")
        return pd.DataFrame()
    
    # Get asset class per symbol
    symbol_to_class = df[["symbol", "asset_class"]].drop_duplicates()
    
    # Pivot returns by symbol
    pivot_returns = df.pivot_table(
        index="date",
        columns="symbol",
        values="daily_return"
    )
    
    # Calculate correlation between all pairs
    symbol_corr = pivot_returns.corr().unstack().reset_index()
    symbol_corr.columns = ["symbol1", "symbol2", "correlation"]
    
    # Map asset classes
    symbol_corr = symbol_corr.merge(
        symbol_to_class.rename(columns={"symbol": "symbol1", "asset_class": "class1"}),
        on="symbol1"
    ).merge(
        symbol_to_class.rename(columns={"symbol": "symbol2", "asset_class": "class2"}),
        on="symbol2"
    )
    
    # Filter for cross-asset class correlations
    symbol_corr = symbol_corr[symbol_corr["class1"] != symbol_corr["class2"]]
    
    # Aggregate by asset class pair
    asset_corr = (
        symbol_corr.groupby(["class1", "class2"])
        ["correlation"]
        .agg(["mean", "median", "min", "max", "count"])
        .reset_index()
        .rename(columns={"mean": "avg_correlation", "count": "pair_count"})
    )
    
    logger.info(f"Asset class correlation: {len(asset_corr)} class pairs")
    return asset_corr


def top_bottom_performers(df: pd.DataFrame, n: int = 5) -> dict:
    """Top and bottom performing symbols by cumulative return """
    
    if df.empty or "cumulative_return" not in df.columns:
        logger.warning("Cannot identify performers: missing data")
        return {"top": pd.DataFrame(), "bottom": pd.DataFrame()}
    
    # Get latest cumulative return per symbol
    latest_returns = df.groupby(["symbol", "asset_class"]).agg(
        cumulative_return=("cumulative_return", "last"),
        final_price=("close", "last")
    ).reset_index().sort_values("cumulative_return", ascending=False)
    
    top = latest_returns.head(n)
    bottom = latest_returns.tail(n).sort_values("cumulative_return")
    
    logger.info(f"Top performers: {len(top)} symbols, Bottom performers: {len(bottom)} symbols")
    
    return {
        "top": top,
        "bottom": bottom
    }


def monthly_performance(df: pd.DataFrame) -> pd.DataFrame:
    """Calculate monthly returns for each symbol"""
    
    if df.empty or "daily_return" not in df.columns:
        logger.warning("Cannot calculate monthly performance: missing data")
        return pd.DataFrame()
    
    # Convert date to month
    df_copy = df.copy()
    df_copy["year_month"] = df_copy["date"].dt.to_period("M")
    
    # Calculate monthly return as final close of month divided by first close of month
    monthly = (
        df_copy.groupby(["symbol", "asset_class", "year_month"])
        .agg(
            month_start_price=("close", "first"),
            month_end_price=("close", "last")
        )
        .reset_index()
    )
    
    monthly["monthly_return"] = (monthly["month_end_price"] / monthly["month_start_price"]) - 1
    
    logger.info(f"Monthly performance: {len(monthly)} monthly records")
    return monthly


def annual_performance(df: pd.DataFrame) -> pd.DataFrame:
    """Calculate annual returns for each symbol"""
    
    if df.empty or "daily_return" not in df.columns:
        logger.warning("Cannot calculate annual performance: missing data")
        return pd.DataFrame()
    
    # Convert date to year
    df_copy = df.copy()
    df_copy["year"] = df_copy["date"].dt.year
    
    # Calculate annual return as final close of year divided by first close of year
    annual = (
        df_copy.groupby(["symbol", "asset_class", "year"])
        .agg(
            year_start_price=("close", "first"),
            year_end_price=("close", "last"),
            min_price=("close", "min"),
            max_price=("close", "max")
        )
        .reset_index()
    )
    
    annual["annual_return"] = (annual["year_end_price"] / annual["year_start_price"]) - 1
    annual["year_high"] = annual["max_price"]
    annual["year_low"] = annual["min_price"]
    annual["year_range"] = ((annual["max_price"] - annual["min_price"]) / annual["year_start_price"]) * 100
    
    logger.info(f"Annual performance: {len(annual)} annual records")
    return annual


def data_completeness_check(df: pd.DataFrame) -> pd.DataFrame:
    """Identify instruments with incomplete history """ 
    
    if df.empty:
        logger.warning("Cannot check data completeness: empty DataFrame")
        return pd.DataFrame()
    
    completeness = (
        df.groupby("symbol")
        .agg(
            observations=("date", "count"),
            date_min=("date", "min"),
            date_max=("date", "max"),
            asset_class=("asset_class", "first"),
            name=("name", "first")
        )
        .reset_index()
    )
    
    # Calculate expected observations
    completeness["date_range_days"] = (completeness["date_max"] - completeness["date_min"]).dt.days
    completeness["expected_observations"] = completeness["date_range_days"] + 1
    completeness["completeness_pct"] = (
        completeness["observations"] / completeness["expected_observations"] * 100
    ).round(2)
    completeness["has_full_history"] = completeness["completeness_pct"] >= 95  # Flag if < 95% complete
    
    missing_history = completeness[completeness["has_full_history"] == False]
    if len(missing_history) > 0:
        logger.warning(f"Found {len(missing_history)} symbols with incomplete history (< 95% complete)")
    
    return completeness