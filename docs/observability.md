# Observability and the SEAJ dashboard

What we built for monitoring the SEAJ trading platform, why it's built this way, and what it can't yet tell
us. For starting the stack, see [infra/observability/README.md](../infra/observability/README.md).

## Summary

Every backend service sends **traces, metrics and logs** to one OpenTelemetry Collector. The collector forwards
them to Tempo, Prometheus and Loki, and Grafana shows them on one dashboard: **SEAJ Trading Platform**.

The dashboard is for a mixed audience. Read it top to bottom:

1. **Trading**: what customers are doing (orders, fills, value traded).
2. **Is the platform healthy?**: the four golden signals, in plain words.
3. **Investigate** (collapsed): logs and traces for the technical audience.

SLIs and SLOs are deliberately **not on the dashboard yet**. See [SLOs: not yet](#slos-not-yet).

## How telemetry flows

```mermaid
flowchart LR
    subgraph Services
        A[app<br/>Spring Boot + Java agent]
        E[execution-engine<br/>Spring Boot + Java agent]
        U[auth-service<br/>NestJS + OpenTelemetry SDK]
    end
    A & E & U -- OTLP push :4317 --> C[OpenTelemetry Collector]
    C -- traces --> T[Tempo]
    C -- logs --> L[Loki]
    P[Prometheus] -- scrapes every 15 s --> C
    T -- span metrics + service graph --> P
    G[Grafana] -- queries on demand --> P & T & L
    K[kafka-exporter] -. consumer lag .-> P
```

- **Services push** to the collector. Prometheus **pulls** metrics from the collector. Grafana stores nothing
  and asks each backend whenever a panel loads.
- Every log line carries the **trace ID** of the request it came from, so Grafana links log → trace → log.
- Telemetry is switched on by `OTEL_SDK_DISABLED=false` in `.env`. With it off, the services run as normal and
  send nothing.

| File | Role |
|---|---|
| `app/backend/backend-java/Dockerfile`, `execution-engine/Dockerfile` | Copy the OpenTelemetry Java agent into the image |
| `app/backend/backend-nestjs/src/observability/` | Starts the Node SDK, sends Nest logs to the collector, logs one line per request |
| `docker-compose.yml` | Turns the agent/SDK on, names each service, points it at the collector |
| `infra/observability/otel-collector.yaml` | Receives everything; routes traces → Tempo, logs → Loki, metrics → Prometheus |
| `infra/observability/tempo.yaml`, `loki.yaml`, `prometheus.yml` | Storage and retention (Tempo 48 h, Loki 7 days, Prometheus 7 days) |
| `infra/observability/grafana/provisioning/` | Data sources (pre-linked trace ↔ logs ↔ metrics) and dashboard loading |
| `infra/observability/grafana/dashboards/seaj-platform.json` | The dashboard |

## What each service sends

| Service | Traces | Metrics | Logs |
|---|---|---|---|
| **app** | Every API request, with its database queries and Kafka messages | HTTP rate and latency, JVM, connection pool, plus **business metrics**: orders placed, completed (filled / rejected / cancelled), time to fill, value traded | Application logs (account IDs masked) |
| **execution-engine** | Kafka consume and produce, database calls | HTTP, JVM, Kafka client | One line per order decision: received, waiting (with reason), filled, cancelled. No account IDs. |
| **auth-service** | Every request, with its Postgres queries | HTTP rate and latency (same metric names as Java) | One line per request (`POST /auth/login 401 12.3ms`), and 500 errors with their cause |
| **Kafka** (via kafka-exporter) | No | Consumer lag per group and topic | No |

Nothing records headers, request bodies or query strings, so passwords, codes, tokens and card numbers stay out
of telemetry.

**Not monitored:** nginx (frontend), the Postgres server itself and the Kafka broker. Their output stays in
`docker logs`. Request traces already show the database and Kafka time from the services' side.

## The dashboard

**Grafana:** http://10.9.65.95:8084 → *SEAJ Trading* folder → *SEAJ Trading Platform*. Defaults: today, refresh
every 30 s. The top bar has a **Service** filter.

### 1. Trading

| Panel | Meaning |
|---|---|
| Orders placed | Orders customers placed in the time range |
| Orders filled | Share of finished orders that filled (the rest were rejected or cancelled) |
| Orders rejected | Orders the platform turned down, for example for insufficient cash |
| Value traded | Quantity × price of filled orders |
| Average time to fill | Placed → filled. Limit orders wait for their price, so they pull this up. |
| Orders over time | Placed, filled, rejected and cancelled per interval |

These come from the business metrics in `OrderTelemetry.java` in app. Fills only happen in market hours, because
the engine fills against an Alpaca quote under 60 seconds old.

### 2. Is the platform healthy? (golden signals)

| Golden signal | Panel | Thresholds |
|---|---|---|
| Traffic | Requests per second | – |
| Errors | Requests failing (HTTP 5xx share) | Amber 1%, red 5% |
| Latency | Response time (p95: 95% of requests are faster) | Amber 300 ms, red 1 s |
| Saturation | Services running (3 of 3) | Red below 2, amber at 2 |

Charts below show traffic and response time per service. Click a line to filter to that service. The dots on
the response-time chart are sample requests; click one to open its trace.

### 3. Investigate (collapsed)

- **Recent warnings and errors** from all services, newest first. Expand a line and click **View trace**.
- **How the services connect**: the service map Tempo builds from traces.

## SLOs: not yet

A first version had a 30-day SLO row: availability SLI, a 99.9% SLO, a 99.5% SLA, a 43.2-minute error budget
and a "ship or stabilise?" indicator. We took it out until it has a clear purpose:

- **SLA:** SEAJ has no external party to promise anything to, so an SLA would be made up.
- **SLO:** the SRE module says the target and the budget policy are set by the team's own prior agreement. We
  haven't agreed on either; 99.9% was only the module's example.
- **SLI:** platform-wide availability is dominated by background polling (the dashboard page refreshes every 2 s).
  A service that's completely down also stops reporting, so it records no failures. The number would look
  precise without meaning much.

The health row still shows **Requests failing** (the errors golden signal), so failures remain visible.

**When we come back to it,** start here:

1. **Pick SLIs for critical user journeys,** not the whole platform. For example:
   - placing an order: share of `POST /api/v1/orders` that don't return 5xx
   - logging in: share of `/auth/login/verify` that succeed
   - order fills: share filled within N seconds during market hours
2. **Agree a target and a budget policy as a team:** what the SLO is, and what we do when the budget is spent.
3. **Cover the "service down" blind spot** with a check from outside the service. For example, the Prometheus
   blackbox exporter calling each service's health endpoint.
4. **Then** add the error budget in minutes (100% − SLO) × 43,200 and a burn-rate indicator. Raise Prometheus
   retention to 30 days (`--storage.tsdb.retention.time` in `docker-compose.yml`) so the window has data.

## What was done

| Change | Where |
|---|---|
| Monitoring stack in Docker: collector, Tempo, Loki, Prometheus, Grafana, kafka-exporter, behind the `observability` profile | `docker-compose.yml`, `infra/observability/` |
| OpenTelemetry Java agent in app and execution-engine | Their Dockerfiles, `x-otel-java` in compose |
| Business metrics for the order lifecycle | `app/.../observability/OrderTelemetry.java` |
| Traces, metrics and logs for auth-service (it sent nothing before) | `backend-nestjs/src/observability/`, `main.ts` |
| 500 errors in auth-service now logged with their cause (users still get a generic message) | `backend-nestjs/src/http.config.ts` |
| Order decision logging in the execution engine (it had none) | `execution-engine/.../OrderListener.java` |
| Memory fixes: Tempo was killed for running out of memory. Smaller trace blocks, Go memory limits on Tempo, Loki and Prometheus, a smaller collector queue, and Tempo raised to 512 MB | `tempo.yaml`, `otel-collector.yaml`, `docker-compose.yml` |
| The **SEAJ Trading Platform** dashboard | `grafana/dashboards/seaj-platform.json` |
| Low-memory start-up instructions | `infra/observability/README.md` |

### How it was checked

- Every dashboard query was run against Prometheus with live data. Trading, health, reliability and log panels
  all return data.
- **Orders filled** and **Average time to fill** still need a check during market hours, because no order has
  filled yet.
- auth-service has 5 new tests and the execution engine 4 new ones; all existing tests still pass.

## Extending the dashboard

- Edit `infra/observability/grafana/dashboards/seaj-platform.json`. Grafana reloads it within a minute. Changes
  saved in the Grafana UI are overwritten the next time the file changes, so copy them into the JSON
  (Dashboard → Share → Export).
- Keep the top three rows simple. Put technical detail in a **collapsed row at the bottom**. Candidates:
  - JVM memory: `jvm_memory_used_bytes`
  - database pool: `db_client_connection_count`
  - Kafka lag: `kafka_consumergroup_lag`
  - slowest endpoints: `http_server_request_duration_seconds` by `http_route`
- Use `$__rate_interval` in `rate()` queries, set a unit and a plain-language description on every panel, and
  filter by `service_name=~"$service"` so the Service dropdown works.
