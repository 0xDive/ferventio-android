#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT_DIR="$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)"

python3 "$SCRIPT_DIR/check-repository-secrets.py" --root "$ROOT_DIR"
python3 "$SCRIPT_DIR/test-check-repository-secrets.py"

if ! command -v gitleaks >/dev/null 2>&1; then
    echo "gitleaks is required for Git history scanning." >&2
    echo "macOS: brew install gitleaks" >&2
    echo "Other platforms: install gitleaks v8.30.1 or newer from the official release." >&2
    exit 2
fi

if git -C "$ROOT_DIR" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    gitleaks git \
        --redact=100 \
        --no-banner \
        --config "$ROOT_DIR/.gitleaks.toml" \
        "$ROOT_DIR"
else
    echo "Warning: no Git metadata found; Gitleaks scans the current directory only." >&2
    gitleaks dir \
        --redact=100 \
        --no-banner \
        --config "$ROOT_DIR/.gitleaks.toml" \
        "$ROOT_DIR"
fi
