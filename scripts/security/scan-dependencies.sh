#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT_DIR="$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)"
INVENTORY="$ROOT_DIR/app/build/reports/security/osv-scanner.json"

if ! command -v osv-scanner >/dev/null 2>&1; then
    echo "osv-scanner is required (v2.3.8 or newer)." >&2
    exit 2
fi

"$ROOT_DIR/gradlew" :app:exportOsvDependencyInventory --no-configuration-cache

test -s "$INVENTORY" || {
    echo "Gradle did not produce the OSV inventory: $INVENTORY" >&2
    exit 1
}

osv-scanner scan -L "osv-scanner:$INVENTORY" --format=vertical
