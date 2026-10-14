# Orders

The dashboard's **Place Order** button opens `/order`. `/blotter` opens the same trading desk, including the order list and per-order timeline.

Overview, Orders and Order History share a sticky navigation header. The header stays mounted during navigation and remains visible while scrolling. Login is outside this authenticated layout.

Users can place buy and sell orders for their own account. Admins can view all accounts, select a trading account, cancel pending orders, and reject pending orders with an audit reason. Ownership is checked before mutations; admin-only endpoints require the ADMIN role on the server.

## Supported behavior

- Market orders use the server's current ask for buys and bid for sells. Client prices are ignored for market orders.
- Limit orders remain PENDING until ask <= buy limit or bid >= sell limit. They can be cancelled until filled.
- Orders are good till cancelled (GTC), for whole units, and filled completely by the existing simulated execution engine.
- Pending buys reserve their order value; pending sells reserve their quantities. Short selling is disallowed.
- An idempotency key prevents a retried submission from creating a second order. Changing the request while reusing its key returns a conflict.
- Settlement locks the order and account and changes cash, holdings, fill price, and order history in the same database transaction. Duplicate executions and late fills after cancellation have no effect.
- Buys update weighted average cost; sells reduce holdings and record realized profit/loss. Fully sold positions close and later buys reopen them.
- The trading desk and dashboard poll every two seconds, with polling cancelled on navigation.

The market-data provider is currently a stub, producing simulated bid/ask quotes. `market.data.poll.symbols: "*"` polls all tradable instruments from the database. Stale quotes are not used for execution. The existing platform stores one cash balance per account and does not implement FX conversion. Prices/cash retain its existing settlement conventions. Editing submitted orders, partial fills, and GTD/IOC/FOK execution are not supported.

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

`GET /api/v1/instruments/{symbol}/quote` returns a current quote or 204 if none is available. Order timelines include persisted rejection reasons.

## Validation

Run backend tests with `mvn test` in `app/backend/backend-java`, and frontend tests/build with `npm test -- --watch=false` and `npm run build` in `app/frontend/SEAJ-ui`. Tests cover submission/retries, limits, cash/holding reservations, settlement, duplicate fills, position closure/reopening, cancellation, authorization, and UI updates.
