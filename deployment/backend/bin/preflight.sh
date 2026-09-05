#!/usr/bin/env bash
set -Eeuo pipefail

readonly BLOCBUY_DEPLOY_ROOT="${BLOCBUY_DEPLOY_ROOT:-/opt/blocbuy/backend}"
readonly BLOCBUY_BACKEND_ENV_FILE="${BACKEND_ENV_FILE:-/etc/blocbuy/backend.env}"
readonly BLOCBUY_COMPOSE_FILE="$BLOCBUY_DEPLOY_ROOT/compose.yml"
readonly BLOCBUY_MIN_FREE_KB="${BLOCBUY_MIN_FREE_KB:-2097152}"
readonly BLOCBUY_PLACEHOLDER_IMAGE="000000000000.dkr.ecr.ap-southeast-1.amazonaws.com/blocbuy-backend@sha256:0000000000000000000000000000000000000000000000000000000000000000"

require_command() {
    local command_name="$1"
    if ! command -v "$command_name" >/dev/null 2>&1; then
        echo "Required command is missing: $command_name" >&2
        return 1
    fi
}

require_env_key() {
    local key="$1"
    if ! grep -Eq "^[[:space:]]*${key}=.+$" "$BLOCBUY_BACKEND_ENV_FILE"; then
        echo "Required non-empty key is missing from $BLOCBUY_BACKEND_ENV_FILE: $key" >&2
        return 1
    fi
}

if [[ "$(uname -s)" != "Linux" ]]; then
    echo "The EC2 deployment requires Linux because the backend uses Docker host networking" >&2
    exit 1
fi

if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
    echo "Run preflight.sh as root; SSM Run Command runs as root by default" >&2
    exit 1
fi

for command_name in aws awk curl df docker flock grep install mv rm rmdir stat timeout; do
    require_command "$command_name"
done

docker info >/dev/null
docker compose version >/dev/null

if [[ ! -f "$BLOCBUY_COMPOSE_FILE" ]]; then
    echo "Compose file not found: $BLOCBUY_COMPOSE_FILE" >&2
    exit 1
fi

if [[ ! -f "$BLOCBUY_BACKEND_ENV_FILE" ]]; then
    echo "Backend environment file not found: $BLOCBUY_BACKEND_ENV_FILE" >&2
    exit 1
fi

env_mode="$(stat -c '%a' "$BLOCBUY_BACKEND_ENV_FILE")"
if (( (8#$env_mode & 077) != 0 )); then
    echo "$BLOCBUY_BACKEND_ENV_FILE must not be readable or writable by group/other (current mode: $env_mode)" >&2
    exit 1
fi

for key in DB_USERNAME DB_PASSWORD REDIS_PASSWORD; do
    require_env_key "$key"
done

if grep -Eq '(^|=)replace-with-' "$BLOCBUY_BACKEND_ENV_FILE"; then
    echo "Replace every example placeholder in $BLOCBUY_BACKEND_ENV_FILE before deployment" >&2
    exit 1
fi

BACKEND_IMAGE="$BLOCBUY_PLACEHOLDER_IMAGE" \
BACKEND_ENV_FILE="$BLOCBUY_BACKEND_ENV_FILE" \
    docker compose --project-name blocbuy-backend-app --file "$BLOCBUY_COMPOSE_FILE" config --quiet

if ! timeout 3 bash -c '</dev/tcp/127.0.0.1/5432' 2>/dev/null; then
    echo "PostgreSQL is not reachable at 127.0.0.1:5432" >&2
    exit 1
fi

if ! timeout 3 bash -c '</dev/tcp/127.0.0.1/6379' 2>/dev/null; then
    echo "Redis is not reachable at 127.0.0.1:6379" >&2
    exit 1
fi

available_kb="$(df -Pk "$BLOCBUY_DEPLOY_ROOT" | awk 'NR == 2 {print $4}')"
if [[ ! "$available_kb" =~ ^[0-9]+$ ]] || ((available_kb < BLOCBUY_MIN_FREE_KB)); then
    echo "At least $BLOCBUY_MIN_FREE_KB KiB of free disk space is required under $BLOCBUY_DEPLOY_ROOT" >&2
    exit 1
fi

aws sts get-caller-identity --output json >/dev/null

echo "EC2 backend deployment preflight passed"
