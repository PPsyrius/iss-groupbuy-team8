#!/usr/bin/env bash
# Live readiness check. --bootstrap requires services already stopped by env-up;
# standalone mode pauses both producer and consumer and restores counts/scaling.
# No Terraform apply, queue purge, real recipients or full state output.
umask 077
# shellcheck source=./lib.sh source-path=SCRIPTDIR
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
# shellcheck source=./service-control.sh source-path=SCRIPTDIR
source "$SWE5001_DIR/scripts/service-control.sh"
# shellcheck source=./messaging-check.sh source-path=SCRIPTDIR
source "$SWE5001_DIR/scripts/messaging-check.sh"

bootstrap=false
case "${1:-}" in "") ;; --bootstrap) bootstrap=true ;; *) die "usage: verify-messaging.sh [--bootstrap]" ;; esac
guard_account
command -v openssl > /dev/null || die "openssl is not installed"
tf_init runtime
targets=$(tf runtime output -json messaging) || die "cannot read messaging targets"
jq -e 'type == "object" and (.topic_arn | startswith("arn:aws:sns:"))' <<< "$targets" > /dev/null || die "distributed messaging is not provisioned"

work=$(mktemp -d)
snapshot="$work/services.json"
printf '[]\n' > "$snapshot"
PROBE_RECEIPTS="$work/held-messages.json"
printf '[]\n' > "$PROBE_RECEIPTS"
cleanup() {
  local result=$?
  trap - EXIT INT TERM
  release_held_messages || { log "message visibility restoration failed; private receipts retained at $PROBE_RECEIPTS"; result=1; }
  if [ "$bootstrap" = false ]; then
    restore_services "$snapshot" || { log "restoration failed; private snapshot retained at $snapshot"; exit 1; }
  fi
  if [ "$result" = 0 ]; then rm -rf "$work"; else log "private verification recovery records retained in $work"; fi
  exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

if [ "$bootstrap" = false ]; then
  pause_services "$snapshot" commerce-svc notification-svc || die "cannot safely pause both services"
else
  [ "$(jq -r .relay_enabled <<< "$targets")" = false ] || die "bootstrap relay must be disabled"
fi
for service in commerce-svc notification-svc; do
  stopped=$(aws ecs describe-services --cluster "$CLUSTER" --services "$service" \
    --query 'services[0].{desired:desiredCount,running:runningCount}' --output json) || die "cannot inspect $service"
  jq -e '.desired == 0 and .running == 0' <<< "$stopped" > /dev/null || die "$service is not stopped"
done
check_messaging_routes "$targets" || die "messaging readiness failed; relay enablement is not authorised"
log "messaging routing and confirmed alarm-recipient readiness passed"
