#!/usr/bin/env bash
# Create (or converge) the SWE5001 runtime environment and validate it through CloudFront
# (AWS plan, section 7, steps 1-5). Safe to re-run on an existing environment.
#
# Usage: env-up.sh [--release|--candidate]
#   --release    image digests from SSM /blocbuy/swe5001/release/<service> (default)
#   --candidate  digests from /blocbuy/swe5001/candidate/<service> (first promotion)
#
# Environment: AWS credentials for account 402178232802, AWS_REGION=ap-southeast-1,
# SWE5001_TF_STATE_BUCKET; optional SWE5001_TOPOLOGY (monolith, default; or none) and
# SWE5001_TIMINGS_FILE. The edge stack must already exist (created once by an operator).
# Exit status 0 when the smoke suite passes.

# shellcheck source=./lib.sh source-path=SCRIPTDIR
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

digest_source=release
case "${1:-}" in
  "" | --release) digest_source=release ;;
  --candidate) digest_source=candidate ;;
  *) die "usage: env-up.sh [--release|--candidate]" ;;
esac

guard_account
topology=${SWE5001_TOPOLOGY:-monolith}
services=$(services_for_topology "$topology")
run_vars=$(mktemp)
trap 'rm -f "$run_vars"' EXIT

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
    -var-file="$run_vars" -var "services_enabled=$1"
}

step "apply runtime (services stopped)"
tf_init runtime
apply_runtime false

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

step "apply runtime (services started)"
apply_runtime true

step "apply edge (point /api/* at the ALB)"
tf edge apply -input=false -auto-approve -var-file="$VAR_FILE"

if [ -z "$services" ]; then
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

log "Environment is up: $frontend_url"
