# shellcheck shell=bash
# Shared helpers for env-up.sh, env-down.sh and env-status.sh (AWS plan, section 7).
# Sourced, never run. Bash 3.2 compatible (macOS default shell).

set -euo pipefail

# Variables marked SC2034 are read by the scripts that source this file.
SWE5001_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
# shellcheck disable=SC2034
REPO_ROOT=$(cd "$SWE5001_DIR/../.." && pwd)
# shellcheck disable=SC2034
VAR_FILE="$SWE5001_DIR/envs/demo.tfvars"
EXPECTED_ACCOUNT=402178232802
EXPECTED_REGION=ap-southeast-1
# shellcheck disable=SC2034
SSM_PREFIX=/blocbuy/swe5001
# shellcheck disable=SC2034
CLUSTER=blocbuy-swe5001

log() { printf '[%s] %s\n' "$(date -u +%H:%M:%S)" "$*" >&2; }
die() {
  log "ERROR: $*"
  exit 1
}

# Refuse to touch anything outside the SWE5001 account and region.
guard_account() {
  local tool region account
  for tool in terraform aws jq curl; do
    command -v "$tool" > /dev/null || die "$tool is not installed"
  done
  region=${AWS_REGION:-${AWS_DEFAULT_REGION:-}}
  [ "$region" = "$EXPECTED_REGION" ] || die "AWS_REGION must be $EXPECTED_REGION (got '${region}')"
  export AWS_REGION=$region AWS_DEFAULT_REGION=$region
  account=$(aws sts get-caller-identity --query Account --output text) || die "no AWS credentials"
  [ "$account" = "$EXPECTED_ACCOUNT" ] || die "credentials are for account $account, not $EXPECTED_ACCOUNT"
  [ -n "${SWE5001_TF_STATE_BUCKET:-}" ] || die "set SWE5001_TF_STATE_BUCKET (README, step 5)"
}

tf() {
  local stack=$1
  shift
  terraform -chdir="$SWE5001_DIR/$stack" "$@"
}

tf_init() {
  tf "$1" init -input=false -reconfigure \
    -backend-config="bucket=$SWE5001_TF_STATE_BUCKET" \
    -backend-config="region=$EXPECTED_REGION" > /dev/null
}

# Service names per runtime topology; keep in sync with runtime/services.tf.
services_for_topology() {
  case "$1" in
    none) echo "" ;;
    monolith) echo "monolith" ;;
    *) die "unknown topology '$1' (none or monolith)" ;;
  esac
}

# Step timings for the promotion evidence (verification plan, V8). Appends
# "<step> <seconds>" to $SWE5001_TIMINGS_FILE when it is set.
STEP_NAME=""
STEP_STARTED=0
step() {
  step_done
  STEP_NAME=$1
  STEP_STARTED=$(date +%s)
  log "== $STEP_NAME"
}
step_done() {
  if [ -n "$STEP_NAME" ]; then
    local seconds=$(($(date +%s) - STEP_STARTED))
    log "== $STEP_NAME done in ${seconds}s"
    if [ -n "${SWE5001_TIMINGS_FILE:-}" ]; then
      printf '%s %s\n' "$STEP_NAME" "$seconds" >> "$SWE5001_TIMINGS_FILE"
    fi
  fi
  STEP_NAME=""
}
