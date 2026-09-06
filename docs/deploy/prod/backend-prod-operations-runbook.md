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
| TLS | ACM cert ARN, or interim HTTP `:80` listener | Stripe live mode requires TLS before real users |
| `backend-prod` reviewers | at least **two** team members | prod approvals are never single-person |

Secret values to prepare (all become Parameter Store SecureStrings in section 3): one PostgreSQL
user/password (new — prod database is separate from dev), one Redis password, the Stripe API key
and webhook secret, and the application `DB_URL`/credentials/Redis host-port exactly as
[`backend.env.example`](../../../deployment/backend/backend.env.example) defines them.

## 1. One-time: Terraform state backend

Run once per AWS account (skip if the bucket/table already exist):

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

## 2. One-time: GitHub `backend-prod` environment

Settings → Environments → New environment → `backend-prod`:

- [ ] **Required reviewers:** at least two team members
- [ ] **Deployment branches and tags:** selected → **tags matching `v*` only** — this is what
      makes the prod approval gate unreachable from branch pushes
- [ ] No environment secrets and no environment variables yet (section 6 fills the variables)

## 3. Seed secrets into SSM Parameter Store

**Ordering note (differs from design doc §9): this must happen *before* `terraform apply`.** The
instance's user-data starts PostgreSQL/Redis at first boot and reads these parameters
immediately; applying before seeding produces a host whose infra Compose project failed at boot.

All parameters are `SecureString`, path-prefixed `/blocbuy/prod/`:

| Parameter | Consumed by |
|---|---|
| `/blocbuy/prod/DB_URL` | backend env (fetch-env.sh) |
| `/blocbuy/prod/DB_USERNAME` | backend env (fetch-env.sh) |
| `/blocbuy/prod/DB_PASSWORD` | backend env (fetch-env.sh) |
| `/blocbuy/prod/REDIS_HOST` | backend env (fetch-env.sh) |
| `/blocbuy/prod/REDIS_PORT` | backend env (fetch-env.sh) |
| `/blocbuy/prod/REDIS_PASSWORD` | backend env (fetch-env.sh) **and** infra compose (first boot) |
| `/blocbuy/prod/STRIPE_API_KEY` | backend env (fetch-env.sh) |
| `/blocbuy/prod/STRIPE_WEBHOOK_SECRET` | backend env (fetch-env.sh) |
| `/blocbuy/prod/POSTGRES_USER` | infra compose (first boot) |
| `/blocbuy/prod/POSTGRES_PASSWORD` | infra compose (first boot) |

```bash
aws ssm put-parameter --name /blocbuy/prod/DB_PASSWORD \
  --type SecureString --value '<real value>' --region ap-southeast-1
# ... repeat for every row above
```

- [ ] All ten parameters exist (`aws ssm describe-parameters --parameter-filters ...`)
- [ ] No value was echoed to a terminal log, issue, or file inside the repository
- [ ] `REDIS_HOST`=`127.0.0.1`, `REDIS_PORT`=`6379`, `DB_URL` points at `127.0.0.1:5432/Blocbuy`
      (host-local infra project), schema suffix identical to dev

## 4. Plan, review, apply the stack

```bash
cd infra/terraform
terraform init \
  -backend-config bucket=blocbuy-tfstate-402178232802 \
  -backend-config region=ap-southeast-1
terraform plan -var-file=envs/prod.tfvars -out=prod.tfplan
```

**Team plan review — read all of these in the plan output before applying** (this replaces the
dev bootstrap's "replace the placeholder then read it back" ritual):

- [ ] Both GitHub trust policies: `sub` equals
      `repo:PPsyrius@19505219/iss-groupbuy-team8@1344446887:ref:refs/heads/<release_branch>`
      (ECR push) and `...:environment:backend-prod` (deploy) — exact match, no wildcards
- [ ] Backend security group: the **only** ingress for 8589 references the ALB security group ID;
      no ingress for 22, 5432, or 6379 anywhere
- [ ] EC2 role inline policies: ECR pull resource is the `prod/blocbuy` repository ARN;
      `ssm:GetParameters` resource is `parameter/blocbuy/prod/*` in this account/region
- [ ] ALB target group health check path is `/api/actuator/health` (context path — see
      implementation plan §0.1)
- [ ] Instance: IMDSv2 required, encrypted root volume, pinned AMI, `ManagedBy=terraform` tag
- [ ] No resource outside this stack is being modified or destroyed (plan shows only `+`)

```bash
terraform apply prod.tfplan
```

Apply creates: two GitHub OIDC roles, `prod/blocbuy` ECR, security groups, ALB + target group,
and the EC2 instance. User-data runs at first boot (Docker, AWS CLI, the `blocbuy-infra`
PostgreSQL/Redis project, loopback-only).

- [ ] `terraform apply` returned successfully; no errors in the user-data self-verification
- [ ] Change log entry (link the PR + the apply)

## 5. Post-apply verification

```bash
INSTANCE_ID="$(terraform -chdir=infra/terraform output -raw instance_id)"

# SSM agent online (first boot installs prerequisites; allow a few minutes)
aws ssm describe-instance-information \
  --filters "Key=InstanceIds,Values=$INSTANCE_ID" --region ap-southeast-1 \
  --query 'InstanceInformationList[].{id:InstanceId,ping:PingStatus}'
# PingStatus must be Online

# Infra listeners: loopback only, no public bind
aws ssm send-command --instance-ids "$INSTANCE_ID" \
  --document-name AWS-RunShellScript --region ap-southeast-1 \
  --parameters 'commands=["ss -lnt | grep -E \"127\.0\.0\.1:(5432|6379)\" && ss -lnt | grep -vE \"127\.0\.0\.1:(5432|6379)|::1:(5432|6379)\" | grep -E \":(5432|6379)\" || echo LOOPBACK-ONLY-OK"]'
# stdout must contain LOOPBACK-ONLY-OK

# Security groups: 8589 only from the ALB SG; nothing else ingress
aws ec2 describe-security-groups --region ap-southeast-1 \
  --group-names "$(terraform -chdir=infra/terraform output -raw backend_sg_name)" \
  --query 'SecurityGroups[].IpPermissions[]'

# ALB target health (after the first deploy there will be a target; before it, expect no targets)
aws elbv2 describe-target-health --region ap-southeast-1 \
  --target-group-arn "$(terraform -chdir=infra/terraform output -raw target_group_arn)"
```

- [ ] `PingStatus=Online`
- [ ] PostgreSQL/Redis listening on `127.0.0.1` only
- [ ] No unexpected ingress rules
- [ ] `terraform output` prints `instance_id`, `ecr_push_role_arn`, `deploy_role_arn`,
      `backend_url` — record all four for section 6

If SSM stays `ConnectionLost`: the agent needs a few minutes after instance-profile attachment;
then check VPC endpoints/NAT (see dev operator guide section 13).

## 6. Configure GitHub (scripted, values derived — never retyped)

Requires the `gh` CLI with admin on `PPsyrius/iss-groupbuy-team8`:

```bash
terraform -chdir=infra/terraform output -json > /tmp/tf-outputs.json

# Repository-level variables (the docker job's prod publish step is not environment-scoped)
gh variable set ECR_REPOSITORY_PROD     --body prod/blocbuy
gh variable set AWS_ECR_PUSH_ROLE_ARN_PROD \
  --body "$(jq -r .ecr_push_role_arn /tmp/tf-outputs.json)"
gh variable set ENABLE_EC2_DEPLOY_PROD  --body false   # freeze switch: stays false until section 7

# Environment-level variables (backend-prod from section 2)
gh variable set AWS_DEPLOY_ROLE_ARN_PROD --env backend-prod \
  --body "$(jq -r .deploy_role_arn /tmp/tf-outputs.json)"
gh variable set BACKEND_EC2_INSTANCE_ID_PROD --env backend-prod \
  --body "$(jq -r .instance_id /tmp/tf-outputs.json)"
gh variable set BACKEND_URL_PROD --env backend-prod \
  --body "$(jq -r .backend_url /tmp/tf-outputs.json)/api"
```

`AWS_ACCOUNT_ID` and `AWS_REGION` already exist from the dev setup — verify, do not re-create.
No AWS access keys and no `BACKEND_DOT_ENV` belong on GitHub.

- [ ] All six variables listed by `gh variable list` (and `--env backend-prod`) with values
      exactly matching the Terraform outputs
- [ ] `ENABLE_EC2_DEPLOY_PROD=false`

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

5. - [ ] Gates re-run on the tag; `docker` job publishes to `prod/blocbuy` **by digest**
       (check the job summary: image `repository@sha256:...`)
6. - [ ] The `backend-prod` environment pauses — an assigned reviewer approves
7. - [ ] Inspect the `deploy-existing-ec2-prod` job: bundle installed, `fetch-env.sh` rendered the
       env file, SSM command `Success`, **local health check passed inside the SSM stdout**
       (deploy.sh auto-rolls-back on failure — a Success status means the app was healthy)
8. - [ ] End-to-end through the ALB:

       ```bash
       curl --fail --silent \
         "$(terraform -chdir=infra/terraform output -raw backend_url)/api/actuator/health"
       ```

9. - [ ] Target group healthy:

       ```bash
       aws elbv2 describe-target-health --target-group-arn <arn> --region ap-southeast-1
       ```

10. - [ ] Change log entry: first prod deployment, tag, image digest, approver

## 8. Steady-state operations

**Routine release:** merge to the release branch → cut `vX.Y.Z` tag → approve the
`backend-prod` environment. Nothing else.

**Rollback (application image only):** Actions → `rollback-backend` → Run workflow →
`target=prod`, branch = release branch or `dev` → `target_image` empty (host's recorded previous
digest) or an explicit `repository@sha256:...` → fill reason → tick the confirmation → a
`backend-prod` reviewer approves. Database/Flyway state is never reverted — migrations must stay
backward-compatible.

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
| ECR push role cannot be assumed on a tag | `release_branch` in tfvars ≠ the branch the tag was cut from; or `ENABLE_EC2_DEPLOY_PROD` still false |
| Deploy role cannot be assumed | `backend-prod` environment not restricted to `v*` tags, or environment variable typo'd |
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
