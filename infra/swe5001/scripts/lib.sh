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

# Service names per runtime topology; keep in sync with runtime/services.tf. Each name is
# also the ECR repository suffix and the SSM digest name (/release/<name>, /candidate/<name>).
services_for_topology() {
  case "$1" in
    none) echo "" ;;
    monolith) echo "monolith" ;;
    distributed) echo "identity-svc commerce-svc notification-svc" ;;
    *) die "unknown topology '$1' (none, monolith or distributed)" ;;
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

# Run the db-init task definition once with container overrides (JSON) and wait for it:
# it already has the RDS master credentials, the private subnets and its log group, so
# one-off SQL needs no Terraform change and no tunnel (grant-admin.sh, session-report.sh).
# $2 is an extended regular expression matching the task's last log line. Sets DB_INIT_EXIT_CODE and
# DB_INIT_OUTPUT (log lines, empty when CloudWatch has not delivered them yet).
# shellcheck disable=SC2034
run_db_init_task() {
  local overrides=$1 last_line=$2 task_definition network task_arn attempt
  # ECS rejects overrides above 8 KiB.
  [ "${#overrides}" -lt 8192 ] || die "container overrides are ${#overrides} bytes (limit 8192)"
  tf_init runtime
  task_definition=$(tf runtime output -raw db_init_task_definition) ||
    die "no runtime outputs; run env-up.sh first"
  network=$(tf runtime output -json db_init_network |
    jq -c '{awsvpcConfiguration: {subnets: .subnets, securityGroups: .security_groups, assignPublicIp: "DISABLED"}}') ||
    die "no db-init network output; run env-up.sh first"

  task_arn=$(aws ecs run-task --cluster "$CLUSTER" --task-definition "$task_definition" \
    --capacity-provider-strategy capacityProvider=FARGATE,weight=1 \
    --network-configuration "$network" --overrides "$overrides" \
    --query 'tasks[0].taskArn' --output text) || die "cannot start the db-init task"
  case "$task_arn" in arn:aws:ecs:*) ;; *) die "db-init task did not start: '$task_arn'" ;; esac
  log "db-init task: $task_arn"
  aws ecs wait tasks-stopped --cluster "$CLUSTER" --tasks "$task_arn"
  DB_INIT_EXIT_CODE=$(aws ecs describe-tasks --cluster "$CLUSTER" --tasks "$task_arn" \
    --query 'tasks[0].containers[0].exitCode' --output text)

  # awslogs stream: <prefix>/<container>/<task id>. CloudWatch Logs can trail the stopped
  # task by a few seconds and deliver the output in parts, so poll until the last line has
  # arrived; callers decide whether complete output is required in addition to exit 0.
  DB_INIT_OUTPUT=""
  for attempt in 1 2 3 4 5; do
    DB_INIT_OUTPUT=$(aws logs get-log-events --log-group-name /blocbuy/swe5001/db-init \
      --log-stream-name "db-init/db-init/${task_arn##*/}" --start-from-head \
      --query 'events[].message' --output text 2> /dev/null || true)
    [ "$DB_INIT_OUTPUT" != None ] || DB_INIT_OUTPUT=""
    if [[ "$DB_INIT_OUTPUT" =~ $last_line ]]; then break; fi
    if [ "$attempt" -lt 5 ]; then sleep 3; fi
  done
  DB_INIT_OUTPUT=$(printf '%s' "$DB_INIT_OUTPUT" | tr '\t' '\n')
}

start_stopped_services() {
  # Terraform ignores desired_count after creation so it cannot reset autoscaling.
  # Enforce the configured floor after db-init; preserve higher scaled counts.
  local service desired minimum
  for service in "$@"; do
    desired=$(aws ecs describe-services --cluster "$CLUSTER" --services "$service" \
      --query 'services[0].desiredCount' --output text) || die "cannot read desired count for $service"
    [[ "$desired" =~ ^[0-9]+$ ]] || die "invalid desired count for $service: $desired"
    minimum=1
    if [ "$service" = commerce-svc ]; then
      minimum=${TF_VAR_commerce_min_capacity:-2}
      case "$minimum" in 1 | 2) ;; *) die "TF_VAR_commerce_min_capacity must be 1 or 2" ;; esac
    fi
    if [ "$desired" -lt "$minimum" ]; then
      aws ecs update-service --cluster "$CLUSTER" --service "$service" --desired-count "$minimum" > /dev/null
    fi
  done
}
