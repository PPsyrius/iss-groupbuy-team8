#!/usr/bin/env bash
# Snapshot GitHub issues and the Kanban board into docs/agile/metrics/YYYY-MM-DD.csv.
#
# Why: the board UI is not part of the git archive and the Sprint 1 report had no burndown data.
# One CSV per day, committed with the day's work, is enough to draw a burndown and to reconstruct
# who moved what when. Re-running on the same day overwrites that day's file.
#
# Usage: snapshot-board.sh [--summary] [--out-dir DIR]
#   --summary   also print counts per board status to stdout
#   --out-dir   write somewhere other than docs/agile/metrics (used by tests)
# Env: REPO (default PPsyrius/iss-groupbuy-team8), PROJECT_OWNER (PPsyrius), PROJECT_NUMBER (2)
#
# Needs `gh` authenticated with repo scope. Board columns need the `project` scope
# (`gh auth refresh -s project`); without it the status/size/estimate columns are blank and a
# warning is printed, which is still a usable issue-state snapshot.
set -euo pipefail

REPO="${REPO:-PPsyrius/iss-groupbuy-team8}"
PROJECT_OWNER="${PROJECT_OWNER:-PPsyrius}"
PROJECT_NUMBER="${PROJECT_NUMBER:-2}"
summary=false
out_dir=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --summary) summary=true; shift ;;
    --out-dir) out_dir="$2"; shift 2 ;;
    *) echo "unknown arg: $1" >&2; exit 1 ;;
  esac
done

root="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
out_dir="${out_dir:-$root/docs/agile/metrics}"
mkdir -p "$out_dir"
today="$(date +%F)"
out="$out_dir/$today.csv"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

gh issue list -R "$REPO" --state all --limit 500 \
  --json number,title,state,labels,assignees,createdAt,closedAt,milestone >"$tmp/issues.json"

if gh project item-list "$PROJECT_NUMBER" --owner "$PROJECT_OWNER" --limit 500 --format json >"$tmp/board.json" 2>"$tmp/board.err"; then
  :
else
  echo "warning: board not readable ($(head -c 200 "$tmp/board.err")); status columns left blank. Try: gh auth refresh -s project" >&2
  echo '{"items":[]}' >"$tmp/board.json"
fi

jq -r --arg d "$today" --slurpfile board "$tmp/board.json" '
  (($board[0].items // [])
    | map(select(.content != null and .content.number != null))
    | map({key: (.content.number|tostring), value: .})
    | from_entries) as $b
  | (["snapshot_date","number","title","state","board_status","labels","assignees","size","estimate","priority","milestone","created_at","closed_at"] | @csv),
    (.[] | . as $i | ($b[($i.number|tostring)] // {}) as $bi
      | [ $d, $i.number, $i.title, $i.state,
          ($bi.status // ""),
          ($i.labels | map(.name) | join("|")),
          ($i.assignees | map(.login) | join("|")),
          ($bi.size // ""), ($bi.estimate // "" | tostring), ($bi.priority // ""),
          ($i.milestone.title // ""), $i.createdAt, ($i.closedAt // "") ]
      | @csv)
' "$tmp/issues.json" >"$out"

echo "wrote $out ($(($(wc -l <"$out") - 1)) issues)"

if $summary; then
  echo
  echo "Board status counts ($today):"
  jq -r '(.items // []) | map(.status // "(no status)") | group_by(.) | map("\(.[0]): \(length)") | .[]' "$tmp/board.json"
  echo "Issues: open $(jq '[.[] | select(.state=="OPEN")] | length' "$tmp/issues.json"), closed $(jq '[.[] | select(.state=="CLOSED")] | length' "$tmp/issues.json")"
fi
