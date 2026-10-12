import pandas as pd
import pytest

from data_analysis.features import MarketFeatureEngineer


class TestCumulativeReturn:
    def test_daily_returns_are_compounded(self):
        # +10% then +10% compounds to +21%, not the +20% a simple sum gives
        df = pd.DataFrame({
            "symbol": ["AAA", "AAA", "AAA"],
            "date": pd.bdate_range("2024-01-01", periods=3),
            "close": [100.0, 110.0, 121.0],
        })

        result = MarketFeatureEngineer.add_cumulative_return(MarketFeatureEngineer.add_returns(df))

        assert result["cumulative_return"].tolist() == pytest.approx([0.0, 0.10, 0.21])

    def test_cumulative_return_matches_price_change_per_symbol(self):
        df = pd.DataFrame({
            "symbol": ["AAA", "AAA", "BBB", "BBB"],
            "date": list(pd.bdate_range("2024-01-01", periods=2)) * 2,
            "close": [50.0, 75.0, 200.0, 100.0],
        })

        result = MarketFeatureEngineer.add_cumulative_return(MarketFeatureEngineer.add_returns(df))
        final = result.groupby("symbol")["cumulative_return"].last()

        assert final["AAA"] == pytest.approx(0.5)
        assert final["BBB"] == pytest.approx(-0.5)
