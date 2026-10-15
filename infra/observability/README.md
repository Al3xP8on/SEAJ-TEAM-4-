# Observability stack

OpenTelemetry Collector → Tempo (traces), Prometheus (metrics), Loki (logs), all viewed in Grafana.

## Run

The monitoring services live in the root `docker-compose.yml` behind the `observability` profile.
`.env` turns it on with `COMPOSE_PROFILES=observability`, so the normal commands include it:

```bash
docker-compose up -d --build   # app + monitoring
docker-compose down            # stop both
```

| UI / endpoint | URL |
|---|---|
| Grafana | http://10.9.65.95:8084 (`GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD`, default admin/admin) |
| Prometheus | http://10.9.65.95:8086 |
| OTLP gRPC (services) | `otel-collector:4317` in Docker, `localhost:4317` from host |
| OTLP HTTP (browser) | `localhost:4318` |

Ports 8084/8086 are used because the VM's AWS security group only allows the 808x–8100 range from the team network.
8085 is taken by the Angular dev server.

## Sending telemetry from a service

Point the service at the collector — it routes each signal to the right backend:

```
OTEL_SERVICE_NAME=<service-name>
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
```

## Layout

| File | Purpose |
|---|---|
| `otel-collector.yaml` | OTLP receivers, exporters to Tempo / Prometheus / Loki |
| `tempo.yaml` | Trace storage + metrics generator (service graph, span metrics → Prometheus) |
| `loki.yaml` | Log storage; OTLP ingestion keeps `trace_id` as structured metadata |
| `prometheus.yml` | Scrape targets (collector, kafka-exporter, stack itself) |
| `grafana/provisioning/` | Datasources (pre-linked trace ↔ logs ↔ metrics), dashboards, alerting |
| `grafana/dashboards/` | Dashboard JSON, loaded into the "SEAJ Trading" folder |
