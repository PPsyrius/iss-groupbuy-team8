#!/usr/bin/env bash
# Capture contract only: all cloud/git calls are stubbed; no AWS credentials or writes.
set -euo pipefail
dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
export AWS_REGION=ap-southeast-1 SWE5001_TF_STATE_BUCKET=test CALLS="$work/calls"
export FAULT=normal
git() {
  case "$*" in
    *'status --porcelain') [ "$FAULT" != dirty ] || printf ' M file\n' ;;
    *'rev-parse HEAD') printf '%040d\n' 0 ;;
    *) return 1 ;;
  esac
}
terraform() { return 1; }
aws() {
  printf '%s\n' "$*" >> "$CALLS"
  [ "$FAULT" != denied ] || return 1
  case "$1 $2" in
    'sts get-caller-identity') printf '402178232802\n' ;;
    'cloudfront get-distribution') printf 'd3lvq02o2e7tcn.cloudfront.net\n' ;;
    'ecs describe-services') printf '{"services":[],"failures":[]}\n' ;;
    'ecs list-tasks') if [ "$FAULT" = extra_task ]; then printf '["task:1","task:2"]\n'; else printf '["task:1"]\n'; fi ;;
    'ecs describe-tasks') printf '{"tasks":[],"failures":[]}\n' ;;
    'application-autoscaling describe-scalable-targets') printf '{"ScalableTargets":[]}\n' ;;
    'application-autoscaling describe-scaling-policies') printf '{"ScalingPolicies":[]}\n' ;;
    *) return 1 ;;
  esac
}
export -f git terraform aws
bash "$dir/http-preflight.sh" v2 > "$work/report.json"
jq -e '.schema == 1 and .mode == "v2" and .account == "402178232802" and .baseUrl == "https://d3lvq02o2e7tcn.cloudfront.net" and (.toolingSha | length) == 40' "$work/report.json" > /dev/null
printf 'PASS preflight records fixed account/origin and tooling source\n'
grep -q -- '--services monolith' "$CALLS"
printf 'PASS V2 captures the monolith, not distributed commerce\n'
bash "$dir/http-preflight.sh" v3 > "$work/report.json"
grep -q -- '--services commerce-svc' "$CALLS"
printf 'PASS V3 captures distributed commerce\n'
if grep -Eq 'update-|register-|run-task|publish|send-message|put-' "$CALLS"; then exit 1; fi
printf 'PASS capture issues no mutation or task-start requests\n'
for fault in dirty denied extra_task; do
  if FAULT=$fault bash "$dir/http-preflight.sh" v2 > "$work/fault.json" 2> "$work/error"; then
    printf 'FAIL preflight accepted %s\n' "$fault" >&2
    exit 1
  fi
  [ ! -s "$work/fault.json" ]
  printf 'PASS capture fails closed on %s\n' "$fault"
done
if bash "$dir/http-preflight.sh" invalid > /dev/null 2>&1; then exit 1; fi
printf 'PASS invalid preflight mode is rejected\n'
