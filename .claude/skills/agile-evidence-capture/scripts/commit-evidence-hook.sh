#!/usr/bin/env bash
# PreToolUse hook (matcher: Bash) for the agile-evidence-capture skill.
#
# Before Claude runs `git commit`, look at what is staged and nudge for the evidence artefacts
# the team keeps: a sprint-log entry, an NFR register row when config/CI/security files change,
# an issue reference in the message. Advisory by default: exit 0 and hand Claude a short
# checklist via additionalContext. Set AGILE_EVIDENCE_HOOK_MODE=enforce to block instead
# (exit 2) until the artefact is staged or the message carries an `Evidence-skipped:` trailer.
#
# Non-git and non-commit commands pass through untouched. Requires jq.
set -euo pipefail

input="$(cat)"
tool="$(jq -r '.tool_name // ""' <<<"$input")"
[[ "$tool" == "Bash" ]] || exit 0
cmd="$(jq -r '.tool_input.command // ""' <<<"$input")"
cwd="$(jq -r '.cwd // "."' <<<"$input")"

# Only git commit. Tolerate env-var prefixes, chaining and `git -C <dir> commit`.
grep -Eq '(^|&&|;|\|\|)[[:space:]]*([A-Za-z_][A-Za-z0-9_]*=[^[:space:]]*[[:space:]]+)*git([[:space:]]+-C[[:space:]]+[^[:space:]]+)?[[:space:]]+commit([[:space:]]|$)' <<<"$cmd" || exit 0

mode="${AGILE_EVIDENCE_HOOK_MODE:-advise}"

# Commit message text, if given inline. Multi -m flags are concatenated. Messages may span
# lines, so flatten first; grep works line by line.
cmd_flat="$(tr '\n' ' ' <<<"$cmd")"
msg="$(grep -oE -- "-m[[:space:]]+(\"[^\"]*\"|'[^']*'|[^[:space:]]+)" <<<"$cmd_flat" | sed -E "s/^-m[[:space:]]+//; s/^[\"']//; s/[\"']\$//" | tr '\n' ' ' || true)"
type="$(grep -oE '^[a-z]+(\([^)]*\))?!?:' <<<"$msg" | head -1 || true)"

# Repository to inspect: the `-C <dir>` of the commit invocation when present, else the hook
# cwd. A relative -C path is resolved against cwd, as git itself would.
repo_dir="$cwd"
c_dir="$(grep -oE 'git[[:space:]]+-C[[:space:]]+[^[:space:]]+[[:space:]]+commit([[:space:]]|$)' <<<"$cmd_flat" | head -1 | sed -E 's/^git[[:space:]]+-C[[:space:]]+//; s/[[:space:]]+commit.*$//' | tr -d '"'"'" || true)"
if [[ -n "$c_dir" ]]; then
  case "$c_dir" in
    /*) repo_dir="$c_dir" ;;
    "~"|"~/"*) repo_dir="${HOME}${c_dir#"~"}" ;;
    *) repo_dir="$cwd/$c_dir" ;;
  esac
fi

# Chores are exempt: nothing to evidence.
case "$type" in
  style*|revert*|"chore(deps)"*|"chore(release)"*) exit 0 ;;
esac
if [[ "$msg" == *"Evidence-skipped:"* ]]; then exit 0; fi

git_dir_ok=false
if git -C "$repo_dir" rev-parse --is-inside-work-tree >/dev/null 2>&1; then git_dir_ok=true; fi
staged=""
if $git_dir_ok; then
  staged="$(git -C "$repo_dir" diff --cached --name-only 2>/dev/null || true)"
  # `git add ... && git commit` in one command: staged is still empty at hook time, so also
  # consider the paths the add names and, for `commit -a`, what is modified in the tree.
  if grep -Eq 'commit[^&;|]*[[:space:]]-a' <<<"$cmd_flat"; then
    staged="$staged"$'\n'"$(git -C "$repo_dir" status --porcelain 2>/dev/null | awk '{print $2}' || true)"
  fi
fi
add_args="$(grep -oE 'git[[:space:]]+add[[:space:]]+[^&;|]*' <<<"$cmd_flat" | sed -E 's/^git[[:space:]]+add[[:space:]]+//' | tr ' ' '\n' | grep -v '^-' || true)"
staged="$staged"$'\n'"$add_args"
staged="$(sed '/^$/d' <<<"$staged" | sort -u)"

# Nothing staged we can see: still advise, but only about the message.
touches_code=false; touches_nfr=false; has_log=false; has_register=false; has_adr=false
while IFS= read -r f; do
  [[ -z "$f" ]] && continue
  case "$f" in
    docs/agile/sprint-*/log.md) has_log=true ;;
    docs/quality/nfr-register.md) has_register=true ;;
    frontend/docs/adr/[0-9][0-9][0-9][0-9]-*.md) has_adr=true ;;
  esac
  case "$f" in
    *.md|*.txt|*.drawio|*.png|*.svg|*.docx|*.csv) ;;
    *) touches_code=true ;;
  esac
  case "$f" in
    .github/*|*/application*.yml|*/application*.yaml|deployment/*|infra/*|*Dockerfile*|docker-compose*|*cloudfront*|*/pom.xml|pom.xml|*/package.json|*vite.config.*|*lefthook*|*.gitleaks*|*checkstyle*|*spotbugs*|*/security/*|*Security*|*Cookie*|*Cors*|*RateLimit*|*Filter.java|*/db/migration/*)
      touches_nfr=true ;;
  esac
done <<<"$staged"

has_issue_ref=false
if grep -Eq '(#[0-9]+|Refs|Closes|Fixes)' <<<"$msg"; then has_issue_ref=true; fi
# With -F/--file or an editor we cannot see the message; don't nag about it.
if [[ -z "$msg" ]]; then has_issue_ref=true; fi

missing=()
if $touches_code && ! $has_log; then
  missing+=("- Sprint log: append an entry to docs/agile/sprint-NN/log.md (date, handle, issue, changed, verified, decisions, problems) and stage it with this commit.")
fi
if $touches_nfr && ! $has_register; then
  missing+=("- NFR register: this commit touches CI/config/security/infra paths. Add or update the row in docs/quality/nfr-register.md (scenario, enforcement point, evidence, status), or state in the log entry why it is not an NFR.")
fi
if ! $has_issue_ref; then
  missing+=("- Issue reference: add 'Refs #N' or 'Closes #N' to the commit body so the issue -> PR chain exists.")
fi

if [[ ${#missing[@]} -eq 0 ]]; then exit 0; fi

checklist="agile-evidence-capture: before this commit lands, evidence looks incomplete."$'\n'
checklist+="$(printf '%s\n' "${missing[@]}")"$'\n'
checklist+="If skipping is deliberate, add a commit trailer: Evidence-skipped: <reason>. Templates: .claude/skills/agile-evidence-capture/references/templates.md"

if [[ "$mode" == "enforce" ]]; then
  printf '%s\n' "$checklist" >&2
  exit 2
fi

jq -n --arg ctx "$checklist" '{hookSpecificOutput:{hookEventName:"PreToolUse",additionalContext:$ctx}}'
exit 0
