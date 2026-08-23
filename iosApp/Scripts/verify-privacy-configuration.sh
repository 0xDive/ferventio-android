#!/bin/sh
set -eu

if [ "${CONFIGURATION:-}" != "Release" ]; then
    exit 0
fi

failed=0

require_value() {
    name="$1"
    value="$2"
    if [ -z "$value" ]; then
        echo "error: $name must be configured for iOS Release builds." >&2
        failed=1
    fi
}

require_value "FERVENTIO_PRIVACY_OPERATOR_NAME" "${FERVENTIO_PRIVACY_OPERATOR_NAME:-}"
require_value "FERVENTIO_PRIVACY_CONTACT" "${FERVENTIO_PRIVACY_CONTACT:-}"
require_value "FERVENTIO_PRIVACY_POLICY_URL" "${FERVENTIO_PRIVACY_POLICY_URL:-}"

case "${FERVENTIO_PRIVACY_POLICY_URL:-}" in
    https://*) ;;
    "") ;;
    *)
        echo "error: FERVENTIO_PRIVACY_POLICY_URL must use https:// for iOS Release builds." >&2
        failed=1
        ;;
esac

exit "$failed"
