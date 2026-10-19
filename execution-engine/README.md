# Live-price execution engine

SEAJ's engine matches orders against actual Alpaca bid/ask quotes received on Kafka. It does not call a brokerage trading API. Random delays and random price improvement have been removed.

1. Consume SUBMIT, CANCEL and REJECT commands from `orders`.
2. Consume sourced/timestamped quotes from `market-data` in its own consumer group.
3. Hold orders until a fresh quote is available and their original market/limit rules allow execution.
4. Publish receipt, waiting and matching stages to `order-lifecycle`, keyed by order ID.
5. Publish the full fill report to `executions`, keyed by account. Reports include the quote event ID, quote timestamp, bid, ask and source used.
6. Let the API settle cash, positions and order status atomically, then publish the final lifecycle events from its durable outbox.

See [the event-flow guide](../docs/event-flow.md) for correlation fields, the database migration, Kafka inspection and recovery behavior.

## Matching

Market buys use the ask and sells use the bid. Limit buys never exceed their submitted limit; limit sells never fall below it. Prices round to cents (buy up/sell down). Only ALPACA_IEX and ALPACA_SIP quotes are eligible. Quotes over 60 seconds old or over five seconds in the future cannot fill an order. Missing, crossed or zero quotes leave it waiting. The engine does not refresh a quote's original exchange timestamp.

The book is indexed by symbol, so a quote only visits that symbol's waiting orders. A bounded cache retains the last 10,000 completed/cancelled IDs. Duplicate submissions reuse the exact execution report. Durable settlement also ignores duplicates. Kafka delivery is acknowledged before listener completion; malformed messages go to dead-letter topics. Database PENDING orders are periodically requeued after an engine restart.

Run one local engine instance. Horizontal scaling needs shared or partition-aligned quote and order state. Whole-unit full fills are supported; partial fills, liquidity/queue position, fees and exchange session rules are not modelled.

## Run and verify

```bash
cd execution-engine
mvn test
mvn spring-boot:run
```

Tests include actual Kafka command/quote/fill traffic on an isolated embedded broker and deterministic matching, cancellation and retry cases. Production Kafka defaults to `localhost:9094`; Compose uses `kafka:9092`.

For an existing platform, follow [the coordinated migration/rebuild](../docs/event-flow.md#apply-locally). Rebuild both the API and engine; the old random-price engine is incompatible with the new quote-proof validation.
