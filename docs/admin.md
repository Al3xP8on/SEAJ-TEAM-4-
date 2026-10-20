# Admin account management

After login, admins land on `/admin`; regular users keep the personal overview at `/dashboard`. The shared navigation gives admins an **Admin dashboard**, **My portfolio**, and **Orders**. Normal users do not see administration, and cannot open the admin route or call its API.

The dashboard has three sections:

- **Overview:** account totals, cash held, account-status distribution, suspended-account follow-up, recently updated account records and a compact operational view.
- **Accounts:** the searchable account directory, account creation and existing access/closure controls, holdings, orders and the administrator audit trail.
- **Observability:** request traffic, HTTP server errors, service response times, a one-hour traffic chart, trading telemetry, Kafka consumer lag, monitoring scrape status and Grafana investigation links.

Account activity on the overview means account-record updates (including cash and status changes), not login history. Account details and audit records are fetched on selection. A failed audit request is shown with a retry action, rather than as an empty history, and selecting a different account cancels the previous history request.

The dashboard supports searching by account holder, username, email or account ID, filtering account status, and viewing cash balances, holdings at cost and the selected account's complete order list. Account details load only when selected. Refreshing is manual; the page does not poll every account's portfolio.

## Account actions

- **Suspend** blocks login, token refresh, authenticated Java API requests and protected auth-service requests, including sessions issued before suspension. New orders and settlement also check account status. Balances, holdings and order history are preserved. Pending orders are not cancelled by suspension; an execution received while suspended is rejected by settlement through the existing Kafka lifecycle.
- **Reactivate** restores access to a suspended account. Closed accounts cannot be reopened.
- **Close** requires zero cash, no position with a positive quantity and no NEW or PENDING orders. It marks the account CLOSED and preserves its records. Withdraw cash, sell holdings and cancel pending orders before closing; reactivate a suspended account first if the holder needs to do this.
- **Create account** uses the admin registration and email-verification endpoints. Enter the holder's name, username, email and initial password, choose User or Administrator access, then confirm the code emailed to the holder. No account is created before verification. The default role is USER and the starting balance is $0; the holder can deposit through My Account. Creating an account does not replace the administrator's login session. Public registration remains restricted to USER access.

Status changes require confirmation. An admin cannot change their own account status. There is no role promotion or hard-delete action.

## API and concurrency

`GET /api/v1/admin/accounts` returns a dedicated response containing identity, contact email, roles, cash, status and last-update time. It excludes credentials, sessions and payment details.

`PUT /api/v1/admin/accounts/{numericId}/status` accepts `{"status":"ACTIVE"}`, `{"status":"SUSPENDED","reason":"Account review"}` or `{"status":"CLOSED","reason":"Closure requested"}`. Both endpoints require the ADMIN role on the server. Status updates hold the same account row lock used by cash transfers, order submission and settlement. Ineligible transitions return HTTP 409 with the reason; missing accounts return 404. Repeating an already-applied status is safe.

The status audit trail requires `infra/db/migrations/03-account-status-history.sql`. New database containers apply it during initialization; existing database volumes need the migration applied once. Rebuilding a database container does not rerun initialization on an existing volume. No new environment variables are required. Rebuild/restart the Java backend and auth service to apply the API and session checks. With the Angular dev server running, frontend changes reload automatically.

Suspending or closing an account also requires a reason (maximum 500 characters). The status update and audit record commit in one service transaction, so an audit failure rolls back the account change. `AdminAccountTransactionTest` checks this against an isolated test database without a surrounding test transaction.

## Verification

`AdminAccountControllerTest` exercises the real Spring JWT filter and method security with signed test tokens: anonymous/USER denial, safe admin responses, status transitions, closure preconditions, self-protection, invalid input and rejection of tokens after suspension. The Nest JWT guard tests cover existing sessions for suspended, closed and missing accounts. Angular tests cover role routing/navigation, account filters/details, confirmation and failure states, duplicate submission prevention and verified account creation.

## Integrated observability

`GET /api/v1/admin/observability` is an admin-only, read-only summary of the existing Prometheus data. The Java API runs five fixed queries: service signals, scrape targets, consumer lag, trading metrics and a one-hour traffic range. It accepts no arbitrary query or destination URL from the browser. The snapshot, including an unavailable response, is cached for 30 seconds across admin sessions. HTTP connections/read waits are bounded; the first failed query short-circuits an unavailable monitoring stack.

The browser polls every 30 seconds while visible, pauses when hidden, and prevents overlapping refresh requests. Charts use at most 61 returned samples, render as SVG, and break across missing intervals. No additional chart library is loaded. Monitoring failure does not disable account management, and failed refreshes discard old live readings rather than presenting them as current.

Request rates and HTTP 5xx shares use five-minute windows. The response-time summary is the **slowest service's p95**, not a platform-wide percentile. Trading statistics use the last hour and Prometheus counter increases, so they are telemetry estimates rather than ledger totals. Missing/non-finite signals show `—`; a missing consumer-lag sample is not zero lag. Service telemetry presence is not an external uptime probe, and no SLO, SLA or error budget is inferred. Collector scrape failure suppresses live-looking service values.

The monitoring stack still uses the existing `observability` Compose profile and `OTEL_SDK_DISABLED=false`. No monitoring containers are started by the dashboard. Run the services following [the observability README](../infra/observability/README.md), then rebuild the Java API for the new endpoint. Existing dashboards and storage configuration remain in place.

Optional configuration:

- `OBSERVABILITY_PROMETHEUS_URL` defaults to `http://prometheus:9090` inside Docker. Use `http://localhost:8086` when running Java directly on the laptop.
- `OBSERVABILITY_GRAFANA_URL` is the public browser URL for Grafana, including any subpath. When empty, links use the app's hostname on port 8084.

Logs and traces are opened in the provisioned Grafana panels (Loki warnings/errors and Tempo service connections); monitoring-stack and Kafka panels link to `seaj-stack-health`. Grafana keeps its own authentication. No Grafana credentials or application tokens are embedded in these links, and this integration does not enable anonymous access or iframe embedding.

`AdminObservabilityServiceTest` covers parsing, units, missing and non-finite samples, partial failure, cache expiry, disabled monitoring and stale-value removal. The real Spring security tests cover anonymous/USER rejection and ADMIN access. UI tests cover section navigation, audit-history races/retry, polling visibility, weighted errors, chart gaps and safe Grafana links.
