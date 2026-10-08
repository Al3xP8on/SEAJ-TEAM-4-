"""SEAJ market insights dashboard.

Run from app/backend/backend-python (so .streamlit/config.toml is picked up):
    streamlit run dashboard/app.py
"""
import sys
from pathlib import Path

# Streamlit only puts dashboard/ on the path; add backend-python so package imports resolve
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import altair as alt
import pandas as pd
import streamlit as st
from dotenv import load_dotenv, find_dotenv

from data_processing.constants import DEFAULT_TICKERS
from data_analysis.loader import MarketDataLoader
from data_analysis.cleaner import MarketDataCleaner
from data_analysis.features import MarketFeatureEngineer
from dashboard.insights import build_insights, ensure_metadata


load_dotenv(find_dotenv(usecwd=True))

st.set_page_config(page_title="SEAJ Market Insights", page_icon="📈", layout="wide")

# Okabe-Ito palette: distinguishable with the common forms of colour blindness
BLUE, ORANGE, GREEN, VERMILLION, PINK, SKY, YELLOW, GREY = (
    "#0072B2", "#E69F00", "#009E73", "#D55E00", "#CC79A7", "#56B4E9", "#F0E442", "#999999",
)
ASSET_CLASS_COLOURS = {"Equity": BLUE, "ETF": ORANGE, "Bond": GREEN, "Cash": PINK, "Unknown": GREY}
SERIES_COLOURS = [BLUE, ORANGE, GREEN, PINK, SKY, VERMILLION, YELLOW, GREY]
LABELLED_OUTLIERS = 3


@st.cache_data(ttl="6h", show_spinner="Loading market data from Yahoo Finance…")
def load_market_data(tickers: tuple[str, ...]) -> pd.DataFrame:
    """Extract, clean and engineer features, the same pipeline the analysis notebooks use"""
    raw = MarketDataLoader.load_analysis_data(list(tickers))
    if raw.empty:
        return raw
    clean = MarketDataCleaner.clean(raw)
    return ensure_metadata(MarketFeatureEngineer.add_all_features(clean))


def asset_class_scale(classes) -> alt.Scale:
    classes = sorted(classes)
    return alt.Scale(domain=classes, range=[ASSET_CLASS_COLOURS.get(c, GREY) for c in classes])


df = load_market_data(tuple(DEFAULT_TICKERS))

st.title("SEAJ Market Insights")
st.caption("Daily prices from Yahoo Finance, enriched with instrument data from the SEAJ database.")

if df.empty:
    st.error("No market data could be loaded. Check your internet connection and try again.")
    st.stop()

# Sidebar filters
with st.sidebar:
    st.header("Filters")
    asset_classes = sorted(df["asset_class"].unique())
    selected_classes = st.multiselect("Asset classes", asset_classes, default=asset_classes)
    top_n = st.slider("Instruments per ranking", min_value=3, max_value=10, value=5)
    if st.button("Refresh data"):
        load_market_data.clear()
        st.rerun()

filtered = df[df["asset_class"].isin(selected_classes)]
if filtered.empty:
    st.warning("Select at least one asset class.")
    st.stop()

insights = build_insights(filtered, n=top_n)
kpis = insights["kpis"]
takeaways = insights["takeaways"]
colour_scale = asset_class_scale(filtered["asset_class"].unique())

# Headline numbers
k1, k2, k3, k4, k5 = st.columns(5)
k1.metric("Instruments", kpis["instruments"])
k2.metric("Period", f"{kpis['date_from']:%Y} – {kpis['date_to']:%Y}")
k3.metric("Best performer", kpis["best_symbol"], f"{kpis['best_annualised_return']:.1%} a year")
k4.metric("Avg annual return", f"{kpis['avg_annual_return']:.1%}")
k5.metric("Avg annual volatility", f"{kpis['avg_annual_volatility']:.1%}")

st.divider()

# Insight 1: risk vs return
left, right = st.columns([3, 2], gap="large")
with left:
    st.subheader("1. Risk vs return")
    st.info(takeaways["risk_return"])
    risk_return = insights["risk_return"]
    points = (
        alt.Chart(risk_return)
        .mark_circle(size=90, opacity=0.85)
        .encode(
            x=alt.X("annual_volatility:Q", title="Annual volatility", axis=alt.Axis(format="%")),
            y=alt.Y("annual_return:Q", title="Annual return", axis=alt.Axis(format="%")),
            color=alt.Color("asset_class:N", title="Asset class", scale=colour_scale),
            tooltip=[
                "symbol",
                "asset_class",
                alt.Tooltip("annual_return:Q", title="Annual return", format=".1%"),
                alt.Tooltip("annual_volatility:Q", title="Annual volatility", format=".1%"),
            ],
        )
    )
    labels = (
        alt.Chart(risk_return.nlargest(LABELLED_OUTLIERS, "annual_return"))
        .mark_text(align="left", dx=8, fontSize=11)
        .encode(x="annual_volatility:Q", y="annual_return:Q", text="symbol:N")
    )
    st.altair_chart((points + labels).properties(height=360), width="stretch")
with right:
    st.subheader("By asset class")
    st.dataframe(
        insights["asset_classes"][["asset_class", "instrument_count", "avg_annual_return", "avg_annual_volatility"]],
        hide_index=True,
        width="stretch",
        column_config={
            "asset_class": "Asset class",
            "instrument_count": "Instruments",
            "avg_annual_return": st.column_config.NumberColumn("Avg return", format="percent"),
            "avg_annual_volatility": st.column_config.NumberColumn("Avg volatility", format="percent"),
        },
    )
    st.caption("Returns and volatility are annualised from daily data (252 trading days).")

st.divider()

# Insight 2: best and worst performers, ranked by annualised return so long and short histories compare fairly
st.subheader("2. Best and worst performers")
st.info(takeaways["performers"])
performers = pd.concat(
    [insights["performers"]["top"].assign(group="Top"), insights["performers"]["bottom"].assign(group="Bottom")]
)
bars = (
    alt.Chart(performers)
    .mark_bar()
    .encode(
        x=alt.X("annualised_return:Q", title="Annualised return", axis=alt.Axis(format="%")),
        y=alt.Y("symbol:N", sort="-x", title=None),
        color=alt.Color("group:N", title=None, scale=alt.Scale(domain=["Top", "Bottom"], range=[BLUE, ORANGE])),
        tooltip=[
            "symbol",
            "name",
            alt.Tooltip("annualised_return:Q", title="Annualised return", format=".1%"),
            alt.Tooltip("cumulative_return:Q", title="Total return", format=",.0%"),
            alt.Tooltip("final_price:Q", title="Latest price", format="$,.2f"),
        ],
    )
    .properties(height=alt.Step(26))
)
st.altair_chart(bars, width="stretch")

st.divider()

# Insights 3 and 4: drawdowns and diversification
left, right = st.columns(2, gap="large")
with left:
    st.subheader("3. Deepest drawdowns")
    st.info(takeaways["drawdowns"])
    drawdown_bars = (
        alt.Chart(insights["drawdowns"])
        .mark_bar(color=VERMILLION)
        .encode(
            x=alt.X("max_drawdown:Q", title="Largest fall from peak", axis=alt.Axis(format="%")),
            y=alt.Y("symbol:N", sort="x", title=None),
            tooltip=[
                "symbol",
                "asset_class",
                alt.Tooltip("max_drawdown:Q", title="Largest fall", format=".1%"),
                alt.Tooltip("drawdown_risk:N", title="Risk"),
            ],
        )
        .properties(height=alt.Step(26))
    )
    st.altair_chart(drawdown_bars, width="stretch")
with right:
    st.subheader("4. Diversification")
    correlation = insights["correlation"]
    if correlation.empty:
        st.caption("Select more instruments to compare how they move together.")
    else:
        st.info(takeaways["diversification"])
        order = list(correlation.columns)
        cells = correlation.rename_axis(index="row", columns="col").stack().reset_index(name="correlation")
        base = alt.Chart(cells).encode(
            x=alt.X("col:N", sort=order, title=None, axis=alt.Axis(labelAngle=0, orient="top")),
            y=alt.Y("row:N", sort=order, title=None),
        )
        heatmap = base.mark_rect().encode(
            color=alt.Color(
                "correlation:Q",
                title="Correlation",
                scale=alt.Scale(scheme="blueorange", domain=[-1, 1], reverse=True),
            ),
            tooltip=["row", "col", alt.Tooltip("correlation:Q", format=".2f")],
        )
        values = base.mark_text(fontSize=11).encode(
            text=alt.Text("correlation:Q", format=".2f"),
            color=alt.condition("abs(datum.correlation) > 0.6", alt.value("white"), alt.value("black")),
        )
        st.altair_chart((heatmap + values).properties(height=320), width="stretch")
        st.caption("Correlation of daily returns: 1 = move together, 0 = unrelated, −1 = move opposite.")

st.divider()

# Growth of selected instruments
st.subheader("Growth of $100")
symbols = sorted(filtered["symbol"].unique())
default_symbols = list(insights["performers"]["top"]["symbol"].head(3)) + [s for s in ["SPY"] if s in symbols]
chosen = st.multiselect("Instruments", symbols, default=list(dict.fromkeys(default_symbols)))
if chosen:
    growth = filtered.loc[filtered["symbol"].isin(chosen), ["date", "symbol", "cumulative_return"]].assign(
        value=lambda d: 100 * (1 + d["cumulative_return"])
    )
    lines = (
        alt.Chart(growth)
        .mark_line(strokeWidth=1.8)
        .encode(
            x=alt.X("date:T", title=None, axis=alt.Axis(format="%Y", tickCount="year")),
            y=alt.Y("value:Q", title="Value of $100 (log scale)", scale=alt.Scale(type="log"), axis=alt.Axis(format="$,.0f")),
            color=alt.Color("symbol:N", title=None, scale=alt.Scale(range=SERIES_COLOURS)),
            tooltip=[
                alt.Tooltip("date:T", format="%d %b %Y"),
                "symbol",
                alt.Tooltip("value:Q", title="Value", format="$,.0f"),
            ],
        )
        .properties(height=380)
    )
    st.altair_chart(lines, width="stretch")
    st.caption("Log scale: equal vertical distances mean equal percentage growth, so fast and slow growers can be compared.")
