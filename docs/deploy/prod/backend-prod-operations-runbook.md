# Production Backend Operations Runbook (Post-Merge Manual Steps)

**Status:** executable checklist of manual operations — everything that happens **outside** the
repository after the [implementation plan](backend-prod-implementation-plan.md) is merged

**Audience/permissions:** sections 2–7 run on an **administrator workstation** with AWS
credentials authorized for S3, DynamoDB, SSM, EC2, ELB, IAM, and ECR. Section 6 additionally needs
the `gh` CLI authenticated against `PPsyrius/iss-groupbuy-team8`. Nothing here is automated by the
pipeline — these steps are one-time (or per-release) by design.

**Companions:** rationale and architecture — [design document](backend-prod-terraform-deployment.md);
repo-side work — [implementation plan](backend-prod-implementation-plan.md). Where this runbook and
the design document conflict, the design document wins and this file must be corrected.

## 0. Prerequisites and values to collect

The implementation plan must be **merged** (all five phases or at minimum phases 1–3).

Decide and record the following — every later section references them:

| Value | Decision to make now | Note |
|---|---|---|
| `aws_account_id` | `402178232802` (existing) | fixed |
| `aws_region` | `ap-southeast-1` (existing) | fixed |
| `github_owner` / `github_owner_id` | `PPsyrius` / `19505219` | verified 2026-09-05 |
| `github_repo` / `github_repo_id` | `iss-groupbuy-team8` / `1344446887` | verified 2026-09-05 |
| `ecr_repository` | `prod/blocbuy` | fixed by the stack |
| `release_branch` | the branch release tags are cut from | team decision; the ECR push trust policy pins it |
| `instance_type` | `t3.medium` minimum | Spring Boot + PostgreSQL + Redis on one host |
| AMI | pin an Ubuntu 24.04 AMI ID into `envs/prod.tfvars` | do not ship a wildcard lookup to prod |
| `key_name` | omit (SSM-only) unless break-glass SSH is required | if set, the key pair must already exist |
| TLS / entry layer | CloudFront entry pattern, same as dev — **no ACM certificate, no custom domain** | user-facing TLS is CloudFront's built-in certificate; the ALB is a private origin (section 6.5) |
| Origin secret | `openssl rand -hex 32` — one new value, becomes `/blocbuy/prod/ORIGIN_SECRET` | shared CloudFront↔ALB; consumed by the listener rule at plan time |
| Release control | operator-controlled `ENABLE_EC2_DEPLOY_PROD` switch and `v*`-tag-only environments | native reviewer rules are unavailable on this private repo; no approval pause is claimed |

Secret values to prepare (all become Parameter Store SecureStrings in section 3): one PostgreSQL
user/password (new — prod database is separate from dev), one Redis password, the Stripe API key
and webhook secret, the application `DB_URL`/credentials/Redis host-port exactly as
[`backend.env.example`](../../../deployment/backend/backend.env.example) defines them, and one
`X-Origin-Secret` value (`openssl rand -hex 32`) shared between the CloudFront distribution and
the ALB listener rule.

## 1. One-time: Terraform state backend

On 2026-09-25 the account holder verified bucket versioning, KMS encryption, public-access blocking, active `blocbuy-tflock` (`LockID (S)`) and no backend-stack state object. Recheck with [design §5.1](backend-prod-terraform-deployment.md#51-bootstrap-the-terraform-state-backend) before provisioning; the §4 read-only plan was **not applied**. The bootstrap commands below are **new-account-only**; never replay them against this populated state bucket.

```bash
aws s3api create-bucket --bucket blocbuy-tfstate-402178232802 \
  --region ap-southeast-1 \
  --create-bucket-configuration LocationConstraint=ap-southeast-1

aws s3api put-bucket-versioning --bucket blocbuy-tfstate-402178232802 \
  --versioning-configuration Status=Enabled

aws s3api put-bucket-encryption --bucket blocbuy-tfstate-402178232802 \
  --server-side-encryption-configuration \
  '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"aws:kms"}}]}'

aws s3api put-public-access-block --bucket blocbuy-tfstate-402178232802 \
  --public-access-block-configuration \
  BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true

aws dynamodb create-table --table-name blocbuy-tflock \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST --region ap-southeast-1
```

- [ ] Bucket exists, versioned, KMS-encrypted, public access fully blocked
- [ ] Lock table active
- [ ] Recorded in the team change log

The GitHub OIDC provider already exists from the dev bootstrap — the stack looks it up
(`data "aws_iam_openid_connect_provider"`), never creates a second one.

## 2. One-time: GitHub environments (`backend-prod` and `backend-prod-publish`)

**2026-09-25 operator decision:** the account holder created `backend-prod` and `backend-prod-publish` explicitly before any release tag. Read-only API verification found exactly one deployment policy per environment: type `tag`, pattern `v*`, with **no branches or reviewer rule**. Native Required reviewers is unavailable in this private personal repository; the account holder chose tag scoping and an operator-controlled deploy switch rather than an unenforceable two-reviewer gate. Once the switch is true, a qualifying tag can deploy automatically—there is no approval pause.

- [x] `backend-prod`: selected `v*` tags only, no reviewer protection; environment variables await Terraform outputs in §6.
- [x] `backend-prod-publish`: selected `v*` tags only; its OIDC subject is trusted by the prod ECR push role.
- [x] `ENABLE_EC2_DEPLOY_PROD` absent on 2026-09-25; keep it unset/false through the closed-switch rehearsal and release-readiness review.

The publish job reads repository variables, not environment secrets. Recheck both tag policies before a release: a missing environment can be auto-created by a workflow without protection rules.

## 3. Seed secrets into SSM Parameter Store

**Ordering note: this must happen *before the first `terraform plan`*.** The stack reads
`ORIGIN_SECRET` at plan time (the ALB listener rule references it), and the instance's user-data
starts PostgreSQL/Redis at first boot using the other parameters — planning or applying before
seeding fails on the first and produces a broken host on the second.

All parameters are `SecureString`, path-prefixed `/blocbuy/prod/`:

| Parameter | Consumed by |
|---|---|
| `/blocbuy/prod/DB_URL` | backend env (fetch-env.sh) |
| `/blocbuy/prod/DB_USERNAME` | backend env (fetch-env.sh) |
| `/blocbuy/prod/DB_PASSWORD` | backend env (fetch-env.sh) |
| `/blocbuy/prod/REDIS_HOST` | backend env (fetch-env.sh) |
| `/blocbuy/prod/REDIS_PORT` | backend env (fetch-env.sh) |
| `/blocbuy/prod/REDIS_PASSWORD` | backend env (fetch-env.sh) **and** infra compose (first boot) |
| `/blocbuy/prod/BLOCBUY_SCHEDULING_ENABLED` | expiry and sandbox-refund worker master switch; seed `false` and enable after checking overdue teams and due refunds |
| `/blocbuy/prod/STRIPE_API_KEY` | backend env (fetch-env.sh) |
| `/blocbuy/prod/STRIPE_WEBHOOK_SECRET` | backend env (fetch-env.sh) |
| `/blocbuy/prod/POSTGRES_USER` | infra compose (first boot) |
| `/blocbuy/prod/POSTGRES_PASSWORD` | infra compose (first boot) |
| `/blocbuy/prod/ORIGIN_SECRET` | ALB listener rule (at plan time) **and** the CloudFront distribution's origin header (section 6.5) |

```bash
aws ssm put-parameter --name /blocbuy/prod/DB_PASSWORD \
  --type SecureString --value '<real value>' --region ap-southeast-1
# ... repeat for every row above
```

- [ ] All twelve parameters exist (`aws ssm describe-parameters --parameter-filters ...`)
- 2026-09-25 metadata check: eleven required SecureStrings existed; the account holder created the missing `/blocbuy/prod/BLOCBUY_SCHEDULING_ENABLED` as SecureString `false` (version 1, Standard tier) after approval and verified its name/type. The other eleven values were not decrypted or validated; this does not make the first production apply or release ready.
- [ ] No value was echoed to a terminal log, issue, or file inside the repository
- [ ] `REDIS_HOST`=`127.0.0.1`, `REDIS_PORT`=`6379`, `DB_URL` points at `127.0.0.1:5432/Blocbuy`
      (host-local infra project), schema suffix identical to dev
- [ ] `BLOCBUY_SCHEDULING_ENABLED=false` until overdue teams and due refund obligations have been counted; enabling it under `ec2` can send sandbox refunds

## 4. Plan, review, apply the stack

From the repository root, keep the saved plan local and access-restricted; it can contain the resolved `/blocbuy/prod/ORIGIN_SECRET` value. Do not attach raw plan/state output to a PR—record a redacted action summary and reviewer decision instead.

**2026-09-25 read-only plan (#48):** CloudShell reused a byte-identical signed AWS 5.100.0 provider after its first install ran out of space. The legacy `aws_prefix_list` lookup failed; local HCL now uses `aws_ec2_managed_prefix_list` for AWS-owned `pl-31a34658`. `fmt`/`validate` and a private plan passed: **21 creates, 0 updates/destroys**, with expected SGs, health path, IMDSv2, encryption and exact OIDC trust. The fix is unmerged, the plan/log were removed, the state key remains absent, and **nothing was applied**. Production deployment variables and credentials remain pending Terraform apply; recheck both `v*` environment policies before release. Release-readiness review and origin TLS remain blockers. Keep `ENABLE_EC2_DEPLOY_PROD` unset/false until the review; share only filtered plan actions.

```bash
umask 077
terraform -chdir=infra/terraform/backend-app-stack init \
  -backend-config="bucket=blocbuy-tfstate-402178232802" \
  -backend-config="region=ap-southeast-1" -lockfile=readonly -input=false
terraform -chdir=infra/terraform/backend-app-stack plan -input=false \
  -var-file=../envs/prod.tfvars -out=prod.tfplan
```

**Team plan review — read all of these in the plan output before applying** (this replaces the
dev bootstrap's "replace the placeholder then read it back" ritual):

- [ ] Both GitHub trust policies: `sub` equals
      `repo:PPsyrius@19505219/iss-groupbuy-team8@1344446887:environment:backend-prod-publish`
      (ECR push) and `...:environment:backend-prod` (deploy) — exact match, no wildcards
- [ ] Backend security group: the **only** ingress for 8589 references the ALB security group ID;
      no ingress for 22, 5432, or 6379 anywhere
- [ ] ALB security group: ingress is the CloudFront managed prefix list
      (`com.amazonaws.global.cloudfront.origin-facing`) on the listener port only — no CIDR blocks
- [ ] ALB listener: default action is the 403 fixed response; exactly one rule (priority 10)
      forwards on the `X-Origin-Secret` header match
- [ ] EC2 role inline policies: ECR pull resource is the `prod/blocbuy` repository ARN;
      `ssm:GetParameters` resource is `parameter/blocbuy/prod/*` in this account/region
- [ ] ALB target group health check path is `/api/actuator/health` (context path — see
      implementation plan §0.1)
- [ ] Instance: IMDSv2 required, encrypted root volume, pinned AMI, `ManagedBy=terraform` tag
- [ ] No resource outside this stack is being modified or destroyed (plan shows only `+`)

```bash
terraform -chdir=infra/terraform/backend-app-stack apply prod.tfplan
```

Apply creates: two GitHub OIDC roles, `prod/blocbuy` ECR, security groups, ALB + target group,
and the EC2 instance. User-data runs at first boot (Docker, AWS CLI, the `blocbuy-infra`
PostgreSQL/Redis project, loopback-only).

- [ ] `terraform apply` returned successfully; user-data bootstrap verified separately via the SSM check in section 5
- [ ] Change log entry (link the PR + the apply)

## 5. Post-apply verification

```bash
INSTANCE_ID="$(terraform -chdir=infra/terraform/backend-app-stack output -raw instance_id)"

# SSM agent online (first boot installs prerequisites; allow a few minutes)
aws ssm describe-instance-information \
  --filters "Key=InstanceIds,Values=$INSTANCE_ID" --region ap-southeast-1 \
  --query 'InstanceInformationList[].{id:InstanceId,ping:PingStatus}'
# PingStatus must be Online

# cloud-init finished AND user-data bootstrap completed (apply does not report this).
# send-command only submits: capture the ID, poll to a terminal status, require Success.
CMD_ID="$(aws ssm send-command --instance-ids "$INSTANCE_ID" \
  --document-name AWS-RunShellScript --region ap-southeast-1 \
  --parameters 'commands=["cloud-init status --wait && grep -F \"BlocBuy prod host bootstrap complete\" /var/log/cloud-init-output.log"]' \
  --query 'Command.CommandId' --output text)"
for _ in $(seq 1 60); do
  CMD_STATUS="$(aws ssm get-command-invocation --command-id "$CMD_ID" --instance-id "$INSTANCE_ID" --region ap-southeast-1 --query Status --output text 2>/dev/null || true)"
  case "$CMD_STATUS" in Success|Cancelled|TimedOut|Failed) break ;; esac
  sleep 10
done
aws ssm get-command-invocation --command-id "$CMD_ID" --instance-id "$INSTANCE_ID" --region ap-southeast-1 \
  --query '[Status, StandardOutputContent, StandardErrorContent]' --output text
# Status must be Success (10-minute cap above covers first-boot cloud-init); otherwise read the log tail via the same SSM path before continuing

# Infra listeners: loopback only, no public bind
aws ssm send-command --instance-ids "$INSTANCE_ID" \
  --document-name AWS-RunShellScript --region ap-southeast-1 \
  --parameters 'commands=["ss -lnt | grep -E \"127\.0\.0\.1:(5432|6379)\" && ss -lnt | grep -vE \"127\.0\.0\.1:(5432|6379)|::1:(5432|6379)\" | grep -E \":(5432|6379)\" || echo LOOPBACK-ONLY-OK"]'
# stdout must contain LOOPBACK-ONLY-OK

# Security groups: 8589 only from the ALB SG; nothing else ingress
aws ec2 describe-security-groups --region ap-southeast-1 \
  --group-names "$(terraform -chdir=infra/terraform/backend-app-stack output -raw backend_sg_name)" \
  --query 'SecurityGroups[].IpPermissions[]'

# ALB target health (after the first deploy there will be a target; before it, expect no targets)
aws elbv2 describe-target-health --region ap-southeast-1 \
  --target-group-arn "$(terraform -chdir=infra/terraform/backend-app-stack output -raw target_group_arn)"

# The ALB SG accepts CloudFront origins only. Direct requests from an admin workstation
# can time out before the listener; inspect its default action without reading rule secrets.
ALB_ARN="$(aws elbv2 describe-load-balancers --names blocbuy-prod \
  --query 'LoadBalancers[0].LoadBalancerArn' --output text --region ap-southeast-1)"
aws elbv2 describe-listeners --load-balancer-arn "$ALB_ARN" --region ap-southeast-1 \
  --query 'Listeners[].{port:Port,defaultStatus:DefaultActions[0].FixedResponseConfig.StatusCode}'
# Expect port 80 and defaultStatus 403; verify public API health through CloudFront in section 7.
```

- [ ] `PingStatus=Online`
- [ ] cloud-init done and user-data bootstrap completion line in the log
- [ ] PostgreSQL/Redis listening on `127.0.0.1` only
- [ ] No unexpected ingress rules
- [ ] `terraform output` prints `instance_id`, `ecr_push_role_arn`, `deploy_role_arn`,
      `backend_url` — record all four for section 6 (`backend_url` is the ALB DNS, consumed as
      the CloudFront origin DomainName in section 6.5 — it is NOT `BACKEND_URL_PROD`)

If SSM stays `ConnectionLost`: the agent needs a few minutes after instance-profile attachment;
then check VPC endpoints/NAT (see dev operator guide section 13).

## 6. Configure GitHub (scripted, values derived — never retyped)

Requires the `gh` CLI with admin on `PPsyrius/iss-groupbuy-team8`:

```bash
terraform -chdir=infra/terraform/backend-app-stack output -json > /tmp/tf-outputs.json

# Repository-level variables (read by both the publish job and the deploy job, which
# live in different environments — repository scope is what they share)
gh variable set ECR_REPOSITORY_PROD     --body prod/blocbuy
gh variable set AWS_ECR_PUSH_ROLE_ARN_PROD \
  --body "$(jq -r .ecr_push_role_arn /tmp/tf-outputs.json)"
gh variable set ENABLE_EC2_DEPLOY_PROD  --body false   # freeze switch: stays false until section 7

# Environment-level variables (backend-prod from section 2)
gh variable set AWS_DEPLOY_ROLE_ARN_PROD --env backend-prod \
  --body "$(jq -r .deploy_role_arn /tmp/tf-outputs.json)"
gh variable set BACKEND_EC2_INSTANCE_ID_PROD --env backend-prod \
  --body "$(jq -r .instance_id /tmp/tf-outputs.json)"
# BACKEND_URL_PROD is set in section 6.5, after the CloudFront distribution exists —
# it must be the CloudFront domain (what users hit), never the raw ALB URL.
```

`AWS_ACCOUNT_ID` and `AWS_REGION` already exist from the dev setup — verify, do not re-create.
No AWS access keys and no `BACKEND_DOT_ENV` belong on GitHub.

- [ ] All five variables listed by `gh variable list` (and `--env backend-prod`) with values
      exactly matching the Terraform outputs
- [ ] `ENABLE_EC2_DEPLOY_PROD=false`

### 6.5 One-time: the prod CloudFront distribution

The entry layer mirrors the dev rework: follow [the current dev entry-layer requirements](../dev-cloudfront-deployment.md) sections 6–7 with prod values. Those sections describe the live resource pattern; the original one-time CLI creation examples are retained in [the dev guide at 12df8a8](https://github.com/PPsyrius/iss-groupbuy-team8/blob/12df8a83c86012d51b9de4532741bc0bf80dcad7/docs/deploy/dev-cloudfront-deployment.md#6-frontend-s3-bucket--oac) for historical reference, not as commands to replay against dev.
The Terraform stack has already built the ALB half (prefix-list SG, default-403 listener,
secret-header rule), so only the CloudFront side is manual:

- **Origin `alb-backend`:** DomainName = `terraform output backend_url` (the ALB DNS),
  `OriginProtocolPolicy: http-only`, custom header `X-Origin-Secret` = the value of
  `/blocbuy/prod/ORIGIN_SECRET` (retrieve once on the admin workstation — never into a file
  inside the repository)
- **Cache behavior `/api/*` → `alb-backend`:** managed `CachingDisabled` + `AllViewerExceptHostHeader`
  policies, `redirect-to-https` (the built-in `*.cloudfront.net` certificate — no ACM, no domain)
- **Frontend (optional):** a separate prod S3 bucket + OAC follows dev sections 6–7 verbatim with
  `blocbuy-prod-frontend-<account>` naming; skip it if the frontend is not served from this
  distribution yet
- **Distribution Comment:** `BlocBuy prod - frontend S3 + backend ALB (/api)` — the lookup below
  matches on it exactly, as the dev tutorial does
- **Do not** create a second ALB, target group, or listener — those are Terraform-owned

Then set the last environment variable to the domain users actually hit:

```bash
DIST_DOMAIN="$(aws cloudfront list-distributions \
  --query "DistributionList.Items[?Comment=='BlocBuy prod - frontend S3 + backend ALB (/api)'].DomainName" \
  --output text)"
gh variable set BACKEND_URL_PROD --env backend-prod --body "https://$DIST_DOMAIN/api"
```

- [ ] `curl --fail --silent "https://$DIST_DOMAIN/api/actuator/health"` returns `{"status":"UP"}`
      (after the first deploy in section 7; 503/404 before it is expected)
- [ ] `BACKEND_URL_PROD` lists as `https://<cloudfront-domain>/api` under `--env backend-prod`

## 7. First production deployment drill

Ordered; the freeze switch opens only at step 3:

1. - [ ] **Rehearse closed:** push a throwaway tag `v0.0.0-rc1` → confirm the full workflow runs
      the quality gates but publishes nothing and does not deploy (freeze switch working)
2. - [ ] Verify `security` workflow green on that tag
3. - [ ] `gh variable set ENABLE_EC2_DEPLOY_PROD --body true`
4. - [ ] Cut the real release from the release branch:

       ```bash
       git checkout <release_branch> && git pull
       git tag v0.1.0 && git push origin v0.1.0
       ```

5. - [ ] Gates re-run on the tag; the `publish-ecr-prod` job publishes to `prod/blocbuy`
       **by digest** (check the job summary: image `repository@sha256:...`)
6. - [ ] The operator has authorized this release tag and enabled the deploy switch; `backend-prod` admits the tag but does **not** pause for reviewer approval
7. - [ ] Inspect the `deploy-existing-ec2-prod` job: bundle installed, `fetch-env.sh` rendered the
       env file, SSM command `Success`, **local health check passed inside the SSM stdout**
       (deploy.sh auto-rolls-back on failure — a Success status means the app was healthy)
8. - [ ] End-to-end through the CloudFront domain (the same URL users hit):

       ```bash
       curl --fail --silent "https://<prod-dist-domain>/api/actuator/health"
       # Direct-to-ALB cannot reach backend health: SG timeout/000 or listener 403.
       curl --max-time 5 -sS -o /dev/null -w '%{http_code}\n' \
         "$(terraform -chdir=infra/terraform/backend-app-stack output -raw backend_url)/api/actuator/health"
       ```

9. - [ ] Target group healthy:

       ```bash
       aws elbv2 describe-target-health --target-group-arn <arn> --region ap-southeast-1
       ```

10. - [ ] Change log entry: first prod deployment, tag, image digest and operator decision

## 8. Steady-state operations

**Routine release:** verify both `v*` environment policies and prerequisites → deliberately enable the deploy switch → cut `vX.Y.Z` tag → inspect the digest, SSM result and health. No native reviewer pause protects this path.

**Manual rollback (application image only):** dispatch `rollback-backend` with `target=prod` from an existing **`v*` release tag**, not `dev` or a release branch. Leave `target_image` empty for the host's recorded previous digest or supply `repository@sha256:...`; add a non-empty reason and select `confirm_rollback=true`. `backend-prod` admits tags only, without reviewer approval. Merge the tag-ref guard before the **first** release tag so that tag contains the corrected workflow. Automatic rollback within a failed deploy is unchanged; neither path reverts Flyway migrations.

```bash
gh workflow run rollback-backend.yml --ref vX.Y.Z --repo PPsyrius/iss-groupbuy-team8 \
  -f target=prod -f reason='reviewed incident reason' -F confirm_rollback=true
```

**Freeze:** `gh variable set ENABLE_EC2_DEPLOY_PROD --body false`. CI and scanning keep running
on tags; nothing publishes or deploys; manual rollback stays available.

**Host/infra changes:** edit the stack → PR → `terraform plan` review (section 4 checklist
applies) → `terraform apply`. Never hand-edit security groups, IAM, or the ALB — a drifted
resource is Terraform's to reconcile on the next apply.

**Secret rotation:** `aws ssm put-parameter --name /blocbuy/prod/<KEY> --type SecureString
--value <new>` then re-run any deploy (or rollback) — `fetch-env.sh` re-renders the env file
before every deploy. For DB_PASSWORD, coordinate with the database user change; for Stripe
secrets, verify webhook delivery after rotation.

## 9. Quick troubleshooting

| Symptom | Most likely cause |
|---|---|
| ECR push role cannot be assumed on a tag | `backend-prod-publish` environment missing or not restricted to `v*` tags (the role trusts the `environment:backend-prod-publish` subject, not a branch/tag ref); or `ENABLE_EC2_DEPLOY_PROD` still false |
| Deploy role cannot be assumed | `backend-prod` environment not restricted to `v*` tags, or environment variable typo'd |
| CloudFront `/api/*` returns 502/503 | Origin secret mismatch: the distribution's `X-Origin-Secret` header ≠ `/blocbuy/prod/ORIGIN_SECRET` (a rotation done on one side only), or the target group has no healthy target |
| Direct request to the ALB DNS name times out/returns 000 or 403 | Expected SG block or default-deny listener; use CloudFront for public health checks |
| SSM command never leaves Pending | Instance not Online (section 5), region mismatch between `AWS_REGION` and the stack |
| `fetch-env.sh` fails: parameter missing | Section 3 incomplete — key list must match `backend.env.example` exactly |
| User-data failed at first boot | Parameters seeded after apply (ordering violation — section 3), or instance-profile propagation; check console output / cloud-init logs |
| ALB target unhealthy | Health check path not `/api/actuator/health`, or container down — inspect via SSM, `docker logs blocbuy-backend` |
| PostgreSQL/Redis unreachable from backend | Infra project not running (`docker compose -ps` under `/opt/blocbuy/infra`), or parameters empty at first boot |
| Rollback cannot find previous image | Host state file `/opt/blocbuy/backend/state/current-image` absent (first deploy never completed) |

**Red lines (unchanged from dev):** never paste `/etc/blocbuy/backend.env`, Parameter Store
values, Docker credentials, or application secrets into issues, PRs, workflow logs, or any file
in this repository; never relax an OIDC trust `sub` to a wildcard; never open 5432/6379/22 to
any CIDR; never attach broad policies (`AmazonSSMFullAccess`,
`AmazonEC2ContainerRegistryFullAccess`) to any role in this design.
