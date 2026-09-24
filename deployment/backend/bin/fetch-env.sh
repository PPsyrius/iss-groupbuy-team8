#!/usr/bin/env bash
# Render /etc/blocbuy/backend.env from SSM Parameter Store SecureStrings.
# Prod-only: invoked by the prod deploy job's remote script with the '/blocbuy/prod'
# path prefix. Dev keeps its hand-edited env file and never runs this script.
set -Eeuo pipefail

readonly BLOCBUY_BACKEND_ENV_FILE="${BACKEND_ENV_FILE:-/etc/blocbuy/backend.env}"
readonly BLOCBUY_ENV_KEYS=(
    DB_URL
    DB_USERNAME
    DB_PASSWORD
    REDIS_HOST
    REDIS_PORT
    REDIS_PASSWORD
    BLOCBUY_SCHEDULING_ENABLED
    STRIPE_API_KEY
    STRIPE_WEBHOOK_SECRET
)

usage() {
    echo "Usage: fetch-env.sh <ssm-parameter-path-prefix>   e.g. fetch-env.sh /blocbuy/prod" >&2
}

require_command() {
    local command_name="$1"
    if ! command -v "$command_name" >/dev/null 2>&1; then
        echo "Required command is missing: $command_name" >&2
        return 1
    fi
}

if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
    echo "Run fetch-env.sh as root; SSM Run Command runs as root by default" >&2
    exit 1
fi

if [[ $# -ne 1 ]] || [[ -z "$1" ]]; then
    usage
    exit 1
fi

parameter_prefix="$1"
if ! [[ "$parameter_prefix" =~ ^(/[A-Za-z0-9_.-]+)+$ ]] || [[ "$parameter_prefix" == */ ]]; then
    echo "Invalid SSM parameter path prefix: $parameter_prefix" >&2
    usage
    exit 1
fi

if [[ ! -d "$(dirname "$BLOCBUY_BACKEND_ENV_FILE")" ]]; then
    echo "Target directory does not exist: $(dirname "$BLOCBUY_BACKEND_ENV_FILE")" >&2
    exit 1
fi

for command_name in aws mktemp mv python3; do
    require_command "$command_name"
done

# One call fetches every key; values never appear on stdout. They are handed to
# python through the environment (root-only readable), not echoed or piped.
parameter_names=()
for key in "${BLOCBUY_ENV_KEYS[@]}"; do
    parameter_names+=("${parameter_prefix}/${key}")
done

env_file_tmp=''
cleanup() {
    if [[ -n "$env_file_tmp" ]]; then
        rm -f -- "$env_file_tmp"
    fi
}
trap cleanup EXIT

# Created next to the target (same filesystem) so the final mv is atomic.
env_file_tmp="$(mktemp "${BLOCBUY_BACKEND_ENV_FILE}.tmp.XXXXXX")"

params_json="$(aws ssm get-parameters --with-decryption --names "${parameter_names[@]}" --output json)"

BLOCBUY_PARAMS_JSON="$params_json" \
    BLOCBUY_ENV_TARGET="$env_file_tmp" \
    BLOCBUY_PARAM_PREFIX="$parameter_prefix" \
    python3 - <<'PY'
import json
import os
import sys

document = json.loads(os.environ["BLOCBUY_PARAMS_JSON"])
prefix = os.environ["BLOCBUY_PARAM_PREFIX"]
invalid = document.get("InvalidParameters") or []
if invalid:
    sys.exit("Missing SSM parameters: " + ", ".join(invalid))
values = {p["Name"][len(prefix) + 1:]: p["Value"] for p in document.get("Parameters", [])}
required = [
    "DB_URL", "DB_USERNAME", "DB_PASSWORD", "REDIS_HOST", "REDIS_PORT",
    "REDIS_PASSWORD", "BLOCBUY_SCHEDULING_ENABLED", "STRIPE_API_KEY", "STRIPE_WEBHOOK_SECRET",
]
for key in required:
    value = values.get(key, "")
    # Only the key name is reported — never the value.
    if not value or "replace-with-" in value:
        sys.exit("Parameter %s is empty or still contains a placeholder" % key)
    if any(c in value for c in "\n\r"):
        sys.exit("Parameter %s contains a line break" % key)
if values["BLOCBUY_SCHEDULING_ENABLED"].lower() not in ("true", "false"):
    sys.exit("Parameter BLOCBUY_SCHEDULING_ENABLED must be true or false")
with open(os.environ["BLOCBUY_ENV_TARGET"], "w", encoding="utf-8") as handle:
    for key in required:
        handle.write("%s=%s\n" % (key, values[key]))
PY

mv -- "$env_file_tmp" "$BLOCBUY_BACKEND_ENV_FILE"
env_file_tmp=''
chown root:root "$BLOCBUY_BACKEND_ENV_FILE"
chmod 0600 "$BLOCBUY_BACKEND_ENV_FILE"

echo "Rendered $BLOCBUY_BACKEND_ENV_FILE from ${parameter_prefix}/* (${#BLOCBUY_ENV_KEYS[@]} keys)"
