#!/usr/bin/env bash
set -euo pipefail
# An isolated local database only; never consume PGHOST or shared credentials.
TASK_ROOT=$(cd "$(dirname "$0")/../.." && pwd)
PG_BIN=${STRIPE_PG_BIN:-/opt/homebrew/opt/postgresql@18/bin}
if [[ ! -x "$PG_BIN/initdb" ]] || [[ $("$PG_BIN/initdb" --version) != *' 18.'* ]]; then
  echo 'PostgreSQL 18 required; set STRIPE_PG_BIN to its bin directory.' >&2
  exit 2
fi
TASK_TMP=$(mktemp -d "/tmp/blocbuy-rollout-test.XXXXXX")
chmod 700 "$TASK_TMP"
cleanup() {
  local result=$?
  "$PG_BIN/pg_ctl" -D "$TASK_TMP/data" -m fast -w stop >/dev/null 2>&1 || true
  if [[ "$result" -eq 0 && "$TASK_TMP" == /tmp/blocbuy-rollout-test.* && -d "$TASK_TMP" ]]; then
    rm -r -- "$TASK_TMP"
    echo 'Private PostgreSQL stopped and temporary test data removed.'
  else
    echo "Private PostgreSQL stopped. Failed-run diagnostics retained at: $TASK_TMP"
  fi
}
trap cleanup EXIT
export STRIPE_TEST_DB_PASSWORD
STRIPE_TEST_DB_PASSWORD=$(openssl rand -hex 24)
"$PG_BIN/initdb" -D "$TASK_TMP/data" -U stripe_test -A scram-sha-256 \
  --pwfile=<(printf '%s\n' "$STRIPE_TEST_DB_PASSWORD") --encoding=UTF8 --no-locale >/dev/null
# This exact loopback address/database pair is guarded by StripeTestDatabase.
"$PG_BIN/pg_ctl" -D "$TASK_TMP/data" -l "$TASK_TMP/postgres.log" \
  -o "-h 127.0.0.1 -p 55439 -k $TASK_TMP" -w start >/dev/null
for test_database in blocbuy_stripe_test blocbuy_stripe_local; do
  PGPASSWORD="$STRIPE_TEST_DB_PASSWORD" "$PG_BIN/createdb" \
    -h 127.0.0.1 -p 55439 -U stripe_test "$test_database"
done
export STRIPE_TEST_DB_URL='jdbc:postgresql://127.0.0.1:55439/blocbuy_stripe_test?currentSchema=domain'
cd "$TASK_ROOT/backend"
./mvnw -B -ntp verify \
  -Dit.test=StripeRolloutAcceptanceIT,TeamExpirySchedulingIT \
  -Dfailsafe.failIfNoSpecifiedTests=false
PGPASSWORD="$STRIPE_TEST_DB_PASSWORD" "$PG_BIN/psql" -X -v ON_ERROR_STOP=1 \
  -h 127.0.0.1 -p 55439 -U stripe_test -d blocbuy_stripe_local \
  -f "$TASK_ROOT/scripts/payment/rollout-backlog.sql"
echo 'Local production-path checks passed. Stripe and OAuth/session providers are test substitutes; dev/Sandbox acceptance is still required.'
