#!/usr/bin/env bash
# Grant the admin role to one signed-in account in the identity database of a running
# distributed session (#456, C3). Runs scripts/grant-dev-admin.sql (idempotent) once as the
# db-init task, which already has the RDS master credentials and network; no Terraform
# change and no tunnel into the private subnets. Roles travel in the Sa-Token session
# (backend ADR 0008), so the account must sign in again afterwards.
#
# Usage: grant-admin.sh <google-email>
#   The account must have signed in once through identity-svc. Prints the grant result
#   (granted = 1 the first time, already_admin = 1 on a rerun).
#
# Environment: as env-up.sh. The email is written to the db-init log group (7-day
# retention, operators only).

# shellcheck source=./lib.sh source-path=SCRIPTDIR
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

[ "$#" -eq 1 ] || die "usage: grant-admin.sh <google-email>"
email=$1
# psql quotes the value (:'admin_email'); this only rejects obvious mistakes.
[[ "$email" =~ ^[^@[:space:]\'\"]+@[^@[:space:]\'\"]+$ ]] || die "not an email address: '$email'"

guard_account

# The identity-svc database in runtime/services.tf (distributed topology).
database=identity
sql_file="$REPO_ROOT/scripts/grant-dev-admin.sql"
[ -r "$sql_file" ] || die "cannot read $sql_file"

step "read db-init task definition"
tf_init runtime
task_definition=$(tf runtime output -raw db_init_task_definition) ||
  die "no runtime outputs; run env-up.sh first"
network=$(tf runtime output -json db_init_network |
  jq -c '{awsvpcConfiguration: {subnets: .subnets, securityGroups: .security_groups, assignPublicIp: "DISABLED"}}')

# The SQL travels inline in a quoted here-document, so the shell expands nothing in it.
# The script ends with exit 0 when no account matches, so its message becomes exit 3.
command="out=\$(psql --no-psqlrc -v admin_email=\"\$ADMIN_EMAIL\" -f - <<'GRANT_SQL'
$(cat "$sql_file")
GRANT_SQL
) || exit
printf '%s\\n' \"\$out\"
case \"\$out\" in *'No single active account'*) exit 3 ;; esac"
overrides=$(jq -nc --arg command "$command" --arg db "$database" --arg email "$email" \
  '{containerOverrides: [{name: "db-init", command: ["sh", "-c", $command],
    environment: [{name: "PGDATABASE", value: $db}, {name: "ADMIN_EMAIL", value: $email}]}]}')
# ECS rejects overrides above 8 KiB.
[ "${#overrides}" -lt 8192 ] || die "container overrides are ${#overrides} bytes (limit 8192)"

step "grant admin in database $database"
task_arn=$(aws ecs run-task --cluster "$CLUSTER" --task-definition "$task_definition" \
  --capacity-provider-strategy capacityProvider=FARGATE,weight=1 \
  --network-configuration "$network" --overrides "$overrides" \
  --query 'tasks[0].taskArn' --output text)
log "grant task: $task_arn"
aws ecs wait tasks-stopped --cluster "$CLUSTER" --tasks "$task_arn"
exit_code=$(aws ecs describe-tasks --cluster "$CLUSTER" --tasks "$task_arn" \
  --query 'tasks[0].containers[0].exitCode' --output text)

# awslogs stream: <prefix>/<container>/<task id>. CloudWatch Logs can trail the stopped
# task by a few seconds and deliver the output in parts (BEGIN before the result row), so
# poll until psql's last line (COMMIT, ROLLBACK or an ERROR) has arrived; the output is
# informational, the exit code decides.
messages=""
for attempt in 1 2 3 4 5; do
  messages=$(aws logs get-log-events --log-group-name /blocbuy/swe5001/db-init \
    --log-stream-name "db-init/db-init/${task_arn##*/}" --start-from-head \
    --query 'events[].message' --output text 2> /dev/null || true)
  [ "$messages" != None ] || messages=""
  case "$messages" in
    *COMMIT* | *ROLLBACK* | *ERROR*) break ;;
  esac
  if [ "$attempt" -lt 5 ]; then sleep 3; fi
done
if [ -n "$messages" ]; then
  printf '%s\n' "$messages" | tr '\t' '\n' >&2
else
  log "no grant output in the log yet; see log group /blocbuy/swe5001/db-init"
fi
case "$exit_code" in
  0) ;;
  3) die "no single active account with that email; sign in once through the session first" ;;
  *) die "grant task exited with '$exit_code'; see log group /blocbuy/swe5001/db-init" ;;
esac
step_done

log "Done. Sign in again as $email so the session carries the admin role."
