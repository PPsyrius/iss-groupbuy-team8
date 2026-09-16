#!/usr/bin/env bash
# Stop: typecheck frontend when frontend files changed in this working tree.
set -euo pipefail
input="$(cat)"
if [ "$(printf '%s' "$input" | jq -r '.stop_hook_active // false')" = "true" ]; then exit 0; fi
root="${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel)}"
cd "$root"
changed="$(git status --porcelain -- frontend | grep -Ev 'frontend/(docs|e2e/\.auth)/' || true)"
[ -n "$changed" ] || exit 0
[ -d frontend/node_modules ] || exit 0
cd frontend
if ! out="$(pnpm exec tsc --noEmit 2>&1)"; then
  printf 'frontend typecheck failed:\n%s\n' "$out" >&2
  exit 2
fi
exit 0
