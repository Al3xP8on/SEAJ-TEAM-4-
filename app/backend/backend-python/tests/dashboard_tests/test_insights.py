import numpy as np
import pandas as pd
import pytest

from data_analysis.features import MarketFeatureEngineer
from dashboard.insights import (
    UNKNOWN_ASSET_CLASS,
    build_insights,
    diversification_matrix,
    ensure_metadata,
    performance_summary,
)


def _instrument(symbol, asset_class, daily_drift, noise_seed, days=120):
    """Synthetic daily OHLCV series with a steady drift plus noise"""
    rng = np.random.default_rng(noise_seed)
    returns = daily_drift + rng.normal(0, 0.01, days)
    close = 100 * np.cumprod(1 + returns)
    return pd.DataFrame({
        "symbol": symbol,
        "name": f"{symbol} Inc.",
        "asset_class": asset_class,
        "date": pd.bdate_range("2024-01-01", periods=days),
        "open": close,
        "high": close * 1.01,
        "low": close * 0.99,
        "close": close,
        "volume": 1_000_000,
    })


@pytest.fixture
def market_df():
    raw = pd.concat([
        _instrument("UP", "Equity", daily_drift=0.004, noise_seed=1),
        _instrument("FLAT", "ETF", daily_drift=0.0, noise_seed=2),
        _instrument("DOWN", "Bond", daily_drift=-0.004, noise_seed=3),
    ], ignore_index=True)
    return MarketFeatureEngineer.add_all_features(raw)


class TestEnsureMetadata:
    def test_adds_missing_metadata_columns(self):
        df = pd.DataFrame({"symbol": ["AAPL"], "close": [1.0]})

        result = ensure_metadata(df)

        assert result.loc[0, "asset_class"] == UNKNOWN_ASSET_CLASS
        assert result.loc[0, "name"] == "AAPL"

    def test_fills_symbols_missing_from_the_database(self):
        df = pd.DataFrame({"symbol": ["AAPL", "NEW"], "name": ["Apple", None], "asset_class": ["Equity", None]})

        result = ensure_metadata(df)

        assert result["asset_class"].tolist() == ["Equity", UNKNOWN_ASSET_CLASS]
        assert result["name"].tolist() == ["Apple", "NEW"]


class TestBuildInsights:
    def test_empty_dataframe_returns_no_insights(self):
        assert build_insights(pd.DataFrame()) == {}

    def test_kpis_identify_the_best_performer(self, market_df):
        kpis = build_insights(market_df)["kpis"]

        assert kpis["instruments"] == 3
        assert kpis["best_symbol"] == "UP"
        assert kpis["best_annualised_return"] > 0

    def test_performers_are_ranked_by_annualised_return(self, market_df):
        performers = build_insights(market_df, n=1)["performers"]

        assert performers["top"]["symbol"].tolist() == ["UP"]
        assert performers["bottom"]["symbol"].tolist() == ["DOWN"]

    def test_drawdowns_are_sorted_deepest_first(self, market_df):
        drawdowns = build_insights(market_df)["drawdowns"]

        assert drawdowns.iloc[0]["symbol"] == "DOWN"
        assert drawdowns["max_drawdown"].is_monotonic_increasing

    def test_top_and_bottom_never_overlap(self, market_df):
        performers = build_insights(market_df, n=5)["performers"]

        assert set(performers["top"]["symbol"]).isdisjoint(performers["bottom"]["symbol"])

    def test_every_insight_has_a_takeaway(self, market_df):
        takeaways = build_insights(market_df)["takeaways"]

        assert set(takeaways) == {"risk_return", "performers", "drawdowns", "diversification"}
        assert all(text for text in takeaways.values())
        assert "UP" in takeaways["performers"]
        assert "DOWN" in takeaways["drawdowns"]


class TestPerformanceSummary:
    def test_annualised_return_compounds_over_the_period(self):
        # Price doubles over exactly two years: 100% total, about 41.4% a year
        df = pd.DataFrame({
            "symbol": ["AAA", "AAA"],
            "name": ["AAA Inc.", "AAA Inc."],
            "asset_class": ["Equity", "Equity"],
            "date": pd.to_datetime(["2022-01-01", "2024-01-01"]),
            "close": [50.0, 100.0],
        })

        row = performance_summary(df).iloc[0]

        assert row["cumulative_return"] == pytest.approx(1.0)
        assert row["annualised_return"] == pytest.approx(2 ** (365.25 / 730) - 1)


class TestDiversificationMatrix:
    def test_uses_the_basket_when_available(self, market_df):
        basket = market_df.assign(symbol=market_df["symbol"].map({"UP": "SPY", "FLAT": "GLD", "DOWN": "AAPL"}))

        matrix = diversification_matrix(basket)

        assert list(matrix.columns) == ["SPY", "GLD"]

    def test_falls_back_to_available_instruments(self, market_df):
        matrix = diversification_matrix(market_df)

        assert sorted(matrix.columns) == ["DOWN", "FLAT", "UP"]
        assert matrix.loc["UP", "UP"] == pytest.approx(1.0)
