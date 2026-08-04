#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"

"$SCRIPT_DIR/scan-repository-secrets.sh"
"$SCRIPT_DIR/scan-dependencies.sh"
