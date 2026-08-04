#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
  echo "Usage: $0 host[:port]" >&2
  exit 64
}

[[ $# -eq 1 ]] || usage
command -v openssl >/dev/null 2>&1 || {
  echo "openssl is required" >&2
  exit 65
}

TARGET="$1"
HOST="${TARGET%%:*}"
PORT="${TARGET##*:}"
if [[ "$TARGET" == "$HOST" ]]; then
  PORT="443"
fi
[[ -n "$HOST" && "$PORT" =~ ^[0-9]+$ ]] || usage

TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/ferventio-spki.XXXXXX")"
trap 'rm -rf -- "$TMP_DIR"' EXIT

openssl s_client \
  -servername "$HOST" \
  -connect "$HOST:$PORT" \
  -showcerts \
  </dev/null 2>/dev/null \
  | awk -v dir="$TMP_DIR" '
      /-----BEGIN CERTIFICATE-----/ { n++; file=sprintf("%s/cert-%02d.pem", dir, n) }
      file != "" { print > file }
      /-----END CERTIFICATE-----/ { close(file); file="" }
    '

shopt -s nullglob
certificates=("$TMP_DIR"/cert-*.pem)
((${#certificates[@]} > 0)) || {
  echo "No TLS certificates received from $HOST:$PORT" >&2
  exit 66
}

for certificate in "${certificates[@]}"; do
  subject="$(openssl x509 -in "$certificate" -noout -subject | sed 's/^subject=//')"
  pin="$(
    openssl x509 -in "$certificate" -pubkey -noout \
      | openssl pkey -pubin -outform DER 2>/dev/null \
      | openssl dgst -sha256 -binary \
      | openssl base64 -A
  )"
  printf '%s=sha256/%s  # %s\n' "$HOST" "$pin" "$subject"
done
