#!/usr/bin/env bash
# PostToolUse Edit|Write: format and autofix the touched frontend source file.
set -euo pipefail
# jq is not pinned anywhere in this repo and is absent from a stock macOS; under `set -e` the
# command substitution below would abort the hook with 127. Degrade to a no-op instead.
command -v jq >/dev/null 2>&1 || exit 0
input="$(cat)"
file="$(printf '%s' "$input" | jq -r '.tool_input.file_path // empty')"
[ -n "$file" ] || exit 0
root="${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel)}"
case "$file" in
  "$root"/frontend/*|frontend/*) ;;
  *) exit 0 ;;
esac
# A repo-relative path would resolve as `frontend/frontend/...` after the `cd` below.
case "$file" in /*) ;; *) file="$root/$file" ;; esac
case "$file" in
  */src/api/generated/*|*/routeTree.gen.ts) exit 0 ;;
  *.ts|*.tsx|*.js|*.mjs|*.cjs|*.json|*.css|*.md|*.yml|*.yaml) ;;
  *) exit 0 ;;
esac
cd "$root/frontend"
[ -d node_modules ] || exit 0
pnpm exec oxfmt "$file" >/dev/null 2>&1 || true
case "$file" in
  *.ts|*.tsx) pnpm exec oxlint --fix "$file" >/dev/null 2>&1 || true ;;
esac
exit 0
