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

## Catalogue replacement

Apply `infra/db/migrations/04-market-catalogue.sql` to existing databases before deploying the backend changes. New database containers apply it after seeding orders and holdings. It replaces the sample GLBEQ1 entry in the browsable catalogue with IBM (USD Equity), verified against Alpaca's IEX snapshots. All other current USD equity/ETF/commodity tickers returned feed data during verification. Migration `05-bond-cash-market-catalogue.sql` subsequently replaces the eight unsupported Bond/Cash entries with feed-connected ETFs as described below.

The visible catalogue remains 120 instruments: Equity 80, ETF 28, Bond 7, Cash 2 and Commodity 3. The legacy GLBEQ1 database row remains hidden from catalogue listings and is no longer tradable. Direct symbol lookup and historical relationships remain intact; orders, holdings, currency and recorded prices are not transferred to IBM. Physical database row count after migration 04 is 121 (129 after migration 05). `catalog_visible` applies consistently to unfiltered, asset-class, currency and tradability catalogue queries. An already-present IBM causes the migration to roll back rather than silently change counts. Re-running a completed migration has no effect.


### Bond and cash-equivalent ETFs

Apply `infra/db/migrations/05-bond-cash-market-catalogue.sql` after migration 04. Fresh database containers apply both automatically. Bond replacements are CORPB1 → VCIT, GILT10 → VGIT, UST10 → GOVT, UST2 → VGSH, UST5 → SCHR, UST30 → VGLT. CORP already supplies Alpaca quotes and stays in the catalogue. Cash replacements are CASHGBP → SGOV and CASHUSD → BIL. Names explicitly identify these as ETFs (cash-equivalent Treasury ETFs for the Cash category); all quote in USD. GILT10's replacement provides US Treasury exposure rather than UK gilts, and SGOV is a USD cash equivalent rather than GBP cash.

All 120 visible tickers returned trade data from Alpaca's IEX snapshot endpoint when verified. Category counts remain Equity 80, ETF 28, Bond 7, Cash 2, Commodity 3. The Bond/Cash categories now group exposure rather than security structure; these are securities, not cash balances or individual bonds. Original instrument rows remain hidden and nontradable for historical records. No orders, holdings, price history, or account balances are transferred. The migration checks category counts, rolls back on replacement collisions, and is safe to rerun. Feed availability can still vary by session and provider status.

### Actual cash and individual bonds

Migration `06-actual-cash-catalogue.sql` restores CASHUSD and CASHGBP as nontradable currency entries, hiding BIL and SGOV without modifying their historical relationships. Apply migrations in order; migration 05 has been superseded for cash entries. Cash remains two catalogue entries; other category counts are unchanged.

The Market Cash tab reads only the authenticated user's `/api/v1/accounts/{id}` balance, using the same USD convention as Portfolio, refreshed every 30 seconds while visible. GBP is explicitly unavailable because accounts have no separate GBP ledger. Cash has no stock rankings or price charts. These are SEAJ balances, not the balance of the shared Alpaca data-provider account.

Individual bonds require Alpaca Broker API fixed-income access, which is separate from the existing IEX stock-data credentials. Read-only checks against the connected credentials returned HTTP 401 for the sandbox Treasury catalogue and HTTP 403 for production. Until suitable Broker credentials or a different provider are configured, the Bond tab displays the connection limitation rather than claiming bond ETFs are individual bonds. Existing bond ETFs remain identified as Bond ETFs in the Overview table. No individual-bond quotes are fabricated.

### Converted USD cash values

The Cash table shows the SEAJ USD balance alongside GBP, EUR, CHF, JPY, CAD and AUD equivalents. All equivalents represent the same USD funds, not separate holdings, and no conversion transaction occurs. `GET /api/v1/market-data/fx-rates` proxies daily USD-based Frankfurter reference rates, caches successful results for one hour and includes the provider's rate date. Requests use a fixed provider URL and carry no account information. The endpoint remains behind the existing API authentication. Rate failures leave USD available; cached rates retain their original date with an explicit warning and failures back off for a minute. The UI refreshes rates hourly while Cash is visible and account cash every 30 seconds. Invalid/missing rates show unavailable; zero cash converts to zero. The values are indicative, not executable FX quotes.

### Forex market view

The Cash market filter has been replaced by Forex. Account cash remains in Portfolio. Forex displays EUR/USD, GBP/USD, USD/JPY, USD/CHF, USD/CAD and AUD/USD using daily Frankfurter reference rates, with rate dates and changes versus the previous available reference date. EUR/USD, GBP/USD and AUD/USD invert the provider's USD-based rates. `GET /api/v1/market-data/fx-history` fetches the latest 120 calendar days through a fixed server-side request, with hourly caching and dated-cache fallback on upstream failures. The 1W/1M/3M chart windows end at the latest available rate date. Missing pairs remain unavailable and their chart buttons are disabled. There are no bid/ask spreads or order controls because this is a reference-rate market view, not an execution feed. Provider status and dates replace the IEX feed label while Forex is selected.

Forex chart links open the shared market-detail modal rather than rendering analytics under the pair table. The dialog retains the 1W/1M/3M controls and closes with Close, Escape, or a click outside its bounds; interactions within the popup keep it open.

### Additional commodities

`07-expand-commodities.sql` adds SLV, IAU, PPLT, PALL, CPER, UNG, BNO, DBA, PDBC, COMT, GSG and USCI. All twelve were verified as tradable Alpaca assets with IEX latest-trade and daily-bar data. They are USD exchange-traded commodity funds/trusts, not direct spot or futures instruments. The visible catalogue grows from 120 to 132, with Commodity increasing from 3 to 15; other category counts stay unchanged. The base seed includes them, and fresh database initialization also runs the idempotent migration. Existing orders and positions are unchanged.

## Alpaca Broker individual bonds

The Bond tab now uses the authenticated, read-only `GET /api/v1/market-data/bonds` endpoint. Configure these separately from stock-feed credentials in the root private `.env`:

```dotenv
ALPACA_BROKER_ENABLED=true
ALPACA_BROKER_BASE_URL=https://broker-api.sandbox.alpaca.markets
ALPACA_BROKER_CLIENT_ID=your_client_id
ALPACA_BROKER_CLIENT_SECRET=your_client_secret
```

Docker Compose passes the settings only to the Java backend. Generate Client Secret credentials in Brokerdash with read-only access; never commit them. The server exchanges credentials via form-encoded OAuth client credentials at `https://authx.sandbox.alpaca.markets/v1/oauth2/token` (production uses `authx.alpaca.markets`), reuses tokens until shortly before expiry and retries a rejected token once. It accepts only the documented US sandbox/production Broker hosts. Credentials and access tokens are not sent to the browser, returned in API DTOs, or logged. The OAuth client uses JDK HTTP rather than Spring HTTP body logging.

The service reads `us_treasuries` and `us_corporates`, filtering for outstanding securities, and displays ISIN, CUSIP, issuer, maturity, coupon, catalogue close price, price date and close yield to maturity. Prices are percentages of par, not dollar values or executable quotes. Missing fields are shown unavailable rather than zero. The details popup has no order action; no accounts, orders, holdings or catalogue database rows are changed by this integration. Broker tradability is informational and does not enable SEAJ bond execution.

Successful catalogues are cached for five minutes; failures back off for one minute. The client refreshes the tab every five minutes while visible and provides a manual refresh. Partial failures preserve prior category results and show a warning with the original per-bond price dates.

After changing settings or source, run `docker-compose up -d --no-deps --build app frontend`. The current sandbox credentials authenticate, but both catalogue requests return HTTP 403 with `subscription does not permit querying us treasuries` / `subscription does not permit querying us corporates`. Ask Alpaca support to enable fixed-income catalogue access for the sandbox correspondent. Authentication success alone does not establish product entitlement; changing to a full-access key is not the fix for this subscription response. Once entitled, the same integration can load the catalogues on the next refresh without another code change. No fabricated securities or prices are substituted.
