#!/usr/bin/env bash
# Converge the dev demo listings (US34, #222) through the audited admin API. No SQL, no deletes.
#
# Usage:
#   export BLOCBUY_TOKEN=...   # admin's blocbuy-token cookie value; never echoed or written to disk
#   scripts/seed-dev-demo-listings.sh           # create / publish / configure, then print a summary
#   scripts/seed-dev-demo-listings.sh --check   # no admin writes: summary plus one quote per listing
#
# Optional environment:
#   BLOCBUY_BASE_URL     API root, default the dev CloudFront entry (…/api/v1); https only, or
#                        http to localhost, 127.0.0.1 or [::1] for a local backend
#   BLOCBUY_SEED_FILE    listing definitions, default scripts/dev-demo-listings.json
#   BLOCBUY_POLICY_DAYS  validity of a newly appended team price policy, default 30
#
# A rerun on a converged environment makes zero writes. A listing whose latest policy is missing,
# not yet in force, priced differently or ending within BLOCBUY_REFRESH_DAYS gets a new policy
# through PUT /admin/products/{id}/team-terms. Runbook: docs/deploy/backend-dev-api-agent-verification.md.
set -euo pipefail

BASE=${BLOCBUY_BASE_URL:-https://d1b3np88z657cp.cloudfront.net/api/v1}
DATA=${BLOCBUY_SEED_FILE:-$(cd "$(dirname "$0")" && pwd)/dev-demo-listings.json}
POLICY_DAYS=${BLOCBUY_POLICY_DAYS:-30}
REFRESH_DAYS=${BLOCBUY_REFRESH_DAYS:-7}
# START needs the policy in force for the 2400 s quote window (CatalogPurchaseSupportImpl); keep
# an hour of margin on top, because the server does not reject shorter windows yet (#264).
MIN_WINDOW_SECONDS=$((2400 + 3600))
# Server clocks may trail this host; a validFrom in the server's future would block START.
CLOCK_SKEW_SECONDS=60

MODE=seed
case "${1-}" in
  "") ;;
  --check) MODE=check ;;
  *) echo "usage: $0 [--check]" >&2; exit 2 ;;
esac

: "${BLOCBUY_TOKEN:?export BLOCBUY_TOKEN with the admin blocbuy-token cookie value}"
command -v jq >/dev/null || { echo "jq is required" >&2; exit 2; }
[[ -r "$DATA" ]] || { echo "cannot read $DATA" >&2; exit 2; }
if ! [[ "$POLICY_DAYS" =~ ^[0-9]+$ && "$REFRESH_DAYS" =~ ^[0-9]+$ ]] \
    || ((POLICY_DAYS <= REFRESH_DAYS || REFRESH_DAYS * 86400 < MIN_WINDOW_SECONDS)); then
  echo "need integer days with BLOCBUY_POLICY_DAYS > BLOCBUY_REFRESH_DAYS >= 1" >&2
  exit 2
fi
# The token is a credential: curl may speak HTTPS only, or plain HTTP to an explicit loopback root.
LOOPBACK_HTTP='^http://(localhost|127\.0\.0\.1|\[::1\])(:[0-9]+)?(/|$)'
if [[ "$BASE" =~ $LOOPBACK_HTTP ]]; then
  PROTO==http
elif [[ "$BASE" == https://* ]]; then
  PROTO==https
else
  echo "BLOCBUY_BASE_URL must be https://, or http:// to localhost, 127.0.0.1 or [::1]" >&2
  exit 2
fi

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
WRITES=0

# api METHOD PATH [JSON_BODY] — prints `data` of the Result envelope; exits on any non-2xx.
# The cookie goes through a curl config on a file descriptor, so the token is never in argv.
api() {
  local method=$1 path=$2 body=${3-} status
  local args=(-sS --proto "$PROTO" -o "$WORK/response" -w '%{http_code}' -X "$method" -H 'Accept: application/json')
  if [[ -n "$body" ]]; then
    args+=(-H 'Content-Type: application/json' --data-binary @-)
  fi
  status=$(printf '%s' "$body" \
    | curl "${args[@]}" -K <(printf 'header = "Cookie: blocbuy-token=%s"\n' "$BLOCBUY_TOKEN") \
        "$BASE$path") || { echo "curl failed: $method $path" >&2; exit 1; }
  if [[ "$status" != 2* ]]; then
    echo "HTTP $status on $method $path" >&2
    jq -r '"  code=\(.code // "-") message=\(.message // "-") traceId=\(.traceId // "-")"' \
      "$WORK/response" >&2 2>/dev/null || head -c 300 "$WORK/response" >&2
    exit 1
  fi
  jq '.data' "$WORK/response"
}

write() {
  WRITES=$((WRITES + 1))
  api "$@" >/dev/null
}

now_epoch() { jq -n 'now | floor'; }

# Bounded read of every managed listing (page size 100), indexed by productNo.
load_index() {
  local page=1 total
  echo '[]' >"$WORK/all"
  while :; do
    api GET "/admin/products?page=$page&size=100" >"$WORK/page"
    jq -s '.[0] + .[1].items' "$WORK/all" "$WORK/page" >"$WORK/all.next"
    mv "$WORK/all.next" "$WORK/all"
    total=$(jq '.total' "$WORK/page")
    ((page * 100 < total)) || break
    page=$((page + 1))
  done
}

listing_by_no() { jq -c --arg no "$1" 'map(select(.productNo == $no)) | first // empty' "$WORK/all"; }

# Prints "ok" or the reason the terms and policy must be (re)written.
terms_verdict() {
  local listing=$1 want=$2 now=$3
  jq -rn --argjson l "$listing" --argjson w "$want" --argjson now "$now" \
    --argjson refresh $((REFRESH_DAYS * 86400)) '
    def ts: sub("\\.[0-9]+"; "") | fromdate;
    ($l.latestPolicy) as $p
    | if $l.teamTerms.targetSize != $w.targetSize or $l.teamTerms.lifetimeSeconds != $w.lifetimeSeconds
        then "terms differ"
      elif $p == null then "no policy"
      elif ($p.amount | tonumber) != ($w.teamPrice | tonumber) then "price differs"
      elif ($p.validFrom | ts) > $now then "policy not in force"
      elif ($p.validUntil | ts) - $now < $refresh then "policy ends within the refresh window"
      else "ok" end'
}

# Comparable listing content: decimal strings as numbers, absent image list as empty, sorted keys.
content() { jq -cS '.listing | .originalPrice |= tonumber | .images |= (. // [])' <<<"$1"; }

converge() {
  local want=$1 no listing id now verdict body
  no=$(jq -r '.productNo' <<<"$want")
  listing=$(listing_by_no "$no")

  if [[ -z "$listing" ]]; then
    echo "$no: create"
    write POST /admin/products "$(jq -c '{productNo, listing}' <<<"$want")"
    load_index
    listing=$(listing_by_no "$no")
  elif [[ "$(content "$listing")" != "$(content "$want")" ]]; then
    # Content edits are left to the admin console; frozen fields cannot change after publish.
    echo "$no: WARNING content differs from $DATA; not changed" >&2
  fi
  id=$(jq -r '.id' <<<"$listing")

  if [[ "$(jq -r '.status' <<<"$listing")" != on_sale ]]; then
    echo "$no: publish"
    write PUT "/admin/products/$id/status" \
      "$(jq -c '{status: "on_sale", expectedVersion: .version}' <<<"$listing")"
    listing=$(api GET "/admin/products/$id" | jq -c .)
  fi

  now=$(now_epoch)
  verdict=$(terms_verdict "$listing" "$want" "$now")
  if [[ "$verdict" != ok ]]; then
    echo "$no: configure team terms and price ($verdict)"
    body=$(jq -cn --argjson l "$listing" --argjson w "$want" --argjson now "$now" \
      --argjson from $((now - CLOCK_SKEW_SECONDS)) --argjson days "$POLICY_DAYS" '{
        expectedVersion: $l.version,
        targetSize: $w.targetSize,
        lifetimeSeconds: $w.lifetimeSeconds,
        teamPrice: {
          amount: $w.teamPrice,
          currency: $w.listing.currency,
          validFrom: ($from | todate),
          validUntil: ($now + $days * 86400 | todate),
          expectedVersion: ($l.latestPolicy.version // 0)
        }}')
    write PUT "/admin/products/$id/team-terms" "$body"
  fi
}

# --check: a quote exercises the same pricing path START uses (policy in force, undercut, terms).
# The quote does not echo the terms, so they are compared first and a mismatch skips the quote.
check_quote() {
  local want=$1 listing id amount
  listing=$(listing_by_no "$(jq -r '.productNo' <<<"$want")")
  [[ -n "$listing" ]] || { echo "$(jq -r '.productNo' <<<"$want"): missing" >&2; return 1; }
  if [[ "$(jq -n --argjson l "$listing" --argjson w "$want" \
      '$l.teamTerms.targetSize == $w.targetSize and $l.teamTerms.lifetimeSeconds == $w.lifetimeSeconds')" \
      != true ]]; then
    echo "$(jq -r '.productNo' <<<"$want"): team terms differ from the seed; no quote requested" >&2
    return 1
  fi
  id=$(jq -r '.id' <<<"$listing")
  amount=$(api POST /team-price-quotes "$(jq -cn --arg id "$id" '{productId: $id}')" | jq -r '.amount')
  if [[ "$(jq -n --arg a "$amount" --arg w "$(jq -r '.teamPrice' <<<"$want")" \
      '($a | tonumber) == ($w | tonumber)')" != true ]]; then
    echo "$(jq -r '.productNo' <<<"$want"): quote '$amount' does not match the seed price" >&2
    return 1
  fi
  echo "$(jq -r '.productNo' <<<"$want"): quote ok ($amount)"
}

summary() {
  load_index
  printf '%-8s %-36s %-8s %-6s %-9s %-8s %s\n' productNo id status size lifetime price validUntil
  jq -r --slurpfile want "$DATA" '
    ($want[0] | map(.productNo)) as $nos
    | map(select(.productNo as $n | $nos | index($n)))
    | sort_by(.productNo)[]
    | [.productNo, .id, .status, (.teamTerms.targetSize // "-"), (.teamTerms.lifetimeSeconds // "-"),
       (.latestPolicy.amount // "-"), (.latestPolicy.validUntil // "-")] | @tsv' "$WORK/all" \
    | while IFS=$'\t' read -r a b c d e f g; do
        printf '%-8s %-36s %-8s %-6s %-9s %-8s %s\n' "$a" "$b" "$c" "$d" "$e" "$f" "$g"
      done
}

load_index
if [[ "$MODE" == seed ]]; then
  while IFS= read -r want; do
    converge "$want"
    load_index
  done < <(jq -c '.[]' "$DATA")
  echo "admin writes: $WRITES"
  summary
else
  summary
  failed=0
  while IFS= read -r want; do
    check_quote "$want" || failed=1
  done < <(jq -c '.[]' "$DATA")
  exit "$failed"
fi
