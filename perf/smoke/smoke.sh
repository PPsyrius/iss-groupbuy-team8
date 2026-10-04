#!/usr/bin/env bash
# Smoke suite V1 (docs/swe5001/impl-verification-observability.md, section 3).
# Usage: perf/smoke/smoke.sh <base-url>   e.g. https://d111111abcdef8.cloudfront.net
# Runs against any shape (EC2 CloudFront, SWE5001 CloudFront, local Compose). Read-only:
# only GET requests, no credentials. Exits 0 when every check passes.
set -euo pipefail

base_url=${1:?usage: smoke.sh <base-url>}
base_url=${base_url%/}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

failures=0
pass() { printf 'PASS  %s\n' "$1"; }
fail() { printf 'FAIL  %s: %s\n' "$1" "$2"; failures=$((failures + 1)); }

# fetch <name> <path>: body in $work/<name>.body, headers in $work/<name>.headers,
# status code on stdout.
fetch() {
  curl --silent --show-error --max-time 20 \
    --output "$work/$1.body" --dump-header "$work/$1.headers" \
    --write-out '%{http_code}' "$base_url$2" || echo 000
}

header() { grep -i "^$2:" "$work/$1.headers" | tail -n 1 | cut -d: -f2- | tr -d ' \r' || true; }

check_trace_id() {
  if [ -n "$(header "$1" X-Trace-Id)" ]; then pass "$1 has X-Trace-Id"; else fail "$1" "no X-Trace-Id header"; fi
}

# 1. Health through CloudFront and the ALB.
status=$(fetch health /api/actuator/health)
if [ "$status" = 200 ] && jq -e '.status == "UP"' "$work/health.body" > /dev/null 2>&1; then
  pass "health is UP"
else
  fail health "status $status, body $(head -c 200 "$work/health.body" 2> /dev/null)"
fi
check_trace_id health

# 2. Catalogue browse returns the Result envelope with seeded products.
status=$(fetch products '/api/v1/products?page=1&size=20')
if [ "$status" = 200 ] && jq -e '.code == "COMMON_200_000" and (.data.items | length) > 0' "$work/products.body" > /dev/null 2>&1; then
  pass "products list is non-empty"
else
  fail products "status $status, body $(head -c 200 "$work/products.body" 2> /dev/null)"
fi
check_trace_id products

# 3. A protected endpoint without a session answers 401 with the envelope.
status=$(fetch auth_me /api/v1/auth/me)
if [ "$status" = 401 ] && jq -e '.code | type == "string"' "$work/auth_me.body" > /dev/null 2>&1; then
  pass "auth/me without a session is 401"
else
  fail auth_me "status $status, body $(head -c 200 "$work/auth_me.body" 2> /dev/null)"
fi
check_trace_id auth_me

# 4. Service-to-service paths never reach a service: CloudFront sends only /api/* to the
#    ALB, so the answer is the SPA (or an S3 error before the SPA is uploaded), not JSON.
status=$(fetch internal /internal/users/00000000-0000-0000-0000-000000000000/active)
content_type=$(header internal Content-Type)
case "$content_type" in
  application/json*) fail internal "status $status answered with JSON; an internal endpoint is exposed" ;;
  *) pass "internal path is not served by a service (status $status, ${content_type:-no content type})" ;;
esac

if [ "$failures" -gt 0 ]; then
  printf '%s check(s) failed against %s\n' "$failures" "$base_url"
  exit 1
fi
printf 'All checks passed against %s\n' "$base_url"
