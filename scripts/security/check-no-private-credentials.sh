#!/usr/bin/env bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

if git ls-files |
  grep -Eiq \
    '(^|/)(\.secrets/.*\.(json|pem|key|p12|pfx|jks|keystore)|google-services\.json|.*service-account.*\.json|.*firebase-admin.*\.json)$'
then
  echo >&2 "ERROR: Git tracks a forbidden credential file:"
  git ls-files |
    grep -Ei \
      '(^|/)(\.secrets/.*\.(json|pem|key|p12|pfx|jks|keystore)|google-services\.json|.*service-account.*\.json|.*firebase-admin.*\.json)$' \
      >&2
  exit 1
fi

if git grep -I -n -E \
  '"type"[[:space:]]*:[[:space:]]*"service_account"|"private_key"[[:space:]]*:|-----BEGIN PRIVATE'' KEY-----' \
  -- \
  ':!*.md' \
  ':!*.txt' \
  ':!*.example' \
  ':!*.sample'
then
  echo >&2 "ERROR: possible private credential found in tracked content"
  exit 1
fi

echo "OK: no tracked private credential files detected"
