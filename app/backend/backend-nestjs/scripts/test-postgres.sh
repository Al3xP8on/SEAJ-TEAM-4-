#!/usr/bin/env bash
# Creates a disposable local PostgreSQL cluster and runs authentication HTTP integration tests.
# Uses isolated test configuration and removes the temporary cluster on exit; never reads application .env.
set -euo pipefail
cd "$(dirname "$0")/.."
auth_test_dir=$(mktemp -d /tmp/seaj-auth-pg.XXXXXX)
auth_test_port=${AUTH_TEST_PORT:-55432}
cleanup() {
    pg_ctl -D "$auth_test_dir/data" -m immediate stop >/dev/null 2>&1 || true
    rm -rf "$auth_test_dir"
}
trap cleanup EXIT
initdb -D "$auth_test_dir/data" -A trust -U seaj_test --no-locale --encoding=UTF8 > "$auth_test_dir/init.log"
pg_ctl -D "$auth_test_dir/data" -l "$auth_test_dir/server.log" -o "-p $auth_test_port -k $auth_test_dir -h 127.0.0.1" start
psql "postgresql://seaj_test@127.0.0.1:$auth_test_port/postgres" -v ON_ERROR_STOP=1 -c 'CREATE DATABASE seaj_auth_test'
AUTH_TEST_DATABASE_URL="postgresql://seaj_test@127.0.0.1:$auth_test_port/seaj_auth_test" npm run test:e2e -- --runInBand
