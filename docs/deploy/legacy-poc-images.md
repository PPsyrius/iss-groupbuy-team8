# Dev PoC legacy-image repair and remaining policy rollout

Refs #636–#641, NFR-16/NFR-71. This is an operator-only data repair, not a Flyway migration, application deployment or permission to change production. The operator runs all AWS/database commands; agents do not execute them. Keep the strict canonical CSP unchanged.

Status update (2026-10-27): the owner completed the repair and authorized dev policy apply, exact live header checks and a final zero-drift plan. [Aggregate operational evidence and limitations](../quality/evidence/zap/poc-image-repair-2026-10-27/README.md) supersede the earlier prepared/not-applied state. Do not rerun the repair: the legacy targets are gone. The procedure below remains recovery/reproduction guidance, not a request for another CloudShell session.

## Decision and observed prerequisite — 2026-10-27

The owner ran aggregate read-only queries through SSM on `BlocBuy-dev` (`i-0852b6c3475b19f05`), PostgreSQL container `postgres18-postgres-1`, database `BlocBuy`, schema `domain`. The inventory found 19 `https://loremflickr.com` references: three covers, six gallery entries, five historical order images and five purchase-intent images. The three products are two soft-deleted drafts and one non-deleted off-sale listing; none is on sale. The five non-deleted orders are four confirmed and one pending. All five intents are fulfilled with expired quotes, but one still has a future business deadline. These are not grounds to delete orders or alter payment/team state. Other stored origins were the approved catalog CDN (14 covers, 28 gallery entries, seven orders, 18 intents) and Google avatars (five user/five OAuth entries). The first inventory attempt used the default `public` search path and failed; the versioned query now selects `domain` transaction-locally.

The owner explicitly accepts substituting pictures for this PoC, including historical snapshots. Use ONE operator-reviewed, existing on-sale cover at `https://dcsjrkmummxi7.cloudfront.net/` as the replacement. This does not preserve historical picture identity and must not be generalized to production. No third-party fetch, image upload, policy exception or record deletion is needed. Only image fields and their `version`/`update_time` metadata change; versions advance to prevent stale application writes. Prices, ownership, statuses, payment/team references, deadlines and fulfilment stay unchanged. Rollback restores images but advances versions again rather than rewinding optimistic-lock tokens.

## Prepare before the next CloudShell session

1. Review the repair scripts and their disposable PostgreSQL tests. The owner may separately authorize an operator-only PoC data correction before a follow-up evidence PR; a prior PR is not inherently required for that correction. Keep commits/pushes/PR updates with the owner and explicitly record whether the scripts are uncommitted. CloudFront policy publication still uses reviewed, merged Terraform source. No hosted result is claimed for uncommitted changes.
2. Prepare a small archive FROM THAT SHA, not a dirty checkout or either stale CloudShell working tree. Include `docs/deploy/queries/`, this runbook, `infra/terraform/frontend-stack/` (including `.terraform.lock.hcl`), `infra/terraform/envs/frontend-dev.tfvars`, and `deployment/frontend/cloudfront/`. Use `git archive` so `.terraform`, state, credentials and generated files cannot be included. Record its SHA-256 locally and upload it through CloudShell's file-upload menu. Preserve the existing CloudShell repositories and their local edits.
3. Confirm normal `ci-frontend` delivery on the policy SHA completed `package-dev`, `deploy-s3-dev` and `e2e-dev`; a skipped post-merge `frontend-check` is not a new check. Run `40453051775` completed those three jobs successfully on merged `2f23d9236a0870156e2ee46af76877e4cc5386ee`; this is workflow evidence, not a new live header or asset-digest inspection. Confirm the later repair merge's delivery as appropriate.
4. Have a browser/account ready for real Google login/return, invitation copy/share, historical-order image checks and approved Stripe sandbox return checks. Physical iOS acceptance cannot be replaced by touch emulation. Those checks and the GitHub verification/ZAP dispatches can run outside CloudShell; do not close findings based only on Terraform success.

The next session should complete the following ordered checklist, with explicit review gates. AWS permissions, new data or unrelated Terraform drift can still require a stop; do not force progress to satisfy a one-session target.

After the PR merges and the owner checks out its exact merged SHA locally, the Windows preparation commands are:

```powershell
$sha = git rev-parse HEAD
$archive = Join-Path $env:LOCALAPPDATA "Temp/opencode/blocbuy-poc-repair-$sha.tar.gz"
git archive --format=tar.gz --output="$archive" HEAD docs/deploy/queries docs/deploy/legacy-poc-images.md infra/terraform/frontend-stack infra/terraform/envs/frontend-dev.tfvars deployment/frontend/cloudfront
if ($LASTEXITCODE -ne 0) { throw 'Archive creation failed' }
Get-FileHash "$archive" -Algorithm SHA256
Write-Output "Reviewed source: $sha"
Write-Output "Upload: $archive"
```

Do not run these archive commands before committing/merging: `git archive HEAD` intentionally excludes local edits. This package requires no new CloudShell Git clone, provider upload or host NodeJS installation. Compare the uploaded file using `sha256sum`, extract with `tar -xzf` into a new `mktemp -d` directory, and set `RELEASE_ROOT` to that directory.

## 1. CloudShell target, private workspace and policy plan FIRST

Use the [dev edge runbook](dev-cloudfront-deployment.md) and [frontend stack guide](../../infra/terraform/frontend-stack/README.md) for the canonical target and private origin-secret handling. Confirm account `402178232802`, Region `ap-southeast-1`, distribution `E3U5W7UWDL8LRV`, domain `d1b3np88z657cp.cloudfront.net`, and status `Deployed`. Recheck the SSM host name/state before any database write. Do not use the old `blocbuy-import` HCL or overwrite existing checkout changes.

Extract the reviewed archive into a NEW private directory; compare its SHA-256 with the locally recorded value first. CloudShell previously had only 343 MB free in `$HOME`: put the Terraform download/data/cache on its ephemeral `/tmp` volume, not in home, and check both filesystems before init. For example, after setting `RELEASE_ROOT` to the extracted archive root:

```bash
umask 077
df -h "$HOME" /tmp
TF_PRIVATE=$(mktemp -d /tmp/blocbuy-policy.XXXXXX)
export TF_DATA_DIR="$TF_PRIVATE/data"
export TF_PLUGIN_CACHE_DIR="$TF_PRIVATE/plugins"
mkdir -m 700 "$TF_PLUGIN_CACHE_DIR"
cd "$RELEASE_ROOT"
```

Capture the existing origin secret privately as in the stack guide (`set +x`, no printed origin config), then init with the committed provider lockfile (`-lockfile=readonly`), format-check and validate. Save a PRIVATE plan before repairing data, so unrelated infrastructure blockers are found first:

```bash
terraform -chdir=infra/terraform/frontend-stack plan \
  -input=false -no-color -detailed-exitcode \
  -var-file=../envs/frontend-dev.tfvars \
  -out="$TF_PRIVATE/policy.tfplan" >"$TF_PRIVATE/plan.log" 2>&1
PLAN_EXIT=$?
printf 'Plan exit code: %s\n' "$PLAN_EXIT"
```

Exit 1 is an error; inspect privately, never paste the log. Expected exit 2 with exactly ONE in-place update of `aws_cloudfront_response_headers_policy.security`; no import, create, delete, replacement, distribution/origin/function/IAM/S3 change or secret rotation is allowed. Inspect the changed policy fields privately against the canonical JSON. Only share address/action summaries, never full plan/state/JSON. A no-change plan is not evidence of first-time publication; compare live headers and establish why no change exists. Do not apply yet.

## 2. Stage the reviewed SQL on EC2, without another checkout

From CloudShell, transfer only the three reviewed SQL files using an SSM command. This creates a new private `/var/tmp` directory on the confirmed dev host, without secrets, installation or repository changes. The payload contains SQL source only, not database output. Requires `python3` on EC2 and the already-confirmed Docker/PostgreSQL setup. Generate the command from the extracted archive:

```bash
export POC_STAGE="/var/tmp/blocbuy-poc-images-$(date -u +%Y%m%dT%H%M%SZ)"
python3 - "$RELEASE_ROOT" "$POC_STAGE" >"$TF_PRIVATE/stage.json" <<'PY'
import base64, json, pathlib, shlex, sys
root, stage = pathlib.Path(sys.argv[1]), sys.argv[2]
commands = ['set -eu', 'umask 077', 'mkdir -m 700 ' + shlex.quote(stage)]
for name in ('replace-legacy-poc-images.sql', 'rollback-legacy-poc-images.sql', 'image-origin-inventory.sql'):
    payload = base64.b64encode((root / 'docs/deploy/queries' / name).read_bytes()).decode()
    commands.append('printf %s ' + shlex.quote(payload) + ' | base64 --decode > ' + shlex.quote(stage + '/' + name))
commands.append('printf "Staged reviewed SQL: %s\\n" ' + shlex.quote(stage))
print(json.dumps({'commands': commands}))
PY
COMMAND_ID=$(aws ssm send-command --instance-ids i-0852b6c3475b19f05 \
  --document-name AWS-RunShellScript --parameters "file://$TF_PRIVATE/stage.json" \
  --query Command.CommandId --output text)
aws ssm wait command-executed --command-id "$COMMAND_ID" --instance-id i-0852b6c3475b19f05
aws ssm get-command-invocation --command-id "$COMMAND_ID" --instance-id i-0852b6c3475b19f05 \
  --query '{Status:Status,Output:StandardOutputContent,Error:StandardErrorContent}' --output json
printf 'Use this STAGED path inside EC2: %s\n' "$POC_STAGE"
aws ssm start-session --target i-0852b6c3475b19f05 --region ap-southeast-1
```

Require SSM success. Inside EC2, set `STAGED` to the exact printed directory, then use a root shell so paths/backup permissions stay private. Do not print env files or `docker inspect`:

```bash
sudo -i
umask 077
# Set this to the exact printed path, NOT a guessed latest directory.
read -r -p 'Staged SQL directory: ' STAGED
test -f "$STAGED/replace-legacy-poc-images.sql"
REPAIR_ROOT=$(mktemp -d /var/tmp/blocbuy-image-repair.XXXXXX)
DB=postgres18-postgres-1
docker exec "$DB" mkdir -m 700 "$REPAIR_ROOT"
for name in replace-legacy-poc-images.sql rollback-legacy-poc-images.sql image-origin-inventory.sql; do
  docker cp "$STAGED/$name" "$DB:$REPAIR_ROOT/$name"
done
REPLACEMENT_URL=$(docker exec -i "$DB" sh -c 'exec psql -X -At -v ON_ERROR_STOP=1 -U "${POSTGRES_USER:-postgres}" -d BlocBuy' <<'SQL'
SELECT cover_image FROM domain.biz_product
WHERE NOT deleted AND status='on_sale'
  AND cover_image ~ '^https://dcsjrkmummxi7[.]cloudfront[.]net/[^[:space:]?#]+$'
ORDER BY product_no LIMIT 1;
SQL
)
test -n "$REPLACEMENT_URL"
printf 'Public replacement to review: %s\n' "$REPLACEMENT_URL"
curl --fail --silent --show-error --max-time 30 --max-redirs 0 --proto '=https' \
  --output /dev/null --write-out '%{http_code} %{content_type}\n' "$REPLACEMENT_URL"
```

Require HTTP 200 and an `image/*` content type WITHOUT redirects, cookies, authentication or TLS bypass; open the public replacement in a browser and approve it as the generic PoC substitute. If empty/blocked/redirecting, stop and choose a different approved existing cover. The SQL independently requires the chosen URL to remain an on-sale approved-CDN cover.

## 3. Dry run, explicit repair and backup

Inside the same root EC2 shell, use separate new working directories for rehearsal and apply. The first transaction executes the complete updates but ROLLBACKS by default; SQL count assertions must pass before a private CSV is written. The expected counts are 3 products, 5 orders, 5 intents; 3 covers and 6 gallery entries. Writes are blocked for at most the bounded transaction; normal reads can continue. Never increase timeouts or relax counts to work around a failure without reviewing new evidence.

```bash
docker exec "$DB" mkdir -m 700 "$REPAIR_ROOT/dry-run" "$REPAIR_ROOT/apply"
docker exec --workdir "$REPAIR_ROOT/dry-run" "$DB" sh -c \
  'umask 077; exec psql -X -v ON_ERROR_STOP=1 -U "${POSTGRES_USER:-postgres}" -d BlocBuy -v replacement_url="$1" -f "$2"' \
  sh "$REPLACEMENT_URL" "$REPAIR_ROOT/replace-legacy-poc-images.sql"
```

Require `DRY RUN` and no SQL error. Review aggregate counts and confirm the owner still authorizes the PoC substitution; only then execute:

```bash
docker exec --workdir "$REPAIR_ROOT/apply" "$DB" sh -c \
  'umask 077; exec psql -X -v ON_ERROR_STOP=1 -U "${POSTGRES_USER:-postgres}" -d BlocBuy -v apply=on -v replacement_url="$1" -f "$2"' \
  sh "$REPLACEMENT_URL" "$REPAIR_ROOT/replace-legacy-poc-images.sql"
```

Require `APPLIED`. The backup is written BEFORE updates/commit and is never overwritten; export it immediately to the private HOST directory and retain it securely with the source SHA and operator action time:

```bash
docker cp "$DB:$REPAIR_ROOT/apply/legacy-image-backup.csv" "$REPAIR_ROOT/legacy-image-backup.csv"
chmod 600 "$REPAIR_ROOT/legacy-image-backup.csv"
sha256sum "$REPAIR_ROOT/legacy-image-backup.csv" >"$REPAIR_ROOT/backup.sha256"
docker exec -i "$DB" sh -c \
  'exec psql -X -v ON_ERROR_STOP=1 -U "${POSTGRES_USER:-postgres}" -d BlocBuy -f "$1"' \
  sh "$REPAIR_ROOT/image-origin-inventory.sql"
```

The backup contains private row IDs/image paths; do not paste, upload as a CI artifact or commit it. A failed export does not undo a committed repair: the original remains inside the PostgreSQL container at the exact printed path. Stop and recover that backup before policy publication. After apply, require NO `loremflickr.com` or other unapproved origin; the replacement CDN totals should become 17 covers, 34 gallery entries, 12 orders and 23 intents, with both Google-avatar counts still five, if nothing else changed. Reload the app and check historical images; do not flush Redis, restart services or alter transaction state.

## 4. Finish the already-reviewed edge rollout in the SAME CloudShell session

Exit the root shell, then the EC2 SSM shell to return to CloudShell. Keep `TF_PRIVATE`, `RELEASE_ROOT` and the private origin-secret variable in that original shell. Recheck the target/distribution and ensure no concurrent infrastructure changes occurred. Apply only the exact policy-only saved plan after explicit authorization:

```bash
terraform -chdir=infra/terraform/frontend-stack apply -input=false "$TF_PRIVATE/policy.tfplan" \
  >"$TF_PRIVATE/apply.log" 2>&1
```

Review success privately. Wait for propagation; distribution `Deployed` alone is insufficient for a separately updated response-headers policy. Run the exact GET/value comparison in [section 15](dev-cloudfront-deployment.md#15-security-response-headers) against root and the catalog API; require exact canonical values on both behaviors. Confirm root/deep-link HTML, missing-asset refusal and API health. Run a fresh detailed-exitcode plan against the same source/variables and require exit 0. Never print or attach full plans/logs/state; remove private Terraform plans/logs and unset the secret after recording non-secret outcomes. Retain the independent DB rollback backup securely, not under ephemeral CloudShell `/tmp`.

From the owner's GitHub-capable local terminal, separately authorize and dispatch `verify-dev-edge.yml` from merged `dev`, review its artifact and complete the real-provider/mobile checks, then authorize a fresh `dast-baseline.yml` run from `dev`. These do not require another CloudShell login. Record new workflow links, SHA, exact header results and six finding dispositions separately from frozen before evidence. No issue closure is automatic. If any check fails, use the rollback guidance below; do not weaken CSP silently.

## Rollback and stop conditions

- Data rollback: from the SAME private apply directory INSIDE the PostgreSQL container, run `rollback-legacy-poc-images.sql` with psql `-X -v ON_ERROR_STOP=1` and no `apply` variable for a rehearsal, then `-v apply=on` only after approval. It validates every backed-up identity/image/version/timestamp and aborts atomically if those fields changed after repair, a row is missing or the file is a rehearsal backup. No order status/price/deadline is restored. If guards fail, review a new targeted repair; never overwrite later application writes.
- Restoring legacy URLs makes the strict image CSP incompatible again. Keep publication paused or restore the previous reviewed edge policy FIRST. Edge rollback uses a separately reviewed Terraform policy-only plan from the known prior canonical policy, with no origin/function/IAM/S3 drift; do not use a manual CloudFront update or a stale whole-stack checkout.
- SQL errors, count drift, image-load failures, backup errors, lock contention, invalid provider lockfiles, unrelated Terraform changes or failed live integrations are stop conditions. A partial session is not a successful rollout. The repair is re-run-safe by refusing nonmatching counts, not by silently applying again. Do not delete the private backup or container repair directory until rollout/rollback retention has been agreed.

## Local verification (no AWS access)

Local Docker verification on 2026-10-27: all 12 integration tests passed against PostgreSQL 18; the existing Python tooling suite passed 238 tests including four expected skips; actionlint passed the changed security workflow; all eight Bash operator blocks passed `bash -n`. Integration tests also caught an initial psql exit-code assumption (`\quit 3` is not portable to the tested client); errors now use `ON_ERROR_STOP` with SQL exceptions, and the backup-no-overwrite regression passes. The inventory regression uses the real identity-table DDL and an explicit psql stop-on-error flag rather than accepting psql's successful exit after a SQL error. No frontend/application/API contract/DDL schema change, source-policy loosening or live repair is claimed.

`.github/tests/legacy_poc_images_integration.py` runs only with `POC_IMAGE_TEST_DATABASE=1` and database name `poc_image_migration_test`; it resets that disposable schema and must NEVER be pointed at dev/prod. The `poc-image-migration` security job uses an isolated PostgreSQL 18 service with no published host port and no real credentials. Tests execute real psql transactions using the repository's table DDL, not SQL-string mocks; cover default rollback, explicit apply, private backup/no-overwrite, reference/row-count guards, invalid replacements, repeat-apply refusal, null/empty galleries and guarded rollback after application writes. Hosted execution remains pending until the repair is pushed. No AWS/database mutation has been performed by the agent.
