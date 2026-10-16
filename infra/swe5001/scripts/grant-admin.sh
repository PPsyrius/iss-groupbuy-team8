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

step "grant admin in database $database"
# psql's last line is COMMIT, ROLLBACK or an ERROR (BEGIN arrives before the result row).
run_db_init_task "$overrides" 'COMMIT|ROLLBACK|ERROR'
if [ -n "$DB_INIT_OUTPUT" ]; then
  printf '%s\n' "$DB_INIT_OUTPUT" >&2
else
  log "no grant output in the log yet; see log group /blocbuy/swe5001/db-init"
fi
case "$DB_INIT_EXIT_CODE" in
  0) ;;
  3) die "no single active account with that email; sign in once through the session first" ;;
  *) die "grant task exited with '$DB_INIT_EXIT_CODE'; see log group /blocbuy/swe5001/db-init" ;;
esac
step_done

log "Done. Sign in again as $email so the session carries the admin role."
