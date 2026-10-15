# Orders

The dashboard's **Place Order** button opens `/order`. `/blotter` opens the same trading desk, including the order list and per-order timeline.

Overview, Orders and Order History share a sticky navigation header. The header stays mounted during navigation and remains visible while scrolling. Login is outside this authenticated layout.

Users can place buy and sell orders for their own account. Admins can view all accounts, select a trading account, cancel pending orders, and reject pending orders with an audit reason. Ownership is checked before mutations; admin-only endpoints require the ADMIN role on the server.

Regular users use their own account automatically and do not see the trading-account selector. The instrument picker searches symbols and names and supports arrow keys, Enter and Escape. Selecting an instrument opens its market detail card above the market overview, with an intraday or daily price chart, daily change, OHLC values and volume. Holdings remain on Overview and continue to refresh for sell validation.

Market cards use Alpaca snapshots and history through authenticated backend endpoints. Snapshots refresh every ten seconds and rank supported instruments by percentage change from the previous close. The 1D chart uses minute bars plus the latest trade, keeping the previous session's available history for comparison. Dashed vertical dividers and date labels separate New York trading days; overnight and weekend gaps are compressed, while elapsed-time spacing within each day is retained. A dotted horizontal line marks the previous close when supplied by Alpaca. The 1W chart shows the last five available trading days and 1M shows the last 30 daily bars. Both reuse the same daily-history request, so switching between them does not fetch additional history. Intraday history refreshes once a minute and daily history every five minutes. The chart reuses calculated history and uses a single marker to inspect prices with the pointer or arrow keys. Browser polling pauses in hidden tabs and resumes immediately when visible. Prices retain their exchange timestamps, and connection failures or unsupported instruments are labelled explicitly. See [Alpaca setup](alpaca.md) for API-key configuration and local startup.

## Supported behavior

- Market orders use the server's current ask for buys and bid for sells. Client prices are ignored for market orders.
- Limit orders remain PENDING until ask <= buy limit or bid >= sell limit. They can be cancelled until filled.
- Orders are good till cancelled (GTC), for whole units, and filled completely by the existing simulated execution engine.
- Pending buys reserve their order value; pending sells reserve their quantities. Short selling is disallowed.
  The account cash card shows cash reserved for pending buys and the remaining amount available for new orders. Filled, cancelled and rejected orders no longer reserve cash; only the selected account's pending buys count.
- An idempotency key prevents a retried submission from creating a second order. Changing the request while reusing its key returns a conflict.
  The browser uses `randomUUID` when available and falls back to a UUID generated with `getRandomValues` on HTTP server/IP origins. If it cannot generate a key, it shows a preparation error before submission.
- Settlement locks the order and account and changes cash, holdings, fill price, and order history in the same database transaction. Duplicate executions and late fills after cancellation have no effect.
- Buys update weighted average cost; sells reduce holdings and record realized profit/loss. Fully sold positions close and later buys reopen them.
- The trading desk refreshes orders, cash, holdings and quotes every ten seconds while visible, and refreshes immediately after order actions. Other sessions' fills appear on the next refresh. The dashboard retains its two-second refresh. Polling is cancelled on navigation.

When Alpaca credentials are configured, its supported stock/ETF quotes feed the existing Kafka pricing pipeline and simulated execution engine. Unavailable Alpaca prices do not fall back to simulated prices. Without configured credentials, order execution retains the existing demo quotes and the market cards show that Alpaca is disconnected. `market.data.poll.symbols: "*"` selects tradable instruments from the database. Stale quotes are not used for execution. The existing platform stores one cash balance per account and does not implement FX conversion. Editing submitted orders, partial fills, and GTD/IOC/FOK execution are not supported.

## Apply to an existing local environment

From the repository root, apply the additive migration without resetting volumes:

```bash
docker exec -i seaj_postgres_db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1' < infra/db/migrations/01-update-orders.sql
docker-compose up -d --no-deps --build app
```

The Angular dev server watches the source changes. If it is not running:

```bash
cd app/frontend/SEAJ-ui
npm start
```

Fresh database builds include the additional columns and PENDING enum value in their SQL schema. Existing orders default to LIMIT; their historical fill price remains unknown unless a new fill is processed.

## API additions

`POST /api/v1/orders` accepts `orderType` (`MARKET` or `LIMIT`, default `LIMIT`), optional `idempotencyKey`, and `timeInForce` (`GTC` only). `price` is required for LIMIT and omitted for MARKET. Responses include `orderType` and nullable `executionPrice`.

`GET /api/v1/instruments/{symbol}/quote` returns a current quote or 204 if none is available. Order timelines include persisted rejection reasons. Rejected orders offer **View reason**, with a prominent explanation in details. Automatic settlement rejections distinguish insufficient cash (required/available amounts), insufficient holdings (requested/available units), inactive accounts, untradable instruments and position limits. Pricing-validation rejections are also persisted and included in their audit events. An open details dialog refreshes if its order status changes. Older orders without a specific recorded reason say so explicitly.

## Validation

Run backend tests with `mvn test` in `app/backend/backend-java`, and frontend tests/build with `npm test -- --watch=false` and `npm run build` in `app/frontend/SEAJ-ui`. Tests cover submission/retries, limits, cash/holding reservations, settlement, duplicate fills, position closure/reopening, cancellation, authorization, and UI updates.
