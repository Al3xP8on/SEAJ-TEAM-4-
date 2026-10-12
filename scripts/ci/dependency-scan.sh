#!/usr/bin/env bash
# Scans the Java backend's built jar for vulnerable dependencies with Trivy.
# Accepted CVEs go in app/backend/backend-java/.trivyignore.
#
# Exit:  0 clean
#        1 fixable vulnerabilities at TRIVY_SEVERITY found
#        2 the scan itself failed (no jar, DB download error, ...)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKEND="$ROOT/app/backend/backend-java"
JAR=team-skeleton.jar
REPORTS_DIR="${REPORTS_DIR:-security-reports}"
TRIVY_IMAGE="${TRIVY_IMAGE:-aquasec/trivy:0.69.1}"
TRIVY_SEVERITY="${TRIVY_SEVERITY:-HIGH,CRITICAL}"

if [ ! -f "$BACKEND/target/$JAR" ]; then
    echo "No $JAR in $BACKEND/target, build first: mvn -B clean package -DskipTests" >&2
    exit 2
fi

mkdir -p "$ROOT/$REPORTS_DIR"
REPORTS="$ROOT/$REPORTS_DIR"

trivy() {
    docker run --rm -v trivy-cache:/root/.cache -v "$BACKEND/target":/scan:ro \
        -v "$BACKEND/.trivyignore":/.trivyignore:ro \
        "$TRIVY_IMAGE" rootfs --scanners vuln --ignorefile /.trivyignore --quiet "$@"
}

# The first run refreshes the vulnerability DB; the later runs reuse it
trivy --format json "/scan/$JAR" > "$REPORTS/trivy-report.json" \
    || { echo "Trivy scan failed" >&2; exit 2; }
trivy --skip-db-update --skip-java-db-update \
    --format template --template '@contrib/html.tpl' "/scan/$JAR" > "$REPORTS/trivy-report.html" \
    || { echo "Trivy HTML report failed" >&2; exit 2; }

# Gate: only fixable findings at the chosen severities count. Exit code 10 marks
# findings, so they can't be confused with Trivy itself failing.
gate=0
trivy --skip-db-update --skip-java-db-update \
    --severity "$TRIVY_SEVERITY" --ignore-unfixed --exit-code 10 "/scan/$JAR" || gate=$?
case $gate in
    0)  echo "✓ No fixable $TRIVY_SEVERITY vulnerabilities" ;;
    10) echo "Fixable $TRIVY_SEVERITY vulnerabilities found, see $REPORTS_DIR/trivy-report.html" >&2; exit 1 ;;
    *)  echo "Trivy gate failed to run" >&2; exit 2 ;;
esac
