#!/usr/bin/env bash
set -euo pipefail
# No shared DB credentials are consumed. This script creates and stops its own private cluster.
TASK_ROOT=$(cd "$(dirname "$0")/.." && pwd)
python3 "$TASK_ROOT/scripts/check-stripe-dev-contracts.py"
PG_BIN=${STRIPE_PG_BIN:-/opt/homebrew/opt/postgresql@18/bin}
if [[ ! -x "$PG_BIN/initdb" ]]; then
  echo 'PostgreSQL 18 required. Install it or set STRIPE_PG_BIN to its bin directory.' >&2
  exit 2
fi
TASK_TMP=$(mktemp -d "/tmp/blocbuy-stripe-test.XXXXXX")
chmod 700 "$TASK_TMP"
cleanup() {
  "$PG_BIN/pg_ctl" -D "$TASK_TMP/data" -m fast -w stop >/dev/null 2>&1 || true
  echo "Stopped private PostgreSQL. Test cluster and logs retained at: $TASK_TMP"
}
trap cleanup EXIT
export STRIPE_TEST_DB_PASSWORD
STRIPE_TEST_DB_PASSWORD=$(openssl rand -hex 24)
"$PG_BIN/initdb" -D "$TASK_TMP/data" -U stripe_test -A scram-sha-256 \
  --pwfile=<(printf '%s\n' "$STRIPE_TEST_DB_PASSWORD") --encoding=UTF8 --no-locale >/dev/null
"$PG_BIN/pg_ctl" -D "$TASK_TMP/data" -l "$TASK_TMP/postgres.log" \
  -o "-h 127.0.0.1 -p 55439 -k $TASK_TMP" -w start >/dev/null
PGPASSWORD="$STRIPE_TEST_DB_PASSWORD" "$PG_BIN/createdb" -h 127.0.0.1 -p 55439 -U stripe_test blocbuy_stripe_test
PGPASSWORD="$STRIPE_TEST_DB_PASSWORD" "$PG_BIN/createdb" -h 127.0.0.1 -p 55439 -U stripe_test blocbuy_stripe_local
export STRIPE_TEST_DB_URL='jdbc:postgresql://127.0.0.1:55439/blocbuy_stripe_test?currentSchema=domain'
cd "$TASK_ROOT/backend"
./mvnw -B -ntp verify -DskipITs
echo 'Payment tests finished with test-only business fixtures. Stripe network is mocked; not deployed acceptance.'
