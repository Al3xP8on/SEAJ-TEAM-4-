#!/usr/bin/env bash
# Scans the working tree for hardcoded secrets with Gitleaks.
# Rules and allowlist: .gitleaks.toml at the repo root.
#
# Usage: bash scripts/ci/secrets-scan.sh
# Exit:  0 no secrets, non-zero secrets found or the scan failed
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
REPORTS_DIR="${REPORTS_DIR:-security-reports}"
GITLEAKS_IMAGE="${GITLEAKS_IMAGE:-zricethezav/gitleaks:v8.24.3}"

mkdir -p "$ROOT/$REPORTS_DIR"

docker run --rm -u "$(id -u):$(id -g)" -v "$ROOT":/repo "$GITLEAKS_IMAGE" \
    dir /repo --config /repo/.gitleaks.toml --redact --no-banner \
    --report-format json --report-path "/repo/$REPORTS_DIR/gitleaks-report.json"

echo "✓ No secrets found"
