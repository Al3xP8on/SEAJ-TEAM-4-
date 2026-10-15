#!/usr/bin/env bash
# Exports the trading API's OpenAPI spec from the running Spring Boot app (springdoc) into the repo,
# so the frontend's typed client can be generated from it. The work is done by export_openapi.py.
#
# Usage: bash scripts/openapi/export-openapi.sh
#        API_URL=http://localhost:8081/api bash scripts/openapi/export-openapi.sh
# Needs: the backend running (docker-compose up -d kafka app), python3
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

exec python3 "$SCRIPT_DIR/export_openapi.py" "$@"
