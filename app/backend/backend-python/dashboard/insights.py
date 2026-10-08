import pandas as pd

from data_analysis.analysis import (
    risk_return_summary,
    asset_class_summary,
    drawdown_summary,
    correlation_matrix,
)
from logger.logger import logger


UNKNOWN_ASSET_CLASS = "Unknown"

# yfinance labels every fund "ETF", so asset classes can't show diversification on their own.
# These funds each track a distinct market: US stocks, tech, bonds, gold, oil, property, Japan.
DIVERSIFICATION_BASKET = ["SPY", "QQQ", "BND", "LQD", "GLD", "USO", "VNQ", "EWJ"]
DIVERSIFICATION_ANCHOR = "SPY"
MAX_CORRELATION_SYMBOLS = 8

DAYS_PER_YEAR = 365.25


def ensure_metadata(df: pd.DataFrame) -> pd.DataFrame:
    """Fill instrument metadata when PostgreSQL was unavailable so the analysis can still group by it"""
    df = df.copy()
    if "asset_class" not in df.columns:
        logger.warning("No instrument metadata attached, grouping all instruments as Unknown")
        df["asset_class"] = UNKNOWN_ASSET_CLASS
    if "name" not in df.columns:
        df["name"] = df["symbol"]
    df["asset_class"] = df["asset_class"].fillna(UNKNOWN_ASSET_CLASS)
    df["name"] = df["name"].fillna(df["symbol"])
    return df


def performance_summary(df: pd.DataFrame) -> pd.DataFrame:
    """Total and annualised return per instrument, so instruments with different histories compare fairly"""
    ordered = df.sort_values(["symbol", "date"])
    summary = (
        ordered.groupby(["symbol", "name", "asset_class"])
        .agg(
            first_date=("date", "first"),
            last_date=("date", "last"),
            first_close=("close", "first"),
            final_price=("close", "last"),
        )
        .reset_index()
    )

    years = (summary["last_date"] - summary["first_date"]).dt.days / DAYS_PER_YEAR
    growth = summary["final_price"] / summary["first_close"]
    summary["cumulative_return"] = growth - 1
    summary["annualised_return"] = growth.pow(1 / years.where(years > 0)) - 1

    return summary.sort_values("annualised_return", ascending=False).reset_index(drop=True)


def diversification_matrix(df: pd.DataFrame) -> pd.DataFrame:
    """Correlation of daily returns across instruments that each represent a different market"""
    available = set(df["symbol"].unique())
    symbols = [s for s in DIVERSIFICATION_BASKET if s in available]
    if len(symbols) < 2:
        # Basket filtered out; fall back to whatever instruments are left
        symbols = sorted(available)[:MAX_CORRELATION_SYMBOLS]
    if len(symbols) < 2:
        return pd.DataFrame()

    return correlation_matrix(df[df["symbol"].isin(symbols)]).loc[symbols, symbols]


def headline_kpis(df: pd.DataFrame, risk_return: pd.DataFrame, performance: pd.DataFrame) -> dict:
    """Headline numbers for the top of the dashboard"""
    if df.empty or risk_return.empty or performance.empty:
        return {}

    best = performance.iloc[0]
    return {
        "instruments": int(df["symbol"].nunique()),
        "date_from": df["date"].min(),
        "date_to": df["date"].max(),
        "best_symbol": best["symbol"],
        "best_annualised_return": float(best["annualised_return"]),
        "avg_annual_return": float(risk_return["annual_return"].mean()),
        "avg_annual_volatility": float(risk_return["annual_volatility"].mean()),
    }


def risk_return_insight(asset_classes: pd.DataFrame) -> str:
    """One-sentence takeaway: which asset class earned the most per unit of risk"""
    if asset_classes.empty:
        return ""

    ranked = asset_classes.assign(
        return_per_risk=asset_classes["avg_annual_return"] / asset_classes["avg_annual_volatility"]
    ).sort_values("return_per_risk", ascending=False)
    best = ranked.iloc[0]
    return (
        f"{best['asset_class']} delivered the best return for its risk: "
        f"{best['avg_annual_return']:.1%} a year at {best['avg_annual_volatility']:.1%} volatility."
    )


def performers_insight(performance: pd.DataFrame) -> str:
    """One-sentence takeaway: spread between the best and worst instrument"""
    if performance.empty:
        return ""

    best, worst = performance.iloc[0], performance.iloc[-1]
    return (
        f"{best['symbol']} grew {best['annualised_return']:.1%} a year "
        f"({best['cumulative_return']:,.0%} in total), while {worst['symbol']} "
        f"managed {worst['annualised_return']:.1%} a year."
    )


def drawdown_insight(drawdowns: pd.DataFrame) -> str:
    """One-sentence takeaway: the deepest peak-to-trough fall and how common severe falls were"""
    if drawdowns.empty:
        return ""

    worst = drawdowns.sort_values("max_drawdown").iloc[0]
    severe = int((drawdowns["drawdown_risk"] == "Severe").sum())
    return (
        f"{worst['symbol']} had the deepest fall at {worst['max_drawdown_pct']:.1f}% from its peak; "
        f"{severe} of {len(drawdowns)} instruments fell more than 30% at some point."
    )


def diversification_insight(correlation: pd.DataFrame) -> str:
    """One-sentence takeaway: which instrument moves least with the market"""
    if correlation.empty:
        return ""

    anchor = DIVERSIFICATION_ANCHOR if DIVERSIFICATION_ANCHOR in correlation.columns else correlation.columns[0]
    others = correlation[anchor].drop(anchor).sort_values()
    least = others.index[0]
    return (
        f"{least} moves least with {anchor} (correlation {others.iloc[0]:.2f}), "
        f"so it does the most to diversify a {anchor} holding."
    )


def build_insights(df: pd.DataFrame, n: int = 5) -> dict:
    """Run the analysis functions the dashboard needs and attach a takeaway to each insight"""
    if df.empty:
        logger.warning("Empty DataFrame passed to build_insights()")
        return {}

    df = ensure_metadata(df)

    risk_return = risk_return_summary(df)
    asset_classes = asset_class_summary(risk_return)
    performance = performance_summary(df)
    all_drawdowns = drawdown_summary(df)
    correlation = diversification_matrix(df)

    ranked = performance.dropna(subset=["annualised_return"])
    top = ranked.head(n)
    bottom = ranked.iloc[len(top):].tail(n)

    insights = {
        "kpis": headline_kpis(df, risk_return, ranked),
        "risk_return": risk_return,
        "asset_classes": asset_classes,
        "performers": {"top": top, "bottom": bottom},
        "drawdowns": all_drawdowns.sort_values("max_drawdown").head(n).reset_index(drop=True),
        "correlation": correlation,
        "takeaways": {
            "risk_return": risk_return_insight(asset_classes),
            "performers": performers_insight(ranked),
            "drawdowns": drawdown_insight(all_drawdowns),
            "diversification": diversification_insight(correlation),
        },
    }

    logger.info(f"Dashboard insights built for {df['symbol'].nunique()} instruments")
    return insights
