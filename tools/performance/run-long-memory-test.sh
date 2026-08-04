#!/usr/bin/env bash
set -euo pipefail

PACKAGE="${PACKAGE:-io.ferventio.app}"
ACTIVITY="${ACTIVITY:-io.ferventio.app.MainActivity}"
DURATION_SECONDS="${DURATION_SECONDS:-1800}"
SAMPLE_INTERVAL_SECONDS="${SAMPLE_INTERVAL_SECONDS:-30}"
INITIAL_MESSAGES="${INITIAL_MESSAGES:-10000}"
MESSAGES_PER_SECOND="${MESSAGES_PER_SECOND:-100}"
OUTPUT="${OUTPUT:-performance-memory-$(date +%Y%m%d-%H%M%S).csv}"

command -v adb >/dev/null || { echo "adb не найден" >&2; exit 1; }
adb get-state >/dev/null

adb shell am force-stop "$PACKAGE"
adb logcat -c
adb shell am start -W \
  -n "$PACKAGE/$ACTIVITY" \
  --ei io.ferventio.app.extra.PERFORMANCE_INITIAL_MESSAGES "$INITIAL_MESSAGES" \
  --ei io.ferventio.app.extra.PERFORMANCE_MESSAGES_PER_SECOND "$MESSAGES_PER_SECOND" \
  --ei io.ferventio.app.extra.PERFORMANCE_DURATION_SECONDS "$DURATION_SECONDS" >/dev/null

echo "timestamp,total_pss_kb,java_heap_kb,native_heap_kb,graphics_kb" > "$OUTPUT"
END=$((SECONDS + DURATION_SECONDS))
while (( SECONDS < END )); do
  MEMINFO="$(adb shell dumpsys meminfo "$PACKAGE")"
  TOTAL="$(awk '/TOTAL PSS:/ {print $3; exit} /^ *TOTAL / {print $2; exit}' <<<"$MEMINFO")"
  JAVA="$(awk '/Java Heap:/ {print $3; exit}' <<<"$MEMINFO")"
  NATIVE="$(awk '/Native Heap:/ {print $3; exit}' <<<"$MEMINFO")"
  GRAPHICS="$(awk '/Graphics:/ {print $2; exit}' <<<"$MEMINFO")"
  printf '%s,%s,%s,%s,%s\n' \
    "$(date --iso-8601=seconds 2>/dev/null || date '+%Y-%m-%dT%H:%M:%S%z')" \
    "${TOTAL:-0}" "${JAVA:-0}" "${NATIVE:-0}" "${GRAPHICS:-0}" | tee -a "$OUTPUT"
  sleep "$SAMPLE_INTERVAL_SECONDS"
done

if adb logcat -d | grep -E 'ANR in io\.ferventio\.app|FerventioANR.*Main thread has not responded' >/dev/null; then
  echo "Обнаружен ANR/main-thread stall. Проверь logcat." >&2
  exit 2
fi

echo "Готово: $OUTPUT"
