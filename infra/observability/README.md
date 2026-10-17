# Observability stack

OpenTelemetry Collector → Tempo (traces), Prometheus (metrics), Loki (logs), all viewed in Grafana.

For what each service sends, how the dashboard and its SLOs work, and known limitations, see
[docs/observability.md](../../docs/observability.md). This README covers running the stack.

## Run

The monitoring services live in the root `docker-compose.yml` behind the `observability` profile.
`.env` turns it on with `COMPOSE_PROFILES=observability`, so the normal commands include it:

```bash
docker-compose up -d --build   # app + monitoring
docker-compose down            # stop both
```

### Monitoring only

To work on the stack without the app, SonarQube or the database, name the services. Compose starts only those and their dependencies:

```bash
docker-compose --profile observability up -d otel-collector tempo loki prometheus grafana
```

`tempo-init` also runs once to fix Tempo's volume permissions, then exits. To include Kafka consumer lag, add `kafka-exporter`; that also starts `kafka`, which it reads from.

There is no app data in this mode, only the stack's own health on the "Observability Stack Health" dashboard.

Stop them by name too. `docker-compose down` removes every container in the project, including app containers someone else is running on the VM:

```bash
docker-compose --profile observability stop otel-collector tempo loki prometheus grafana kafka-exporter
```

`--profile observability` is only needed if your `.env` doesn't set `COMPOSE_PROFILES=observability`.

### Minimal spin-up (low memory)

The smallest set that lets you use the app and see it in Grafana. The VM has about 7.7 GB, and editors
and other tools take a large part of it, so leave out what the dashboards don't need.

Before starting, stop the big processes that run outside Docker:

- **`ng serve`**: Ctrl+C in its terminal. It uses about 1 GB, and the `frontend` container below serves
  the same app on the same port, 8085, in about 10 MB. Only one of them can use 8085 at a time.
- **Jenkins**, if you're not running builds: `sudo systemctl stop jenkins` (about 550 MB).
  `sudo systemctl start jenkins` brings it back.

Then start the app and the core of the monitoring stack:

```bash
# App: database, auth, API, Kafka and the execution engine (which fills orders), plus the UI
docker-compose up -d db auth-service app kafka execution-engine frontend

# Monitoring: traces and metrics, viewed in Grafana (tempo-init runs once by itself)
docker-compose --profile observability up -d otel-collector tempo prometheus grafana
```

Add these only when a panel needs them:

```bash
docker-compose --profile observability up -d loki            # log panels (~100 MB)
docker-compose --profile observability up -d kafka-exporter  # Kafka consumer lag (~20 MB)
```

Leave out `kafka-ui` and `sonarqube`. The dashboards don't use them, and they have 384 MB and 2 GB limits.

Add `--build` to the first command after changing app code. Telemetry needs `OTEL_SDK_DISABLED=false`
in `.env`. Otherwise the services run but send nothing.

Open the app at http://10.9.65.95:8085 and Grafana at http://10.9.65.95:8084. Use the app for a few minutes:
load the dashboard, place and cancel orders, make a deposit. A wrong password on the login page
and an order larger than your cash balance give the error panels data.

Check memory with `docker stats --no-stream` and `free -m`. Stop everything by name when you're done:

```bash
docker-compose --profile observability stop otel-collector tempo loki prometheus grafana kafka-exporter
docker-compose stop frontend execution-engine app auth-service kafka db
```

| UI / endpoint | URL |
|---|---|
| Grafana | http://10.9.65.95:8084 (`GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD`, default admin/admin) |
| Prometheus | http://10.9.65.95:8086 |
| OTLP gRPC (services) | `otel-collector:4317` in Docker, `localhost:4317` from host |
| OTLP HTTP (browser) | `localhost:4318` |

Ports 8084/8086 are used because the VM's AWS security group only allows the 808x–8100 range from the team network.
8085 is the app's UI: the `frontend` container, or the Angular dev server (`ng serve`) if that's running instead.

## Sending telemetry from a service

Point the service at the collector — it routes each signal to the right backend:

```
OTEL_SERVICE_NAME=<service-name>
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
```

## Dashboards

Dashboards load automatically into the **SEAJ Trading** folder. **SEAJ Trading Platform**
(`seaj-platform.json`) is the main one, built for a mixed audience. Read it from the top down:

| Row | For | Shows |
|---|---|---|
| **Trading** | Everyone | Orders placed, % filled, rejected, value traded, average time to fill, orders over time |
| **Is the platform healthy?** | Everyone | The golden signals in plain words: requests per second (traffic), requests failing (errors), response time (latency), services running |
| **Reliability promise (last 30 days)** | Everyone | Availability against the SLO and SLA, allowed and used downtime (error budget), **Ship or stabilise?**, burn rate |
| **Investigate** (collapsed) | Technical | Recent warnings and errors, each linking to its trace, and a map of how the services connect |

How the numbers work (from the SRE module):

- **SLI, SLO, SLA.** Availability is the SLI. The SLO is our internal target, 99.9% (a dropdown at the top).
  The SLA line is the looser promise, 99.5%. Dipping below the SLO is the early warning, before the SLA is at risk.
- **Error budget** = (100% − SLO) × the 43,200 minutes in 30 days. At 99.9% that's 43.2 minutes. The reliability
  row always covers the last 30 days. Prometheus keeps 30 days of data for this.
- **Ship or stabilise?** reads *Ship* while budget is left and *Stabilise* once it's spent, when reliability work
  comes first.
- **Only platform failures count** (HTTP 5xx). A wrong password or an invalid order is the user's mistake (4xx),
  so it doesn't use up the budget.
- The trading and health rows follow the time picker, which defaults to today.

**Monitoring health** (`stack-health.json`, linked at the top right) checks the monitoring itself: scrape
targets and spans received by the collector.

To add panels, edit the JSON in `grafana/dashboards/`; Grafana picks the change up within a minute. Edits saved
in the Grafana UI are overwritten the next time the JSON file changes. For detail that would crowd the main
dashboard (JVM memory, database pool, Kafka lag, slowest endpoints), add a collapsed row at the bottom so the
top stays simple.

## Layout

| File | Purpose |
|---|---|
| `otel-collector.yaml` | OTLP receivers, exporters to Tempo / Prometheus / Loki |
| `tempo.yaml` | Trace storage + metrics generator (service graph, span metrics → Prometheus) |
| `loki.yaml` | Log storage; OTLP ingestion keeps `trace_id` as structured metadata |
| `prometheus.yml` | Scrape targets (collector, kafka-exporter, stack itself) |
| `grafana/provisioning/` | Datasources (pre-linked trace ↔ logs ↔ metrics), dashboards, alerting |
| `grafana/dashboards/` | Dashboard JSON, loaded into the "SEAJ Trading" folder |
