#!/usr/bin/env bash
# Static security analysis of the Java backend with SpotBugs + FindSecBugs.
# Analyses already-compiled classes; rules are limited to security bugs
# (spotbugs-security-include.xml / spotbugs-security-exclude.xml).
#
# Usage: bash scripts/ci/sast.sh   (after: mvn -B clean package -DskipTests)
# Exit:  0 no findings, non-zero findings or the scan failed
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKEND="$ROOT/app/backend/backend-java"
REPORTS_DIR="${REPORTS_DIR:-security-reports}"

if [ ! -d "$BACKEND/target/classes" ]; then
    echo "No compiled classes in $BACKEND/target, build first: mvn -B clean package -DskipTests" >&2
    exit 2
fi

mkdir -p "$ROOT/$REPORTS_DIR"

cd "$BACKEND"
mvn -B spotbugs:check
status=$?

# Copy the report whether or not there were findings
cp target/spotbugsXml.xml "$ROOT/$REPORTS_DIR/spotbugs-report.xml" 2>/dev/null || true

[ "$status" -eq 0 ] && echo "✓ No security bugs found"
exit "$status"
