#!/usr/bin/env bash
# Create (or converge) the SWE5001 runtime environment and validate it through CloudFront
# (AWS plan, section 7, steps 1-5). Safe to re-run on an existing environment.
#
# Usage: env-up.sh [--release|--candidate]
#   --release    image digests from SSM /blocbuy/swe5001/release/<service> (default)
#   --candidate  digests from /blocbuy/swe5001/candidate/<service> (first promotion)
#
# Environment: AWS credentials for account 402178232802, AWS_REGION=ap-southeast-1,
# SWE5001_TF_STATE_BUCKET; optional SWE5001_TOPOLOGY (monolith, default; distributed for
# identity-svc, commerce-svc and notification-svc; or none) and SWE5001_TIMINGS_FILE. The
# workflows pass the topology explicitly. The edge stack must already exist (created once
# by an operator).
# Exit status 0 when the smoke suite passes.

# shellcheck source=./lib.sh source-path=SCRIPTDIR
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
# shellcheck source=./service-control.sh source-path=SCRIPTDIR
source "$SWE5001_DIR/scripts/service-control.sh"
umask 077

digest_source=release
case "${1:-}" in
  "" | --release) digest_source=release ;;
  --candidate) digest_source=candidate ;;
  *) die "usage: env-up.sh [--release|--candidate]" ;;
esac

guard_account
topology=${SWE5001_TOPOLOGY:-monolith}
services=$(services_for_topology "$topology")
if [ "$topology" = distributed ]; then
  command -v openssl > /dev/null || die "openssl is not installed"
  jq -e 'type == "array" and length > 0 and all(.[]; type == "string" and length > 0)' \
    <<< "${TF_VAR_alert_email_addresses:-[]}" > /dev/null || die "distributed up requires team alarm recipients in TF_VAR_alert_email_addresses"
fi
# Terraform parses a variable file as JSON only when its name ends in .json, and BSD
# mktemp cannot add a suffix, so the files live in a private temporary directory.
work=$(mktemp -d)
snapshot="$work/services.json"
printf '[]\n' > "$snapshot"
infra_started=false
completed=false
cleanup() {
  local result=$?
  trap - EXIT INT TERM
  if [ "$completed" = false ]; then
    if [ "$infra_started" = false ] && [ "$(jq length "$snapshot")" -gt 0 ]; then
      restore_services "$snapshot" || { log "restoration failed; private snapshot retained at $snapshot"; exit 1; }
    elif [ "$infra_started" = true ]; then
      # A later failure (edge/smoke/restore) may occur after the second apply started
      # tasks. Explicitly pause again; never merely assume counts are still zero.
      # shellcheck disable=SC2086 # known topology names
      if pause_services "$work/failed-services.json" $services; then
        log "up failed after infrastructure changes; services are paused until a successful retry"
      else
        log "up failed and service stop could not be confirmed; inspect the cluster before retrying"
      fi
      log "rerun env-up.sh or run env-down.sh; private recovery snapshot retained at $snapshot"
      exit "$result"
    fi
  fi
  rm -rf "$work"
  exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
run_vars="$work/run.tfvars.json"

step "read edge URL"
tf_init edge
frontend_url=$(tf edge output -raw url 2> /dev/null) ||
  die "edge has no outputs; an operator must create it first (infra/swe5001/README.md)"
log "edge: $frontend_url"

step "read $digest_source image digests"
digests='{}'
for service in $services; do
  digest=$(aws ssm get-parameter --name "$SSM_PREFIX/$digest_source/$service" \
    --query Parameter.Value --output text 2> /dev/null) ||
    die "no $digest_source digest for $service; on the first run use --candidate"
  log "$service: $digest"
  digests=$(jq --arg s "$service" --arg d "$digest" '. + {($s): $d}' <<< "$digests")
done
jq -n --arg t "$topology" --arg u "$frontend_url" --argjson d "$digests" \
  '{topology: $t, frontend_url: $u, image_digests: $d}' > "$run_vars"

apply_runtime() {
  tf runtime apply -input=false -auto-approve -var-file="$VAR_FILE" \
    -var-file="$run_vars" -var "services_enabled=$1" -var "messaging_verified=$2"
}

step "pause existing services and scaling before changing runtime"
tf_init runtime
# shellcheck disable=SC2086 # one word per known topology service
pause_services "$snapshot" $services || die "cannot safely pause existing services"
infra_started=true
step "apply runtime (services stopped, relay off, scaling suspended)"
apply_runtime false false

step "run db-init"
task_definition=$(tf runtime output -raw db_init_task_definition)
network=$(tf runtime output -json db_init_network |
  jq -c '{awsvpcConfiguration: {subnets: .subnets, securityGroups: .security_groups, assignPublicIp: "DISABLED"}}')
task_arn=$(aws ecs run-task --cluster "$CLUSTER" --task-definition "$task_definition" \
  --capacity-provider-strategy capacityProvider=FARGATE,weight=1 \
  --network-configuration "$network" --query 'tasks[0].taskArn' --output text)
log "db-init task: $task_arn"
aws ecs wait tasks-stopped --cluster "$CLUSTER" --tasks "$task_arn"
exit_code=$(aws ecs describe-tasks --cluster "$CLUSTER" --tasks "$task_arn" \
  --query 'tasks[0].containers[0].exitCode' --output text)
[ "$exit_code" = 0 ] ||
  die "db-init exited with '$exit_code'; see log group /blocbuy/swe5001/db-init"

verified=false
if [ "$topology" = distributed ]; then
  step "verify messaging before enabling the commerce relay"
  bash "$SWE5001_DIR/scripts/verify-messaging.sh" --bootstrap || die "messaging gate failed; services remain stopped"
  verified=true
fi
step "apply runtime (services started only after db-init and readiness)"
apply_runtime true "$verified"
# Restore prior scaled counts, rather than replacing them with a hard-coded 1.
restore_services "$snapshot" || die "cannot restore previous service counts/scaling"
# shellcheck disable=SC2086 # one word per service name
start_stopped_services $services

step "apply edge (point /api/* at the ALB)"
runtime_alb_dns_name=$(tf runtime output -raw alb_dns_name)
# The origin diff carries ORIGIN_SECRET in clear (edge/cloudfront.tf marks it
# nonsensitive), so the plan is never printed; workflow logs are readable by every
# collaborator. On failure only Terraform's diagnostics are shown.
edge_log="$work/edge.log"
if tf edge apply -input=false -auto-approve -no-color -var-file="$VAR_FILE" \
  -var "runtime_alb_dns_name=$runtime_alb_dns_name" > "$edge_log" 2>&1; then
  grep '^Apply complete' "$edge_log" >&2 || true
else
  sed -n '/^Error:/,$p' "$edge_log" >&2
  die "edge apply failed"
fi

if [ -z "$services" ]; then
  completed=true
  step_done
  log "topology none: no service to wait for or smoke-test"
  exit 0
fi

step "wait for services to be stable"
# shellcheck disable=SC2086 # one word per service name
aws ecs wait services-stable --cluster "$CLUSTER" --services $services

step "smoke suite"
attempt=1
until "$REPO_ROOT/perf/smoke/smoke.sh" "$frontend_url"; do
  [ "$attempt" -lt 5 ] || die "smoke suite failed after $attempt attempts"
  attempt=$((attempt + 1))
  log "smoke attempt $attempt in 30s"
  sleep 30
done
step_done

completed=true
log "Environment is up: $frontend_url"
