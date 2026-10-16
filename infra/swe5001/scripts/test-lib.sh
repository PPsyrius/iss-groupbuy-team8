#!/usr/bin/env bash
# Exercise lib.sh helpers without AWS: the service names per topology must match the
# runtime map keys (runtime/services.tf), which are also the ECR and SSM digest names.
set -euo pipefail

# shellcheck source=./lib.sh source-path=SCRIPTDIR
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

check() {
  local topology=$1 expected=$2 actual
  actual=$(services_for_topology "$topology")
  if [ "$actual" != "$expected" ]; then
    printf "FAIL %s: expected '%s', got '%s'\n" "$topology" "$expected" "$actual" >&2
    exit 1
  fi
  printf 'PASS %s\n' "$topology"
}

check none ''
check monolith monolith
check distributed 'identity-svc commerce-svc notification-svc'

if output=$(services_for_topology monolit 2>&1); then
  printf 'FAIL unknown topology accepted: %s\n' "$output" >&2
  exit 1
fi
[[ "$output" == *"unknown topology 'monolit'"* ]] || {
  printf 'FAIL unknown topology message: %s\n' "$output" >&2
  exit 1
}
printf 'PASS unknown topology rejected\n'

updates=$(mktemp)
trap 'rm -f "$updates"' EXIT
aws() {
  case "$2" in
    describe-services) printf '%s\n' "$mock_desired" ;;
    update-service) printf '%s\n' "$*" >> "$updates" ;;
    *) return 1 ;;
  esac
}
mock_desired=0
start_stopped_services notification-svc
[ "$(wc -l < "$updates")" -eq 1 ] || exit 1
mock_desired=4
start_stopped_services notification-svc
[ "$(wc -l < "$updates")" -eq 1 ] || exit 1
printf 'PASS start zero-count service without resetting scaled count\n'
mock_desired=None
if (start_stopped_services notification-svc) 2>/dev/null; then
  printf 'FAIL missing service accepted\n' >&2
  exit 1
fi
printf 'PASS missing service fails closed\n'

unset TF_VAR_commerce_min_capacity
mock_desired=1
start_stopped_services commerce-svc
grep -q -- '--service commerce-svc --desired-count 2' "$updates"
printf 'PASS commerce demo enforces two-task floor\n'
: > "$updates"
mock_desired=4
start_stopped_services commerce-svc
[ ! -s "$updates" ]
printf 'PASS commerce preserves higher scaled counts\n'
TF_VAR_commerce_min_capacity=1
mock_desired=0
start_stopped_services commerce-svc
grep -q -- '--service commerce-svc --desired-count 1' "$updates"
printf 'PASS commerce baseline uses one-task floor\n'
TF_VAR_commerce_min_capacity=3
if (start_stopped_services commerce-svc) 2>/dev/null; then
  printf 'FAIL invalid commerce minimum accepted\n' >&2
  exit 1
fi
printf 'PASS invalid commerce minimum fails closed\n'

for workflow in swe5001-env.yml swe5001-deploy.yml; do
  grep -Fq "TF_VAR_commerce_min_capacity: \${{ vars.SWE5001_COMMERCE_MIN_CAPACITY || '2' }}" "$REPO_ROOT/.github/workflows/$workflow"
  grep -Fq "TF_VAR_commerce_requests_per_target: \${{ vars.SWE5001_COMMERCE_REQUESTS_PER_TARGET || '200' }}" "$REPO_ROOT/.github/workflows/$workflow"
  printf 'PASS %s preserves the configured commerce floor and calibrated request target\n' "$workflow"
done
