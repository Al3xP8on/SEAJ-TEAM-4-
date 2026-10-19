# Alpaca market data

The Orders page reads real market prices from Alpaca through the Java backend. The browser receives prices, charts and connection status; API keys stay in the backend environment. Kafka delivers Alpaca quotes to SEAJ's execution engine, which matches market/limit orders at their live bid or ask. Alpaca supplies read-only market data; account balances and execution remain in SEAJ. See [the complete Kafka lifecycle](event-flow.md).

## Connect an account

1. Create an [Alpaca paper account](https://docs.alpaca.markets/us/docs/paper-trading).
2. In its dashboard, find **API Keys** and generate a key ID and secret. See Alpaca's [market-data getting-started guide](https://docs.alpaca.markets/us/docs/getting-started-with-alpaca-market-data).
3. Fill in these entries in the repository's private `.env` file:

```dotenv
ALPACA_ENABLED=true
ALPACA_API_KEY_ID=your-paper-api-key-id
ALPACA_API_SECRET_KEY=your-paper-api-secret-key
ALPACA_DATA_FEED=iex
```

4. From the repository root, build and recreate only the Java app:

```bash
docker-compose up -d --no-deps --build app
```

If the image already includes this integration and only the keys changed:

```bash
docker-compose up -d --no-deps --force-recreate app
```

The frontend dev server reloads source changes. Live verification requires valid keys; blank fields show a disconnected state and orders cannot execute without fresh Alpaca quotes. Upstream failures are reported and never replaced with demo execution prices. For the live-price engine and durable Kafka lifecycle, apply the [outbox migration and coordinated rebuild](event-flow.md#apply-locally).

## Coverage and price changes

The default `iex` feed uses Alpaca's Basic plan and covers IEX exchange activity for US stocks and ETFs. It is not consolidated data from all exchanges. The optional `sip` feed requires the appropriate entitlement for live quotes; historical SIP chart requests end 15 minutes before the current time. See [Alpaca's market-data plans](https://docs.alpaca.markets/us/docs/about-market-data-api).

Supported database instruments are tradable USD equities, ETFs, and exchange-listed commodity ETFs (including GLD, USO and DBC). Instruments explicitly named as ETFs are also eligible. Custom funds such as GLBEQ1 and synthetic treasury symbols such as UST10 have no Alpaca stock feed and show an unavailable message. A symbol absent from an Alpaca response is not assigned a fabricated price.

Daily gain is `(latest price - previous close) / previous close * 100`. Before today's daily bar exists, the most recent daily close is used as the comparison. Rankings compare prices from the latest available date and omit symbols without a comparison close. Prices change when the exchange reports new data; outside trading sessions the last market timestamps remain visible.

## API and request limits

- `GET /api/v1/market-data/overview` returns connection state, source feed, fetch time, supported snapshots, daily percentage changes, and unsupported symbols.
- `GET /api/v1/market-data/{symbol}/bars?timeframe=1Min` returns minute history. `timeframe=6Hour` returns the weekly chart’s 6-hour history, and `timeframe=1Day` returns daily history. Unknown instruments return 404 and other timeframes return 400.
- Both endpoints require the same JWT as the other trading endpoints.
- One batched snapshot request serves all supported instruments, cached for ten seconds. Minute history is cached for one minute; 6-hour and daily history are cached separately for five minutes. History requests fetch newest-first, stop at 1,000 minute bars, 60 six-hour bars or 30 daily bars, and follow pagination only until that limit. Six-hour requests look back up to 21 calendar days to allow for weekends and holidays. The 1D display retains the latest two sessions where available, with dividers at New York date changes and a previous-close reference line. Overnight/weekend gaps are compressed; a previous session may be partial because the existing 1,000-bar cap remains in place. The 1W control uses 6-hour bars and displays the last five available New York trading sessions, preserving the recorded prices and adding the latest trade when newer. The weekly curve uses monotone interpolation without inventing highs or lows; inspected prices remain actual samples. The 1M control uses 30 daily bars. Both reuse their shared server caches when switching periods; 10-second snapshot updates do not reload history.
- Snapshots use Alpaca's [snapshots endpoint](https://docs.alpaca.markets/us/reference/stocksnapshots-1); charts use its [historical bars endpoint](https://docs.alpaca.markets/us/reference/stockbars). Requests have finite connection/read timeouts.
- Authentication failures, subscription errors, throttling and outages produce sanitized messages without returning upstream credentials or response bodies. The overview retains the last successful prices and fetch time alongside an error during an outage.
- The Kafka price events retain the original quote timestamp. The existing 60-second freshness rule applies to execution, so an old closing quote does not become executable just because it was fetched again.

The Kafka order lifecycle requires the additive `02-order-event-outbox.sql` migration on existing databases. No database reset is needed. The old price-history table is not used to invent live charts or daily movement.

## Lightweight local startup

The default Compose stack runs the database, auth, Java app, Kafka and execution engine:

```bash
docker-compose up -d --build
```

SonarQube and Kafka UI are in the optional `tools` profile. Start them when needed:

```bash
docker-compose --profile tools up -d sonarqube kafka-ui
```

If those tools were already running before the profile was added, stop them once to free their resources:

```bash
docker-compose --profile tools stop sonarqube kafka-ui
```

Use `docker compose` in these commands if your laptop uses the Compose plugin. No cloud deployment or external cache is required.

The Orders page polls every ten seconds while visible and refreshes immediately after order actions. Order status, cash and holdings can take up to ten seconds to reflect a later fill. Market and trading-desk polling stop in a hidden tab, with an immediate refresh on return. The backend continues checking prices for pending orders. Chart history/date calculations are reused between live price updates; the SVG uses one polyline and a single interactive marker, instead of a separate element and tooltip for every historical bar.

## Verification

Backend tests mock Alpaca HTTP responses and cover authentication, parsing, previous-close calculations, pagination, caching, unavailable feeds and quote publication. UI tests cover live price updates, chart periods, rankings, unsupported instruments, failed requests and cancelling obsolete chart requests. A production build validates the templates. Actual live-feed verification requires account credentials.
