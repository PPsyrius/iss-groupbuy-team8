#!/usr/bin/env bash
set -Eeuo pipefail
umask 027

readonly BLOCBUY_DEPLOY_ROOT="${BLOCBUY_DEPLOY_ROOT:-/opt/blocbuy/backend}"
readonly BLOCBUY_BACKEND_ENV_FILE="${BACKEND_ENV_FILE:-/etc/blocbuy/backend.env}"
readonly BLOCBUY_COMPOSE_FILE="$BLOCBUY_DEPLOY_ROOT/compose.yml"
readonly BLOCBUY_STATE_DIR="$BLOCBUY_DEPLOY_ROOT/state"
readonly BLOCBUY_CONTAINER_NAME="blocbuy-backend"
readonly BLOCBUY_PROJECT_NAME="blocbuy-backend-app"
readonly BLOCBUY_IMAGE_PATTERN='^([0-9]{12})\.dkr\.ecr\.([a-z0-9-]+)\.amazonaws\.com/([a-z0-9][a-z0-9._/-]*)@sha256:([a-f0-9]{64})$'

usage() {
    echo "Usage: $0 <immutable-ecr-image-uri> [git-sha]" >&2
}

atomic_write() {
    local destination="$1"
    local value="$2"
    local temporary_file="${destination}.tmp.$$"
    printf '%s\n' "$value" >"$temporary_file"
    mv -f "$temporary_file" "$destination"
}

compose_up() {
    local image_uri="$1"
    local git_sha="$2"
    BACKEND_IMAGE="$image_uri" \
    BACKEND_ENV_FILE="$BLOCBUY_BACKEND_ENV_FILE" \
    BLOCBUY_GIT_SHA="$git_sha" \
        docker compose \
            --project-name "$BLOCBUY_PROJECT_NAME" \
            --file "$BLOCBUY_COMPOSE_FILE" \
            up --detach --no-deps --force-recreate backend
}

if [[ $# -lt 1 || $# -gt 2 ]]; then
    usage
    exit 2
fi

readonly requested_image="$1"
readonly requested_git_sha="${2:-unknown}"

if [[ ! "$requested_image" =~ $BLOCBUY_IMAGE_PATTERN ]]; then
    echo "Image must be a private Amazon ECR URI pinned by sha256 digest" >&2
    exit 2
fi

readonly aws_account_id="${BASH_REMATCH[1]}"
readonly aws_region="${BASH_REMATCH[2]}"
readonly ecr_repository="${BASH_REMATCH[3]}"
readonly ecr_registry="${requested_image%%/*}"

if [[ -n "${BLOCBUY_EXPECTED_AWS_ACCOUNT_ID:-}" && "$aws_account_id" != "$BLOCBUY_EXPECTED_AWS_ACCOUNT_ID" ]]; then
    echo "Image AWS account does not match BLOCBUY_EXPECTED_AWS_ACCOUNT_ID" >&2
    exit 2
fi

if [[ -n "${BLOCBUY_EXPECTED_AWS_REGION:-}" && "$aws_region" != "$BLOCBUY_EXPECTED_AWS_REGION" ]]; then
    echo "Image AWS Region does not match BLOCBUY_EXPECTED_AWS_REGION" >&2
    exit 2
fi

if [[ -n "${BLOCBUY_EXPECTED_ECR_REPOSITORY:-}" && "$ecr_repository" != "$BLOCBUY_EXPECTED_ECR_REPOSITORY" ]]; then
    echo "Image repository does not match BLOCBUY_EXPECTED_ECR_REPOSITORY" >&2
    exit 2
fi

if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
    echo "Run deploy.sh as root; SSM Run Command runs as root by default" >&2
    exit 1
fi

install -d -m 0750 "$BLOCBUY_STATE_DIR"
exec 9>"$BLOCBUY_STATE_DIR/deploy.lock"
if ! flock -n 9; then
    echo "Another BlocBuy backend deployment or rollback is already running" >&2
    exit 1
fi

"$BLOCBUY_DEPLOY_ROOT/bin/preflight.sh"

readonly docker_auth_dir="$BLOCBUY_STATE_DIR/docker-auth.$$"
# Invoked indirectly by the EXIT trap below.
# shellcheck disable=SC2317
cleanup_docker_auth() {
    rm -f "$docker_auth_dir/config.json"
    rmdir "$docker_auth_dir" 2>/dev/null || true
}
trap cleanup_docker_auth EXIT
install -d -m 0700 "$docker_auth_dir"

echo "Authenticating Docker to $ecr_registry"
aws ecr get-login-password --region "$aws_region" \
    | DOCKER_CONFIG="$docker_auth_dir" docker login --username AWS --password-stdin "$ecr_registry" >/dev/null

echo "Pulling immutable backend image: $requested_image"
DOCKER_CONFIG="$docker_auth_dir" docker pull "$requested_image"

previous_image="$(docker inspect --format '{{.Config.Image}}' "$BLOCBUY_CONTAINER_NAME" 2>/dev/null || true)"

echo "Deploying backend image: $requested_image"
compose_up "$requested_image" "$requested_git_sha"

if "$BLOCBUY_DEPLOY_ROOT/bin/health-check.sh"; then
    if [[ -n "$previous_image" && "$previous_image" != "$requested_image" ]] \
        && [[ "$previous_image" =~ $BLOCBUY_IMAGE_PATTERN ]]; then
        atomic_write "$BLOCBUY_STATE_DIR/previous-image" "$previous_image"
    elif [[ "$previous_image" != "$requested_image" ]]; then
        rm -f "$BLOCBUY_STATE_DIR/previous-image"
    fi
    atomic_write "$BLOCBUY_STATE_DIR/current-image" "$requested_image"
    atomic_write "$BLOCBUY_STATE_DIR/current-git-sha" "$requested_git_sha"
    echo "Backend deployment succeeded: $requested_image"
    exit 0
fi

echo "New backend failed its health check; recent logs follow" >&2
docker logs --tail 200 "$BLOCBUY_CONTAINER_NAME" >&2 || true

if [[ -z "$previous_image" || "$previous_image" == "$requested_image" ]]; then
    echo "No distinct previous backend image is available for automatic rollback" >&2
    BACKEND_IMAGE="$requested_image" \
    BACKEND_ENV_FILE="$BLOCBUY_BACKEND_ENV_FILE" \
        docker compose \
            --project-name "$BLOCBUY_PROJECT_NAME" \
            --file "$BLOCBUY_COMPOSE_FILE" \
            stop backend || true
    exit 1
fi

echo "Rolling back automatically to: $previous_image" >&2
compose_up "$previous_image" "automatic-rollback"

if "$BLOCBUY_DEPLOY_ROOT/bin/health-check.sh"; then
    atomic_write "$BLOCBUY_STATE_DIR/current-image" "$previous_image"
    atomic_write "$BLOCBUY_STATE_DIR/current-git-sha" "automatic-rollback"
    echo "Automatic rollback succeeded; deployment remains failed" >&2
else
    echo "CRITICAL: automatic rollback also failed" >&2
    docker logs --tail 200 "$BLOCBUY_CONTAINER_NAME" >&2 || true
fi

exit 1
