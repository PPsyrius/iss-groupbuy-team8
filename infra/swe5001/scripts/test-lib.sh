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
