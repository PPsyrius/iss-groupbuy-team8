#!/usr/bin/env bash
# Destroy the SWE5001 runtime environment, then prove nothing is left (AWS plan,
# section 7). edge stays: /api/* returns 502 until the next env-up.sh.
#
# Environment: as env-up.sh. Exit status 0 when env-status.sh finds no runtime resource.

# shellcheck source=./lib.sh source-path=SCRIPTDIR
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

guard_account

# Destroy with the default topology (none): resources in state are destroyed whether or
# not the configuration still lists them, and no digest or edge URL is needed.
destroy_runtime() {
  tf runtime destroy -input=false -auto-approve -var-file="$VAR_FILE"
}

step "destroy runtime"
tf_init runtime
if ! destroy_runtime; then
  # Usually the Service Connect namespace, deleted while services still drain.
  log "destroy failed; retrying once in 60s"
  sleep 60
  destroy_runtime
fi
step_done

"$(dirname "${BASH_SOURCE[0]}")/env-status.sh"
