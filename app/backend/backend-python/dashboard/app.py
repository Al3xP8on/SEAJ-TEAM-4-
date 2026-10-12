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
from data_analysis.constants import TRADING_DAYS
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

# Look-back windows; None means the full history
PERIODS = {"1Y": 1, "3Y": 3, "5Y": 5, "10Y": 10, "Max": None}
DEFAULT_PERIOD = "5Y"
# Over the full history, an instrument needs a year of data to be ranked
MAX_PERIOD_MIN_YEARS = 1.0
CARD_CHART_HEIGHT = 300
SPARKLINE_POINTS = 60


@st.cache_data(ttl="6h", show_spinner="Loading market data from Yahoo Finance…")
def load_clean_data(tickers: tuple[str, ...]) -> pd.DataFrame:
    """Extract and clean the full history once; features are computed per period"""
    raw = MarketDataLoader.load_analysis_data(list(tickers))
    if raw.empty:
        return raw
    return ensure_metadata(MarketDataCleaner.clean(raw))


@st.cache_data(ttl="6h", show_spinner="Crunching the numbers…")
def load_period_data(tickers: tuple[str, ...], period: str) -> pd.DataFrame:
    """Features recomputed inside the window, so drawdowns and growth start from the window's first day"""
    clean = load_clean_data(tickers)
    if clean.empty:
        return clean
    years = PERIODS[period]
    if years is not None:
        start = clean["date"].max() - pd.DateOffset(years=years)
        clean = clean[clean["date"] >= start]
    return MarketFeatureEngineer.add_all_features(clean)


def asset_class_scale(classes) -> alt.Scale:
    classes = sorted(classes)
    return alt.Scale(domain=classes, range=[ASSET_CLASS_COLOURS.get(c, GREY) for c in classes])


def sparkline(df: pd.DataFrame, symbol: str, column: str) -> list[float]:
    """One instrument's series over the period, thinned out for a KPI card"""
    series = df.loc[df["symbol"] == symbol].sort_values("date")[column].dropna()
    step = max(1, len(series) // SPARKLINE_POINTS)
    return series.iloc[::step].round(4).tolist()


def card(title: str, caption: str | None = None):
    """Bordered panel with a title, used for every chart on the page"""
    container = st.container(border=True)
    container.markdown(f"**{title}**")
    if caption:
        container.caption(caption)
    return container


# Sidebar filters
with st.sidebar:
    st.header("Filters")
    period = st.segmented_control("Period", list(PERIODS), default=DEFAULT_PERIOD) or DEFAULT_PERIOD

df = load_period_data(tuple(DEFAULT_TICKERS), period)

if df.empty:
    st.title("SEAJ Market Insights")
    st.error("No market data could be loaded. Check your internet connection and try again.")
    st.stop()

with st.sidebar:
    asset_classes = sorted(df["asset_class"].unique())
    selected_classes = st.multiselect("Asset classes", asset_classes, default=asset_classes)
    top_n = st.slider("Instruments per ranking", min_value=3, max_value=10, value=5)
    st.divider()
    if st.button("Refresh data", width="stretch"):
        load_clean_data.clear()
        load_period_data.clear()
        st.rerun()
    st.caption("Daily prices from Yahoo Finance, enriched with instrument data from the SEAJ database.")

filtered = df[df["asset_class"].isin(selected_classes)]
if filtered.empty:
    st.warning("Select at least one asset class.")
    st.stop()

period_years = PERIODS[period]
min_years = MAX_PERIOD_MIN_YEARS if period_years is None else period_years * 0.9
insights = build_insights(filtered, n=top_n, min_years=min_years)
kpis = insights["kpis"]
takeaways = insights["takeaways"]
colour_scale = asset_class_scale(filtered["asset_class"].unique())

# Header
st.title("SEAJ Market Insights")
st.caption(
    f"{kpis['instruments']} instruments · {kpis['date_from']:%d %b %Y} – {kpis['date_to']:%d %b %Y} · "
    f"returns and volatility annualised from daily data"
)

# Headline numbers
deepest = insights["drawdowns"].iloc[0] if not insights["drawdowns"].empty else None
k1, k2, k3, k4, k5 = st.columns(5)
k1.metric("Instruments", kpis["instruments"], border=True)
k2.metric(
    "Best performer",
    kpis["best_symbol"],
    f"{kpis['best_annualised_return']:.1%} a year",
    border=True,
    chart_data=sparkline(filtered, kpis["best_symbol"], "close"),
)
k3.metric("Avg annual return", f"{kpis['avg_annual_return']:.1%}", border=True)
k4.metric("Avg annual volatility", f"{kpis['avg_annual_volatility']:.1%}", border=True)
if deepest is not None:
    k5.metric(
        "Deepest drawdown",
        deepest["symbol"],
        f"{deepest['max_drawdown']:.1%} from peak",
        border=True,
        chart_data=sparkline(filtered, deepest["symbol"], "drawdown"),
        chart_type="area",
    )

# Key takeaways, one line per insight
with st.container(border=True):
    st.markdown("**Key takeaways**")
    for label, key in [
        ("Risk vs return", "risk_return"),
        ("Performers", "performers"),
        ("Drawdowns", "drawdowns"),
        ("Diversification", "diversification"),
    ]:
        if takeaways[key]:
            st.markdown(f":blue-badge[{label}] {takeaways[key]}")

# Insight grid, row 1: risk vs return, best and worst performers
left, right = st.columns(2)
with left, card("Risk vs return", "Each dot is an instrument; up and to the left is better."):
    risk_return = insights["risk_return"]
    points = (
        alt.Chart(risk_return)
        .mark_circle(size=80, opacity=0.85)
        .encode(
            x=alt.X("annual_volatility:Q", title="Annual volatility", axis=alt.Axis(format="%")),
            y=alt.Y("annual_return:Q", title="Annual return", axis=alt.Axis(format="%")),
            color=alt.Color("asset_class:N", title="Asset class", scale=colour_scale, legend=alt.Legend(orient="bottom")),
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
    st.altair_chart((points + labels).properties(height=CARD_CHART_HEIGHT), width="stretch")

# Ranked by annualised return so long and short histories compare fairly
with right, card("Best and worst performers", "Annualised return over the selected period."):
    performers = pd.concat(
        [insights["performers"]["top"].assign(group="Top"), insights["performers"]["bottom"].assign(group="Bottom")]
    )
    bars = (
        alt.Chart(performers)
        .mark_bar(cornerRadiusEnd=3)
        .encode(
            x=alt.X("annualised_return:Q", title="Annualised return", axis=alt.Axis(format="%")),
            y=alt.Y("symbol:N", sort="-x", title=None),
            color=alt.Color(
                "group:N", title=None,
                scale=alt.Scale(domain=["Top", "Bottom"], range=[BLUE, ORANGE]),
                legend=alt.Legend(orient="bottom"),
            ),
            tooltip=[
                "symbol",
                "name",
                alt.Tooltip("annualised_return:Q", title="Annualised return", format=".1%"),
                alt.Tooltip("cumulative_return:Q", title="Total return", format=",.0%"),
                alt.Tooltip("final_price:Q", title="Latest price", format="$,.2f"),
            ],
        )
        .properties(height=CARD_CHART_HEIGHT)
    )
    st.altair_chart(bars, width="stretch")

# Insight grid, row 2: drawdowns and diversification
left, right = st.columns(2)
with left, card("Deepest drawdowns", "Largest fall from a previous peak."):
    drawdown_bars = (
        alt.Chart(insights["drawdowns"])
        .mark_bar(color=VERMILLION, cornerRadiusEnd=3)
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
        .properties(height=CARD_CHART_HEIGHT)
    )
    st.altair_chart(drawdown_bars, width="stretch")

with right, card("Diversification", "Correlation of daily returns: 1 = move together, 0 = unrelated."):
    correlation = insights["correlation"]
    if correlation.empty:
        st.caption("Select more instruments to compare how they move together.")
    else:
        order = list(correlation.columns)
        cells = correlation.rename_axis(index="row", columns="col").stack().reset_index(name="correlation")
        base = alt.Chart(cells).encode(
            x=alt.X("col:N", sort=order, title=None, axis=alt.Axis(labelAngle=0, orient="top")),
            y=alt.Y("row:N", sort=order, title=None),
        )
        heatmap = base.mark_rect().encode(
            color=alt.Color(
                "correlation:Q",
                title=None,
                scale=alt.Scale(scheme="blueorange", domain=[-1, 1], reverse=True),
                legend=None,
            ),
            tooltip=["row", "col", alt.Tooltip("correlation:Q", format=".2f")],
        )
        values = base.mark_text(fontSize=11).encode(
            text=alt.Text("correlation:Q", format=".2f"),
            color=alt.condition("abs(datum.correlation) > 0.6", alt.value("white"), alt.value("black")),
        )
        st.altair_chart((heatmap + values).properties(height=CARD_CHART_HEIGHT), width="stretch")

# Detail views
growth_tab, detail_tab, classes_tab = st.tabs(["Growth of $100", "Instrument detail", "Asset classes"])
symbols = sorted(filtered["symbol"].unique())

with growth_tab:
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
                x=alt.X("date:T", title=None),
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
        st.caption("Log scale: equal vertical distances mean equal percentage growth.")

with detail_tab:
    symbol = st.selectbox("Instrument", symbols, index=symbols.index(kpis["best_symbol"]) if kpis["best_symbol"] in symbols else 0)
    history = filtered[filtered["symbol"] == symbol].sort_values("date")
    latest = history.iloc[-1]
    d1, d2, d3 = st.columns(3)
    d1.metric("Latest close", f"${latest['close']:,.2f}", f"{latest['cumulative_return']:.1%} over period", border=True)
    d2.metric("30-day volatility", f"{latest['rolling_volatility'] * TRADING_DAYS ** 0.5:.1%}", help="Annualised", border=True)
    d3.metric("Current drawdown", f"{latest['drawdown']:.1%}", border=True)
    price = alt.Chart(history).mark_line(color=BLUE, strokeWidth=1.6).encode(
        x=alt.X("date:T", title=None),
        y=alt.Y("close:Q", title="Close", axis=alt.Axis(format="$,.0f")),
        tooltip=[alt.Tooltip("date:T", format="%d %b %Y"), alt.Tooltip("close:Q", format="$,.2f")],
    ).properties(height=260)
    underwater = alt.Chart(history).mark_area(color=VERMILLION, opacity=0.5).encode(
        x=alt.X("date:T", title=None),
        y=alt.Y("drawdown:Q", title="Drawdown", axis=alt.Axis(format="%")),
        tooltip=[alt.Tooltip("date:T", format="%d %b %Y"), alt.Tooltip("drawdown:Q", format=".1%")],
    ).properties(height=140)
    st.altair_chart(alt.vconcat(price, underwater).resolve_scale(x="shared"), width="stretch")

with classes_tab:
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
