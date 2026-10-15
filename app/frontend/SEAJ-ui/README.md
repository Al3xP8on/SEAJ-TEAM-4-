<!-- Explains how to run, package and test the Angular authentication interface.
     Records the preserved email verification/session flow and the API proxy setup. -->
# SEAJ Angular authentication UI

Use Node.js 24 and install dependencies with `npm ci` in this directory.

## Development

Start PostgreSQL and the configured NestJS authentication service first. Then:

```bash
npm start
```

On the Linux VM, open http://10.9.71.121:8085/login from your browser. The dev
server listens on all VM interfaces on port 8085 and automatically reloads
the browser when frontend files change. Keep `npm start` running.
`proxy.conf.json` forwards `/auth/**` to the
local NestJS service on port 3000 and `/api/**` to the Java API on port 8081.
The browser uses relative API URLs, so remote development does not require
hardcoded server addresses. Your computer must have a route to the VM's private
network (for example, through the work VPN), and inbound TCP 8085 must be allowed
from your network. Alternatively, forward port 8085 through SSH or your IDE and
open http://localhost:8085/login. Change proxy targets only if the backend
services run on another host; `127.0.0.1` here refers to the VM running Angular.

## Preserved authentication behavior

- Required unique username/email registration starts an email challenge (`202`).
- Only a correct registration code creates the account (`201`) with the backend's
  existing UUID-based ACC identifier. Registration does not issue login tokens.
- Username/password login starts a fresh email challenge (`202`). Only a correct
  login code returns tokens (`200`). Tokens are never displayed in the page.
- Access and rotating refresh tokens use the same `sessionStorage` keys:
  `seaj.access_token` and `seaj.refresh_token`.
- Access tokens renew automatically before expiry, including after reload and
  browser wake-up. Rejected refresh tokens clear the session and prompt login.
- Logout remains local: it clears tokens, pending challenges and timers. The
  separate API logout endpoint retains its existing server revocation behavior.
- Cancellation prevents late responses from restoring authentication.

`src/app/auth/session-client.mjs` is the previous session client relocated as an
ES module; its business logic is unchanged. The adjacent declaration file types
its interface for Angular. `auth.service.ts` adapts its requests to HttpClient;
`login/` provides Angular forms and status messages. The old standalone HTML/JS
screens have been removed.

## Build and deployment

```bash
npm run build
```

The production UI is emitted to `dist/seaj-ui/browser`. The auth Dockerfile builds
Angular first, then packages it with NestJS and `docs/auth-service.yml`. From the
repository root:

```bash
docker-compose up -d --build db auth-service
```

Open http://10.9.71.121:3000/login (Swagger is at
http://10.9.71.121:3000/api/docs). NestJS listens on all interfaces and Docker
publishes port 3000. Access requires a route to the VM's private network and
inbound TCP 3000 allowed from your network. Existing `/login/Login.html`
bookmarks redirect through the Angular router. Alternatively, forward port
3000 through SSH or your IDE and open http://localhost:3000/login.
The packaged UI requires a rebuild for frontend changes; use port 8085 during
development for automatic browser refresh. An API-only local NestJS build can run
without UI artifacts; use the Angular dev server in that case.

The additive `align-account-roles.migration.ts` startup migration supports older
accounts tables that lack the merged Java `roles` column. It copies legacy primary
roles only when adding the column and preserves existing merged role lists.

## Tests

From this directory:

```bash
npm test -- --watch=false
npm run build
```

### SonarQube coverage

Generate coverage before scanning. SonarQube imports the LCOV report; running
tests without coverage does not populate its coverage metrics.

```bash
npm run test:coverage
read -rsp "SonarQube token: " SQ_SCAN_TOKEN
echo
env -u SONAR_TOKEN npx --yes @sonar/scan \
  -Dsonar.host.url=http://localhost:8088 \
  -Dsonar.login="$SQ_SCAN_TOKEN" \
  -Dsonar.qualitygate.wait=true
unset SQ_SCAN_TOKEN
```

The project settings in `sonar-project.properties` import
`coverage/SEAJ-ui/lcov.info`. Reports are ignored by Git. The scan fails the
quality gate if measured coverage is below the configured threshold, even when
all tests pass. Review any security hotspots separately in SonarQube.

From repository root, run the preserved browser-session regression tests:

```bash
node app/frontend/test/auth-service.test.mjs
```

The Angular tests use HttpClient's test backend and fake tokens. NestJS HTTP tests
use disposable PostgreSQL and fake email delivery. Verify real Gmail delivery
manually using private SMTP credentials: register, enter the registration code,
log in and enter the second code. Neither the server nor client should expose a
code or password in API responses.

To check automatic renewal quickly, temporarily configure `JWT_EXPIRY=60` and
`JWT_REFRESH_EXPIRY=180`, recreate auth-service and log in again. Network should
show `/auth/refresh` around 48 seconds later and both stored tokens should rotate.
When the absolute refresh session expires, the next renewal prompts login.
Restore the normal lifetimes after testing.
