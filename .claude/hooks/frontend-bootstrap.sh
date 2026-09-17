#!/usr/bin/env bash
# SessionStart: make sure the generated client exists; install git hooks once.
set -euo pipefail
root="${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel)}"
fe="$root/frontend"
[ -f "$fe/package.json" ] || exit 0
if [ ! -d "$fe/src/api/generated" ]; then
  # `pnpm install` only runs `prepare` (→ `gen:api`) when it actually installs something; with
  # node_modules already warm it is a no-op and the client stays missing, so regenerate
  # explicitly afterwards and report on the directory, not on the exit code.
  ( cd "$fe" \
    && corepack pnpm install --frozen-lockfile >/dev/null 2>&1 \
    && corepack pnpm run gen:api >/dev/null 2>&1 ) || true
  if [ -d "$fe/src/api/generated" ]; then
    echo "frontend: generated client was missing; ran pnpm install (prepare → gen:api)."
  else
    echo "frontend: pnpm install failed; run it by hand in frontend/ before editing."
  fi
fi
# `.git` is a file, not a directory, inside a worktree, so ask git where the hooks live
# instead of testing `-d "$root/.git"`. lefthook 2.1.14 writes no `lefthook.checksum`; the
# marker it leaves is the `call_lefthook` line stamped into each generated hook script.
hooks_dir="$(cd "$root" && git rev-parse --git-path hooks 2>/dev/null || true)"
case "$hooks_dir" in
  "") ;;
  /*) ;;
  *) hooks_dir="$root/$hooks_dir" ;;
esac
if [ -n "$hooks_dir" ] && [ -d "$fe/node_modules" ] \
  && ! grep -qs call_lefthook "$hooks_dir"/*; then
  ( cd "$fe" && pnpm exec lefthook install >/dev/null 2>&1 ) && echo "frontend: lefthook hooks installed."
fi
exit 0
