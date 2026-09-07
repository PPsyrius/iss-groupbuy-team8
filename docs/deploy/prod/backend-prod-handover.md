# Production Backend Handover — Manual Operations Before the First Deployment

**Status:** handover checklist for the operator who takes the prod environment live
**Date:** 2026-09-07
**Audience:** a team member with administrative AWS credentials on account `402178232802`
(region `ap-southeast-1`) and the `gh` CLI authenticated against `PPsyrius/iss-groupbuy-team8`
with admin rights.

Everything below happens **outside** the repository. The authoritative references are:

- Runbook (most detailed): [backend-prod-operations-runbook.md](backend-prod-operations-runbook.md)
- Design (rationale; wins on conflict): [backend-prod-terraform-deployment.md](backend-prod-terraform-deployment.md)
- Dev entry-layer tutorial (CloudFront pattern we replicate):
  [../dev-cloudfront-deployment.md](../dev-cloudfront-deployment.md)

---

## 0. Where we stand

Done (code, reviewed, `terraform fmt`/`validate` clean, shellcheck/actionlint clean):

- `infra/terraform/backend-app-stack/` — the whole prod stack: GitHub OIDC roles, `prod/blocbuy`
  ECR, CloudFront-origin ALB (managed prefix list + default-403 listener + `X-Origin-Secret`
  rule), EC2 instance with first-boot bootstrap (Docker, AWS CLI v2, loopback-only
  PostgreSQL/Redis Compose project).
- Pipeline extension in `.github/workflows/` — tag-triggered prod publish (`v*` tags), the
  `deploy-existing-ec2-prod` job behind the `backend-prod` approval environment, prod rollback
  target, security scanning of `infra/terraform/**`.

Not done (all manual, in order — sections 1 through 9 below):

- The pipeline PR is merged, values pinned in `envs/prod.tfvars`, secrets seeded, the stack
  applied, GitHub configured, the prod CloudFront distribution created, and the first release
  tag pushed.

**Two blocking values** still need filling before anything can run (section 2).

---

## 1. Merge the pipeline PR

Branch `chore/prod-pipeline-build-20260906` → PR into `dev`. Note the working tree currently
also holds the **CloudFront entry-layer changes** (uncommitted at the time of writing) — commit
and include them in the same PR. Nothing in sections 4–10 can run until this is merged: the
release tag must point at a commit that contains both the Terraform stack and the workflow
files.

## 2. Record the decisions and pin `envs/prod.tfvars`

| Variable | Decision | Action |
|---|---|---|
| `release_branch` | **confirmed `dev`** | already correct in tfvars — no change |
| `ami_id` | **must be pinned** (currently empty on purpose — validation fails until filled) | run the lookup below, paste the `ami-...` into `envs/prod.tfvars` |
| `key_name` | empty = SSM-only access (recommended; the backend SG has no port-22 ingress, so a key pair alone would be useless) | leave empty |
| `acm_certificate_arn` | empty = CloudFront entry pattern (user-facing TLS on CloudFront's built-in certificate; ALB is a private HTTP origin) | leave empty |

AMI lookup (Ubuntu 24.04, amd64 — matches t3.medium):

```bash
aws ssm get-parameters-by-path \
  --path /aws/service/canonical/ubuntu/server/24.04/stable/current \
  --region ap-southeast-1 \
  --query "Parameters[?starts_with(Name, 'amd64')].Value" \
  --output text
```

Pin the printed AMI ID — the `stable/current` pointer drifts; pinning is deliberate.

## 3. One-time: Terraform state backend (skip if it already exists)

```bash
aws s3api head-bucket --bucket blocbuy-tfstate-402178232802   # 404 → create it, else skip
```

If missing, create per runbook section 1: the versioned, KMS-encrypted, public-access-blocked
bucket `blocbuy-tfstate-402178232802` and the DynamoDB lock table `blocbuy-tflock`
(`LockID` hash key, PAY_PER_REQUEST, region `ap-southeast-1`).

## 4. One-time: GitHub `backend-prod` environment

Settings → Environments → New environment → `backend-prod`:

- **Required reviewers:** at least two team members (prod approvals are never single-person).
- **Deployment branches and tags:** selected → **tags matching `v*` only**. This is what makes
  the approval gate unreachable from branch pushes — do not skip it.
- No secrets, no variables yet (section 8 fills the variables).

## 5. Seed ALL secrets into SSM Parameter Store — **before the first `terraform plan`**

Ordering is load-bearing: the stack reads `/blocbuy/prod/ORIGIN_SECRET` **at plan time** (the
ALB listener rule references it), and the instance's user-data reads the first-boot keys at
instance creation. Seeding late fails the plan (best case) or produces a broken host (worst
case).

Generate one new origin secret and prepare one fresh PostgreSQL user/password (the prod
database is separate from dev), one Redis password, and the Stripe live API key/webhook secret:

```bash
export ORIGIN_SECRET=$(openssl rand -hex 32)   # keep for section 9; never commit it
```

All eleven parameters are `SecureString` under `/blocbuy/prod/`:

| Parameter | Value guidance | Consumed by |
|---|---|---|
| `DB_URL` | points at `127.0.0.1:5432/Blocbuy` (host-local infra project), schema suffix identical to dev | fetch-env.sh |
| `DB_USERNAME` / `DB_PASSWORD` | the new prod database user | fetch-env.sh |
| `REDIS_HOST` / `REDIS_PORT` | `127.0.0.1` / `6379` | fetch-env.sh |
| `REDIS_PASSWORD` | new, strong | fetch-env.sh **and** first boot |
| `STRIPE_API_KEY` / `STRIPE_WEBHOOK_SECRET` | live-mode values | fetch-env.sh |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | the same new prod database credentials | first boot |
| `ORIGIN_SECRET` | the `$ORIGIN_SECRET` generated above | the ALB listener rule (at plan time) **and** the CloudFront distribution (section 9) |

```bash
aws ssm put-parameter --name /blocbuy/prod/DB_PASSWORD \
  --type SecureString --value '<real value>' --region ap-southeast-1
# ...repeat for every row above; never echo values into a terminal log inside the repository
```

## 6. Plan, team review, apply

```bash
cd infra/terraform
terraform -chdir=backend-app-stack init \
  -backend-config bucket=blocbuy-tfstate-402178232802 \
  -backend-config region=ap-southeast-1
terraform -chdir=backend-app-stack plan -var-file=../envs/prod.tfvars -out=prod.tfplan
```

Read the plan against the runbook section 4 checklist before applying — specifically:

- [ ] Both GitHub trust policies: `sub` equals
      `repo:PPsyrius@19505219/iss-groupbuy-team8@1344446887:ref:refs/heads/dev` (ECR push) and
      `...:environment:backend-prod` (deploy) — exact match, no wildcards
- [ ] Backend SG: the only ingress for 8589 references the ALB SG; no 22/5432/6379 anywhere
- [ ] ALB SG: ingress is the CloudFront managed prefix list on the listener port only — no CIDR
- [ ] ALB listener: default 403; exactly one rule forwarding on `X-Origin-Secret`
- [ ] Target-group health path `/api/actuator/health`
- [ ] IMDSv2 required, encrypted 30 GiB volume, pinned AMI
- [ ] Plan shows only `+` (nothing outside this stack modified/destroyed)

```bash
terraform -chdir=backend-app-stack apply prod.tfplan
```

First boot takes several minutes (apt, Docker install, infra Compose project). The apply output
includes the user-data self-verification — a silent failure there means section 5 was seeded
late or incomplete.

## 7. Post-apply verification

```bash
INSTANCE_ID="$(terraform -chdir=infra/terraform output -raw instance_id)"

# SSM agent online
aws ssm describe-instance-information \
  --filters "Key=InstanceIds,Values=$INSTANCE_ID" --region ap-southeast-1 \
  --query 'InstanceInformationList[].{id:InstanceId,ping:PingStatus}'   # Online

# PostgreSQL/Redis on loopback ONLY
aws ssm send-command --instance-ids "$INSTANCE_ID" \
  --document-name AWS-RunShellScript --region ap-southeast-1 \
  --parameters 'commands=["ss -lnt | grep -E \"127\.0\.0\.1:(5432|6379)\" && ss -lnt | grep -vE \"127\.0\.0\.1:(5432|6379)|::1:(5432|6379)\" | grep -E \":(5432|6379)\" || echo LOOPBACK-ONLY-OK"]'

# Default-deny listener: direct-to-ALB without the secret must be 403 (with the secret: 503
# before the first deploy — no healthy target yet — and {"status":"UP"} after it)
ALB_URL="$(terraform -chdir=infra/terraform output -raw backend_url)"
curl -s -o /dev/null -w '%{http_code}\n' "$ALB_URL/api/actuator/health"   # 403
```

Record `instance_id`, `ecr_push_role_arn`, `deploy_role_arn`, `backend_url` (the ALB DNS —
it becomes the CloudFront origin in section 9, **not** `BACKEND_URL_PROD`).

## 8. Configure GitHub variables (values derived, never retyped)

```bash
terraform -chdir=infra/terraform output -json > /tmp/tf-outputs.json

gh variable set ECR_REPOSITORY_PROD     --body prod/blocbuy
gh variable set AWS_ECR_PUSH_ROLE_ARN_PROD \
  --body "$(jq -r .ecr_push_role_arn /tmp/tf-outputs.json)"
gh variable set ENABLE_EC2_DEPLOY_PROD  --body false   # freeze switch — stays false until section 10

gh variable set AWS_DEPLOY_ROLE_ARN_PROD --env backend-prod \
  --body "$(jq -r .deploy_role_arn /tmp/tf-outputs.json)"
gh variable set BACKEND_EC2_INSTANCE_ID_PROD --env backend-prod \
  --body "$(jq -r .instance_id /tmp/tf-outputs.json)"
```

`AWS_ACCOUNT_ID` and `AWS_REGION` already exist from dev — verify with `gh variable list`, do
not re-create. `BACKEND_URL_PROD` is set in section 9, after the distribution exists.
No AWS access keys and no env-file contents ever belong on GitHub.

## 9. One-time: create the prod CloudFront distribution

Follow [../dev-cloudfront-deployment.md](../dev-cloudfront-deployment.md) sections 6–7 with
prod values. Terraform already built the ALB half (prefix-list SG, 403 listener, secret-header
rule) — only the CloudFront side is manual:

- **Origin `alb-backend`:** DomainName = the `backend_url` output; `OriginProtocolPolicy:
  http-only`; custom header `X-Origin-Secret` = `$ORIGIN_SECRET` from section 5.
- **Cache behavior `/api/*` → `alb-backend`:** managed `CachingDisabled` (ID
  `4135ea2d-6df8-44a3-9df3-4b5a84be39ad`) + `AllViewerExceptHostHeader` (ID
  `b689b0a8-53d0-40ab-baf2-68738e2966ac`), `redirect-to-https`, viewer certificate = built-in
  `*.cloudfront.net` (no ACM, no custom domain).
- **Distribution Comment:** `BlocBuy prod - frontend S3 + backend ALB (/api)` — the lookup below
  matches on it exactly.
- **Frontend (optional):** a prod S3 bucket + OAC follows the dev tutorial verbatim with
  `blocbuy-prod-frontend-<account>` naming; skip if the frontend is not served yet.
- **Do not** create a second ALB, target group, or listener — those are Terraform-owned.

Then:

```bash
DIST_DOMAIN="$(aws cloudfront list-distributions \
  --query "DistributionList.Items[?Comment=='BlocBuy prod - frontend S3 + backend ALB (/api)'].DomainName" \
  --output text)"
gh variable set BACKEND_URL_PROD --env backend-prod --body "https://$DIST_DOMAIN/api"
```

## 10. First deployment drill (freeze switch opens at step 3)

1. **Rehearse closed:** push a throwaway tag `v0.0.0-rc1`. Expect: quality gates run, nothing
   publishes, nothing deploys (the freeze switch working). Verify the `security` workflow is
   green on that tag.
2. Confirm the docker job did NOT push to `prod/blocbuy` and `deploy-existing-ec2-prod` did NOT
   run.
3. `gh variable set ENABLE_EC2_DEPLOY_PROD --body true`
4. Cut the real release from the release branch (`dev`):

   ```bash
   git checkout dev && git pull
   git tag v0.1.0 && git push origin v0.1.0
   ```

5. Gates re-run on the tag; the docker job publishes to `prod/blocbuy` **by digest** (check the
   job summary: `repository@sha256:...`).
6. The `backend-prod` environment pauses — an assigned reviewer approves.
7. Inspect `deploy-existing-ec2-prod`: bundle installed, `fetch-env.sh` rendered the env file,
   SSM command `Success` (deploy.sh auto-rolls-back on failure — `Success` means the app was
   healthy).
8. End-to-end, the same URL users hit:

   ```bash
   curl --fail --silent "https://<prod-dist-domain>/api/actuator/health"        # {"status":"UP"}
   curl -s -o /dev/null -w '%{http_code}\n' \
     "$(terraform -chdir=infra/terraform output -raw backend_url)/api/actuator/health"   # 403 — by design
   aws elbv2 describe-target-health --target-group-arn <target_group_arn> --region ap-southeast-1
   ```

---

## Quick troubleshooting

| Symptom | Most likely cause |
|---|---|
| `terraform plan` fails on a missing parameter | Section 5 incomplete — `ORIGIN_SECRET` must exist before the first plan |
| ECR push role cannot be assumed on a tag | Tag not cut from `dev`, or `ENABLE_EC2_DEPLOY_PROD` still false |
| Deploy role cannot be assumed | `backend-prod` environment not restricted to `v*` tags, or a variable typo |
| CloudFront `/api/*` returns 502/503 | Origin-secret mismatch (distribution header ≠ SSM parameter — a one-sided rotation), or no healthy target |
| Direct request to the ALB DNS returns 403 | None — that is the design; go through CloudFront or send the secret header |
| SSM command never leaves Pending | Instance not Online (section 7), region mismatch |
| User-data failed at first boot | First-boot parameters seeded late/empty — check console output / cloud-init logs |

## Red lines (unchanged, non-negotiable)

- Never paste `/etc/blocbuy/backend.env`, Parameter Store values, the origin secret, or any
  application secret into issues, PRs, workflow logs, or files in this repository.
- Never relax an OIDC trust `sub` to a wildcard.
- Never open 5432/6379/22 to any CIDR.
- Never run `terraform apply` from CI; Terraform runs are human, reviewed, one-time.
- Never attach broad managed policies (`AmazonSSMFullAccess`,
  `AmazonEC2ContainerRegistryFullAccess`) to any role in this design.
- Application rollback never reverses Flyway migrations; prod migrations must stay
  backward-compatible.
