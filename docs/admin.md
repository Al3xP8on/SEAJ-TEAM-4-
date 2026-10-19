# Admin account management

After login, admins land on `/admin`; regular users keep the personal overview at `/dashboard`. The shared navigation gives admins an **Admin dashboard**, **My portfolio**, and **Orders**. Normal users do not see administration, and cannot open the admin route or call its API.

The dashboard supports searching by account holder, username, email or account ID, filtering account status, and viewing cash balances, holdings at cost and the selected account's complete order list. Account details load only when selected. Refreshing is manual; the page does not poll every account's portfolio.

## Account actions

- **Suspend** blocks login, token refresh, authenticated Java API requests and protected auth-service requests, including sessions issued before suspension. New orders and settlement also check account status. Balances, holdings and order history are preserved. Pending orders are not cancelled by suspension; an execution received while suspended is rejected by settlement through the existing Kafka lifecycle.
- **Reactivate** restores access to a suspended account. Closed accounts cannot be reopened.
- **Close** requires zero cash, no position with a positive quantity and no NEW or PENDING orders. It marks the account CLOSED and preserves its records. Withdraw cash, sell holdings and cancel pending orders before closing; reactivate a suspended account first if the holder needs to do this.
- **Create account** uses the existing registration and email-verification endpoints. Enter the holder's name, username, email and initial password, then confirm the code emailed to the holder. No account is created before verification. New accounts receive USER access and a $0 starting balance; the holder can deposit through My Account. Creating an account does not replace the administrator's login session.

Status changes require confirmation. An admin cannot change their own account status. There is no role promotion or hard-delete action.

## API and concurrency

`GET /api/v1/admin/accounts` returns a dedicated response containing identity, contact email, roles, cash, status and last-update time. It excludes credentials, sessions and payment details.

`PUT /api/v1/admin/accounts/{numericId}/status` accepts `{"status":"ACTIVE"}`, `{"status":"SUSPENDED","reason":"Account review"}` or `{"status":"CLOSED","reason":"Closure requested"}`. Both endpoints require the ADMIN role on the server. Status updates hold the same account row lock used by cash transfers, order submission and settlement. Ineligible transitions return HTTP 409 with the reason; missing accounts return 404. Repeating an already-applied status is safe.

The status audit trail requires `infra/db/migrations/03-account-status-history.sql`. New database containers apply it during initialization; existing database volumes need the migration applied once. Rebuilding a database container does not rerun initialization on an existing volume. No new environment variables are required. Rebuild/restart the Java backend and auth service to apply the API and session checks. With the Angular dev server running, frontend changes reload automatically.

Suspending or closing an account also requires a reason (maximum 500 characters). The status update and audit record commit in one service transaction, so an audit failure rolls back the account change. `AdminAccountTransactionTest` checks this against an isolated test database without a surrounding test transaction.

## Verification

`AdminAccountControllerTest` exercises the real Spring JWT filter and method security with signed test tokens: anonymous/USER denial, safe admin responses, status transitions, closure preconditions, self-protection, invalid input and rejection of tokens after suspension. The Nest JWT guard tests cover existing sessions for suspended, closed and missing accounts. Angular tests cover role routing/navigation, account filters/details, confirmation and failure states, duplicate submission prevention and verified account creation.
