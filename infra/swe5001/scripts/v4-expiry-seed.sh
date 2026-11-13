#!/usr/bin/env bash
# Explicit, billed demo-fixture write. Never run during V2/V3 measurements.
# Profile/digest/recipient guards precede the existing private db-init task runner.
# shellcheck source=./lib.sh source-path=SCRIPTDIR
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
[ "$#" -eq 0 ] || die 'v4-expiry-seed.sh accepts no arguments or arbitrary SQL'
guard_account
umask 077
expected=${V4_EXPECTED_COUNT-2000}
case "$expected" in 2000|20000) ;; *) die 'V4_EXPECTED_COUNT must be 2000 or the explicitly approved 20000' ;; esac
[ "${CONFIRM_V4_SEED:-}" = "$expected" ] || die "CONFIRM_V4_SEED=$expected is required"
[[ "${V4_RUN_LABEL:-}" =~ ^[a-zA-Z0-9-]{1,24}$ ]] || die 'V4_RUN_LABEL must contain 1..24 letters/digits/hyphens'
[[ "${V4_RECIPIENT_ID:-}" =~ ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$ ]] || die 'V4_RECIPIENT_ID must be a UUID'
[[ "${EXPECTED_IMAGE_DIGEST:-}" =~ ^sha256:[0-9a-f]{64}$ ]] || die 'reviewed commerce EXPECTED_IMAGE_DIGEST is required'
[ -z "$(git -C "$REPO_ROOT" status --porcelain)" ] || die 'seed requires a clean reviewed checkout'

services=$(aws ecs describe-services --cluster "$CLUSTER" --services commerce-svc \
  --query '{services:services[].{status:status,taskDefinition:taskDefinition,runningCount:runningCount,desiredCount:desiredCount,pendingCount:pendingCount,deployments:deployments[].{rolloutState:rolloutState}},failures:failures}' --output json)
revision=$(jq -er 'select(.failures == [] and (.services | length) == 1) | .services[0] |
  select(.status == "ACTIVE" and .runningCount >= 1 and .runningCount == .desiredCount and .pendingCount == 0 and
  (.deployments | length) == 1 and .deployments[0].rolloutState == "COMPLETED") | .taskDefinition' <<< "$services") || die 'commerce must be stable before seeding'
definition=$(aws ecs describe-task-definition --task-definition "$revision" \
  --query "taskDefinition.containerDefinitions[?name=='commerce-svc'].{environment:environment,command:command,entryPoint:entryPoint}" --output json)
jq -e 'length == 1 and (.[0] as $c |
  (($c.command // []) | length) == 0 and (($c.entryPoint // []) | length) == 0 and
  ([$c.environment[] | select(.name == "SPRING_PROFILES_ACTIVE")] | length) == 1 and
  any($c.environment[]; .name == "SPRING_PROFILES_ACTIVE" and
    ([.value | split(",")[] | gsub("^\\s+|\\s+$"; "")] | index("demo") != null)) and
  all(["BLOCBUY_SCHEDULING_ENABLED", "BLOCBUY_EVENTS_ENABLED", "BLOCBUY_EVENTS_RELAY_ENABLED"][];
    . as $key | [$c.environment[] | select(.name == $key and .value == "true")] | length == 1) and
  any($c.environment[]; .name == "BLOCBUY_SCHEDULING_TEAM_EXPIRY_BATCH_SIZE" and .value == "500") and
  any($c.environment[]; .name == "BLOCBUY_SCHEDULING_TEAM_EXPIRY_INTERVAL" and .value == "PT1S"))' \
  <<< "$definition" > /dev/null || die 'reviewed V4 demo profile/expiry settings, enabled scheduling/outbox/relay and default entrypoint/command are required'
tasks=$(aws ecs list-tasks --cluster "$CLUSTER" --service-name commerce-svc --desired-status RUNNING --query taskArns --output json)
task_arns=()
while IFS= read -r arn; do task_arns+=("$arn"); done < <(jq -er '.[]' <<< "$tasks")
[ "${#task_arns[@]}" -gt 0 ] || die 'no running commerce tasks'
observed=$(aws ecs describe-tasks --cluster "$CLUSTER" --tasks "${task_arns[@]}" \
  --query '{tasks:tasks[].{lastStatus:lastStatus,taskDefinitionArn:taskDefinitionArn,containers:containers[].{name:name,imageDigest:imageDigest}},failures:failures}' --output json)
jq -e --arg revision "$revision" --arg digest "$EXPECTED_IMAGE_DIGEST" --argjson services "$services" '
  .failures == [] and (.tasks | length) == $services.services[0].runningCount and
  all(.tasks[]; .lastStatus == "RUNNING" and .taskDefinitionArn == $revision and
    ([.containers[] | select(.name == "commerce-svc")] | length) == 1 and
    any(.containers[]; .name == "commerce-svc" and .imageDigest == $digest))' \
  <<< "$observed" > /dev/null || die 'commerce task revision/digest mismatch'

seed_sql="$REPO_ROOT/backend/blocbuy-bootstrap/src/main/resources/db/seed/load/v4-expiry.sql"
count_args=''
if [ "$expected" = 20000 ]; then
  seed_sql="$REPO_ROOT/perf/k6/v4-expiry.sql"
  count_args="-v expected_count='$expected' -v confirm_count='$CONFIRM_V4_SEED'"
fi
sql=$(base64 < "$seed_sql" | tr -d '\n')
command="set -eu
active=\$(PGDATABASE=identity psql --no-psqlrc -At -v ON_ERROR_STOP=1 <<'SQL'
SELECT count(*) FROM domain.sys_user WHERE id = '$V4_RECIPIENT_ID'::uuid AND status = 'active' AND deleted = false;
SQL
)
[ \"\$active\" = 1 ] || { echo 'V4 recipient is not active' >&2; exit 1; }
printf '%s' '$sql' | base64 -d | PGDATABASE=commerce psql --no-psqlrc -qAt -v ON_ERROR_STOP=1 -v profile=demo -v run_label='$V4_RUN_LABEL' -v recipient='$V4_RECIPIENT_ID' $count_args
echo V4_SEED_COMPLETE"
overrides=$(jq -n --arg cmd "$command" '{containerOverrides:[{name:"db-init",command:["sh","-c",$cmd]}]}')
run_db_init_task "$overrides" 'V4_SEED_COMPLETE|ERROR'
[ "$DB_INIT_EXIT_CODE" = 0 ] || die 'V4 seed task failed'
grep -Fxq V4_SEED_COMPLETE <<< "$DB_INIT_OUTPUT" || die 'V4 seed completion marker is missing; do not reseed'
report=$(printf '%s\n' "$DB_INIT_OUTPUT" | sed -n '/^{/p' | jq -sce 'select(length == 1) | .[0]') || die 'expected exactly one V4 seed receipt'
jq -e --arg run "$V4_RUN_LABEL" --argjson expected "$expected" 'type == "object" and .schema == 1 and .runLabel == $run and
  (if has("expectedCount") then .expectedCount else 2000 end) == $expected and .teams == $expected and .members == $expected and .evidenceKind == "demo-expiry-seed" and
  (.seededAt | type == "string") and (.expiresAt | type == "string")' <<< "$report" > /dev/null || die 'missing or incomplete V4 seed receipt'
jq -c --arg sha "$(git -C "$REPO_ROOT" rev-parse HEAD)" --arg digest "$EXPECTED_IMAGE_DIGEST" --arg revision "$revision" \
  '. + {toolingSha:$sha,commerceDigest:$digest,commerceRevision:$revision,profile:"demo",
    account:"402178232802",region:"ap-southeast-1",cluster:"blocbuy-swe5001"}' <<< "$report"
