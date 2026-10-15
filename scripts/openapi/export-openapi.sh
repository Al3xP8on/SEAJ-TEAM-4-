#!/usr/bin/env bash
# Exports the trading API's OpenAPI spec from the running Spring Boot app (springdoc) into the repo,
# so the frontend's typed client can be generated from it.
# refer to export_openapi.py for logic.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

exec python3 "$SCRIPT_DIR/export_openapi.py" "$@"
