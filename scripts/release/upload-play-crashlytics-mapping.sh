#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$PROJECT_ROOT"

usage() {
  cat >&2 <<'USAGE'
Usage:
  FERVENTIO_FIREBASE_APPLICATION_ID=1:...:android:... \
  FERVENTIO_CRASHLYTICS_MAPPING_ID=<same-id-used-for-build> \
  ./scripts/release/upload-play-crashlytics-mapping.sh [mapping.txt]
USAGE
  exit 64
}

[[ $# -le 1 ]] || usage
command -v firebase >/dev/null 2>&1 || {
  echo "Firebase CLI is required: https://firebase.google.com/docs/cli" >&2
  exit 65
}

read_property() {
  local key="$1" file value
  for file in "gradle.properties" "${HOME:-}/.gradle/gradle.properties"; do
    [[ -f "$file" ]] || continue
    value="$(sed -nE "s/^[[:space:]]*${key}[[:space:]]*=[[:space:]]*(.*)$/\1/p" "$file" | tail -n1)"
    if [[ -n "$value" ]]; then
      printf '%s' "$value"
      return 0
    fi
  done
  return 1
}

APP_ID="${FERVENTIO_FIREBASE_APPLICATION_ID:-}"
if [[ -z "$APP_ID" ]]; then
  APP_ID="$(read_property FERVENTIO_FIREBASE_APPLICATION_ID || true)"
fi
MAPPING_ID="${FERVENTIO_CRASHLYTICS_MAPPING_ID:-}"
if [[ -z "$MAPPING_ID" && -f app/build/outputs/mapping/playRelease/mapping-id.txt ]]; then
  MAPPING_ID="$(tr -d '[:space:]' < app/build/outputs/mapping/playRelease/mapping-id.txt)"
fi
MAPPING_FILE="${1:-app/build/outputs/mapping/playRelease/mapping.txt}"

[[ -n "$APP_ID" && -n "$MAPPING_ID" ]] || usage
MAPPING_ID="$(printf '%s' "$MAPPING_ID" | tr -d '[:space:]')"
if [[ ! "$MAPPING_ID" =~ ^[0-9a-f]{32}$ ]]; then
  if [[ "$MAPPING_ID" =~ ^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$ ]]; then
    echo "The release was built with a hyphenated UUID mapping ID, which Crashlytics upload rejects." >&2
    echo "Rebuild the Play release with ./scripts/build/build-release.sh before uploading mapping.txt." >&2
  else
    echo "FERVENTIO_CRASHLYTICS_MAPPING_ID must be exactly 32 lowercase hexadecimal characters." >&2
  fi
  exit 66
fi
[[ -f "$MAPPING_FILE" ]] || {
  echo "Mapping file not found: $MAPPING_FILE" >&2
  exit 67
}

RESOURCE_FILE="$(mktemp "${TMPDIR:-/tmp}/ferventio-crashlytics.XXXXXX.xml")"
trap 'rm -f -- "$RESOURCE_FILE"' EXIT
cat > "$RESOURCE_FILE" <<XML
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="com.google.firebase.crashlytics.mapping_file_id" translatable="false">$MAPPING_ID</string>
</resources>
XML

firebase crashlytics:mappingfile:upload \
  --app="$APP_ID" \
  --resource-file="$RESOURCE_FILE" \
  "$MAPPING_FILE"
