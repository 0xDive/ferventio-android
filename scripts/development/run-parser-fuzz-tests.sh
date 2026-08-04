#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$PROJECT_ROOT"

./gradlew \
  :core:domain:testDebugUnitTest \
  --tests '*FuzzTest' \
  --no-configuration-cache

./gradlew \
  :app:testFossDebugUnitTest \
  :app:testPlayDebugUnitTest \
  --tests '*FuzzTest' \
  --no-configuration-cache
