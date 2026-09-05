#!/usr/bin/env bash
set -Eeuo pipefail

readonly BLOCBUY_HEALTH_URL="${BLOCBUY_HEALTH_URL:-http://127.0.0.1:8589/api/actuator/health}"
readonly BLOCBUY_HEALTH_ATTEMPTS="${BLOCBUY_HEALTH_ATTEMPTS:-30}"
readonly BLOCBUY_HEALTH_INTERVAL_SECONDS="${BLOCBUY_HEALTH_INTERVAL_SECONDS:-3}"

if [[ ! "$BLOCBUY_HEALTH_ATTEMPTS" =~ ^[1-9][0-9]*$ ]]; then
    echo "BLOCBUY_HEALTH_ATTEMPTS must be a positive integer" >&2
    exit 2
fi

if [[ ! "$BLOCBUY_HEALTH_INTERVAL_SECONDS" =~ ^[1-9][0-9]*$ ]]; then
    echo "BLOCBUY_HEALTH_INTERVAL_SECONDS must be a positive integer" >&2
    exit 2
fi

for ((attempt = 1; attempt <= BLOCBUY_HEALTH_ATTEMPTS; attempt++)); do
    response="$(curl --silent --show-error --fail --max-time 5 "$BLOCBUY_HEALTH_URL" 2>/dev/null || true)"
    if [[ "$response" =~ \"status\"[[:space:]]*:[[:space:]]*\"UP\" ]]; then
        echo "Backend health check passed on attempt $attempt: $BLOCBUY_HEALTH_URL"
        exit 0
    fi

    if ((attempt < BLOCBUY_HEALTH_ATTEMPTS)); then
        sleep "$BLOCBUY_HEALTH_INTERVAL_SECONDS"
    fi
done

echo "Backend did not become healthy after $BLOCBUY_HEALTH_ATTEMPTS attempts: $BLOCBUY_HEALTH_URL" >&2
exit 1
