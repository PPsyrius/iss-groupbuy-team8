#!/usr/bin/env bash
set -Eeuo pipefail

readonly BLOCBUY_DEPLOY_ROOT="${BLOCBUY_DEPLOY_ROOT:-/opt/blocbuy/backend}"
readonly BLOCBUY_PREVIOUS_IMAGE_FILE="$BLOCBUY_DEPLOY_ROOT/state/previous-image"

if [[ $# -gt 1 ]]; then
    echo "Usage: $0 [immutable-ecr-image-uri]" >&2
    exit 2
fi

if [[ $# -eq 1 ]]; then
    target_image="$1"
elif [[ -s "$BLOCBUY_PREVIOUS_IMAGE_FILE" ]]; then
    target_image="$(<"$BLOCBUY_PREVIOUS_IMAGE_FILE")"
else
    echo "No previous image is recorded; provide an immutable ECR image URI" >&2
    exit 1
fi

exec "$BLOCBUY_DEPLOY_ROOT/bin/deploy.sh" "$target_image" "manual-rollback"
