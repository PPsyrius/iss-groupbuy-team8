# Production Backend Deployment Guide (Terraform IaC)

**Status:** implemented — stack code merged under `infra/terraform/`, reviewed via the implementation plan; awaiting the runbook's one-time apply and first release.

**Environment:** `backend-prod` (a second, dedicated EC2 instance)

**Provisioning model:** Terraform-defined production infrastructure — IAM, ECR, EC2, security groups, load balancer and host bootstrap are declared as code; production `terraform apply` remains an account-holder runbook step (#48), not a completed deployment claim.

**Application delivery:** unchanged from dev — GitHub Actions -> Amazon ECR -> AWS Systems Manager Run Command, extended with a release tag trigger and a mandatory approval gate

This guide specifies the production counterpart of the existing [dev deployment](../backend-ec2-pipeline-operator-guide.md). The application-delivery design (quality gates, immutable digests, OIDC, SSM Run Command, non-secret bundle, automatic rollback) is reused as-is; what changes is **who creates the environment**: the dev environment was bootstrapped with hand-run AWS CLI commands, the production environment is created by Terraform from this repository.

## 1. Why IaC for production — lessons from the dev bootstrap

The dev deployment went live on 2026-09-05. Every incident during that first deployment traces to the same root cause: **the environment was assembled by hand, so reality drifted from the documented design**. Terraform removes that class of failure by construction:

| Dev incident | Root cause | Production control (this guide) |
|---|---|---|
| OIDC role could not be assumed | Trust policy `sub` used the pre-2026-07 name-only format; the repo (created 2026-08-24) uses immutable subjects | Trust policy rendered by `data "aws_iam_policy_document"` from tfvars; `terraform plan` shows the exact `sub` before anything is created |
| `ssm:SendCommand` AccessDenied | Live policy still contained the placeholder instance ID `i-0123456789abcdef0` — sed replacement was never applied | The instance ID is `aws_instance.backend.id`, a resource reference. A literal placeholder cannot exist in the rendered policy |
| Instance missing `aws` CLI | Host prerequisites were a checklist item, not a machine state | `user_data` installs and verifies the full prerequisite set on first boot; preflight is exercised by the pipeline on every deploy |
| Port 8589 open to `0.0.0.0/0` | Health check needed a path in; GitHub runner IPs are dynamic, so the group was opened wide | The only ingress reference for 8589 is the ALB security group ID; the workflow health check runs through SSM on the instance itself |
| Variable naming mismatch, stray values | GitHub variables were edited by hand across sessions | All variable values are derived from `terraform output` and set with `gh variable set` in one scripted step |

The general principle: **everything that was a "replace the placeholder, then verify by reading it back" instruction in the dev bootstrap becomes a code review of a rendered diff**.

## 2. Architecture

```text
Release tag v* pushed (or workflow_dispatch)
  ci-backend quality gates re-run on the tag
    -> GitHub OIDC assumes BlocBuyGitHubEcrPushProd
    -> image pushed to prod/blocbuy ECR, resolved to sha256 digest
    -> protected backend-prod environment (required reviewers)
    -> GitHub OIDC assumes BlocBuyGitHubBackendDeployProd
    -> SSM installs the non-secret bundle under /opt/blocbuy/backend
    -> deploy.sh pulls the digest and starts only blocbuy-backend
    -> local health check (127.0.0.1:8589) inside deploy.sh; optional public check via CloudFront

                    ┌─ CloudFront (built-in *.cloudfront.net TLS) ─┐
  users ─ HTTPS ───►│  /api/* → ALB origin (HTTP, X-Origin-Secret) │
                    └───────────────┬──────────────────────────────┘
                                    │ SG: CloudFront prefix list only
                    ┌───────────────▼────────────────┐
                    │ ALB (Terraform-created origin) │
                    │ default 403; secret header     │
                    │ rule → target :8589            │
                    └───────────────┬────────────────┘
                                    │ SG: alb-sg → backend-sg (8589 only)
                    ┌───────────────▼────────────────┐
                    │ prod EC2 (Terraform-created)   │
                    │  blocbuy-backend  host :8589   │◄─ SSM Run Command (deploy/rollback)
                    │  PostgreSQL       127.0.0.1:5432│◄─ provisioned by user-data, not pipeline
                    │  Redis            127.0.0.1:6379│◄─ provisioned by user-data, not pipeline
                    │  secrets   /etc/blocbuy/backend.env (root:root 0600)
                    └────────────────────────────────┘
```

The entry layer is the same pattern the dev environment adopted ([dev-cloudfront-deployment.md](../dev-cloudfront-deployment.md)): user-facing TLS terminates on CloudFront's built-in certificate (no custom domain, no ACM certificate to manage), and the ALB is a **private origin** — its security group accepts the CloudFront managed prefix list only, and its listener defaults to 403 unless the request carries the shared `X-Origin-Secret` header. Anyone who resolves the ALB DNS name and connects directly gets nothing.

Boundaries carried over from dev and unchanged:

| Pipeline manages | Pipeline does not manage |
|---|---|
| Backend image build, scan, ECR publication, digest | EC2 lifecycle, AMI, EBS, VPC, security groups, ALB — **these become Terraform-managed** |
| Backend Compose file and lifecycle scripts | PostgreSQL/Redis containers (Terraform/user-data-managed on the host) |
| Backend container replacement and health check | Database backup, restore, or Flyway reversal |
| Application rollback (automatic + approved manual) | DNS and TLS certificates (ACM), and the CloudFront distribution (runbook section 6.5) |

Two boundaries are deliberately kept even though Terraform *could* cross them:

1. **The deployment pipeline still owns only the backend container.** PostgreSQL and Redis on the prod host are started by the Terraform user-data as a separate Compose project (`blocbuy-infra`), so a pipeline deploy can never touch them.
2. **Terraform creates the environment; the pipeline changes only the running image.** A prod deploy never runs `terraform apply`, and a Terraform run never deploys an application image.

## 3. Repository layout

```text
infra/terraform/
├── backend-app-stack/            # one stack, applied per environment
│   ├── main.tf                   # wires the modules below
│   ├── variables.tf
│   ├── outputs.tf                # everything the GitHub setup script needs
│   ├── github-oidc.tf            # OIDC provider lookup + two prod roles
│   ├── ecr.tf
│   ├── network.tf                # VPC data sources, security groups, ALB + target group
│   ├── ec2.tf                    # instance, instance profile, ECR-pull inline policy
│   └── templates/
│       └── user-data.sh.tftpl    # host bootstrap (prerequisites + infra Compose project)
├── envs/
│   ├── dev.tfvars                # future import of the existing dev resources (section 10)
│   └── prod.tfvars               # the production environment this guide creates
└── README.md
```

A single stack (rather than nested modules) keeps the first Terraform adoption reviewable in one PR. Split into modules only when a second consumer appears.

State lives in S3 with DynamoDB locking, bootstrapped once per account (section 5.1).

## 4. Values to collect

| Variable | Example / actual | Source |
|---|---|---|
| `aws_account_id` | `402178232802` | existing account |
| `aws_region` | `ap-southeast-1` | existing account |
| `github_owner` / `github_owner_id` | `PPsyrius` / `19505219` | GitHub API; already verified for this repo |
| `github_repo` / `github_repo_id` | `iss-groupbuy-team8` / `1344446887` | GitHub API; already verified for this repo |
| `ecr_repository` | `prod/blocbuy` | new, created by this stack |
| `release_branch` | `main` (or the branch that tags are cut from) | team decision |
| `instance_type` | `t3.medium` (prod floor; dev runs smaller) | team decision |
| `key_name` | EC2 key pair for break-glass SSH, or omitted if SSM-only | team decision |
| ACM certificate ARN | empty — CloudFront entry pattern (see section 6.4); set only to upgrade the origin leg to HTTPS | team decision |
| Origin secret | `openssl rand -hex 32`, seeded as `/blocbuy/prod/ORIGIN_SECRET` before the first plan | new, shared CloudFront↔ALB |

The OIDC subject values are not guessed: they were decoded from a live workflow token on 2026-09-05. Repositories created after 15 July 2026 (like this one) use the immutable format `repo:<owner>@<ownerID>/<repo>@<repoID>:<suffix>`.

## 5. One-time setup

### 5.1 Bootstrap the Terraform state backend

Run once per AWS account from an administrator workstation (outside Terraform, like the OIDC provider in the dev bootstrap). On 2026-09-25 the bucket and active `LockID (S)` DynamoDB table were confirmed for #161; verify versioning, encryption and access rather than replaying create commands against existing resources.

```bash
aws s3api create-bucket --bucket blocbuy-tfstate-402178232802 \
  --region ap-southeast-1 --create-bucket-configuration LocationConstraint=ap-southeast-1
aws s3api put-bucket-versioning --bucket blocbuy-tfstate-402178232802 \
  --versioning-configuration Status=Enabled
aws s3api put-bucket-encryption --bucket blocbuy-tfstate-402178232802 \
  --server-side-encryption-configuration '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"aws:kms"}}]}'
aws s3api put-public-access-block --bucket blocbuy-tfstate-402178232802 \
  --public-access-block-configuration BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true

aws dynamodb create-table --table-name blocbuy-tflock \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST --region ap-southeast-1
```

### 5.2 OIDC provider

The account already has the GitHub OIDC provider (created during the dev bootstrap). The stack looks it up instead of creating a second one:

```hcl
data "aws_iam_openid_connect_provider" "github" {
  url = "https://token.actions.githubusercontent.com"
}
```

One provider per account, shared by dev and prod roles — the isolation between environments comes from the `sub` conditions, not from duplicate providers.

## 6. The stack, component by component

### 6.1 GitHub OIDC roles (placeholder-free by construction)

The ECR push role is assumed by the `publish-ecr-prod` workflow job, which publishes on `v*` tag pushes. Tag names vary per release, so a `ref:refs/tags/...` subject can never be exact-matched — the job carries the stable `environment:backend-prod-publish` subject instead (the GitHub environment is restricted to `v*` tags; see section 8).

```hcl
locals {
  github_repo_slug = "repo:${var.github_owner}@${var.github_owner_id}/${var.github_repo}@${var.github_repo_id}"
}

data "aws_iam_policy_document" "github_ecr_push_trust" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [data.aws_iam_openid_connect_provider.github.arn]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["${local.github_repo_slug}:environment:backend-prod-publish"]
    }
  }
}

data "aws_iam_policy_document" "github_deploy_trust" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [data.aws_iam_openid_connect_provider.github.arn]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["${local.github_repo_slug}:environment:backend-prod"]
    }
  }
}
```

Both roles, their permission policies, and their attachments are declared in the same file. There is no sed step, no `.json.example`, and no post-create read-back ritual — `terraform plan` prints the exact trust documents before anything is created. Wildcards remain forbidden; if the plan shows a `sub` that does not match the formats above, the tfvars are wrong, not the policy.

Role and policy names carry the `Prod` suffix (`BlocBuyGitHubEcrPushProd`, `BlocBuyGitHubBackendDeployProd`) so dev and prod roles are never confused in logs or reviews.

### 6.2 ECR repository

```hcl
resource "aws_ecr_repository" "backend" {
  name                 = var.ecr_repository # prod/blocbuy — separate from dev/blocbuy
  image_tag_mutability = "IMMUTABLE"
  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "backend" {
  repository = aws_ecr_repository.backend.name
  policy     = file("${path.module}/../../deployment/backend/ecr-lifecycle-policy.json")
}
```

A separate repository keeps the dev ECR-push role unable to write prod images and gives prod its own retention budget. The lifecycle policy file is the one already committed for dev — one source of truth.

### 6.3 Networking: security groups that reference, not enumerate

```hcl
# The ALB is a CloudFront origin, not a public entry point.
data "aws_prefix_list" "cloudfront_origin" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

resource "aws_security_group" "alb" {
  name_prefix = "blocbuy-prod-alb-"
  vpc_id      = data.aws_vpc.default.id
  ingress {
    description     = "Origin traffic from CloudFront only"
    from_port       = 80
    to_port         = 80
    protocol        = "tcp"
    prefix_list_ids = [data.aws_prefix_list.cloudfront_origin.id] # <- managed list, auto-updated
  }
  egress {
    from_port = 0
    to_port   = 0
    protocol  = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "backend" {
  name_prefix = "blocbuy-prod-backend-"
  vpc_id      = data.aws_vpc.default.id
  ingress {
    description     = "Backend port from the ALB only"
    from_port       = 8589
    to_port         = 8589
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id] # <- reference, never a CIDR list
  }
  egress {
    from_port = 0
    to_port   = 0
    protocol  = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_lb_target_group" "backend" {
  name     = "blocbuy-prod-backend"
  port     = 8589
  protocol = "HTTP"
  vpc_id   = data.aws_vpc.default.id
  health_check {
    path                = "/api/actuator/health" # container serves under the /api context path
    matcher             = "200"
    interval            = 15
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

resource "aws_lb" "backend" {
  name               = "blocbuy-prod"
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = data.aws_subnets.public.ids
}

resource "aws_lb_target_group_attachment" "backend" {
  target_group_arn = aws_lb_target_group.backend.arn
  target_id        = aws_instance.backend.id
  port             = 8589
}

# Shared origin secret, seeded as a SecureString before the first plan (section 6.7).
data "aws_ssm_parameter" "origin_secret" {
  name = "/blocbuy/prod/ORIGIN_SECRET"
}

# Default-deny: direct connections to the ALB DNS name get 403.
resource "aws_lb_listener" "backend" {
  load_balancer_arn = aws_lb.backend.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type           = "fixed-response"
    fixed_response {
      content_type = "text/plain"
      message_body = "forbidden"
      status_code  = "403"
    }
  }
}

# Only CloudFront's requests (carrying the secret header) reach the backend.
resource "aws_lb_listener_rule" "origin_secret" {
  listener_arn = aws_lb_listener.backend.arn
  priority     = 10

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.backend.arn
  }

  condition {
    http_header {
      http_header_name = "X-Origin-Secret"
      values           = [data.aws_ssm_parameter.origin_secret.value]
    }
  }
}
```

The dev incident of opening 8589 to the world cannot recur: the only ingress rule for 8589 names the ALB security group, and changing that requires a reviewed Terraform PR. The ALB itself accepts the CloudFront managed prefix list only — a second layer the dev incident never had. SSH (22), PostgreSQL (5432), and Redis (6379) have **no** ingress rules at all — the host is reachable only through CloudFront → ALB and SSM.

One accepted trade-off: the origin secret is read through `data "aws_ssm_parameter"` and therefore lands in Terraform state. The state bucket is KMS-encrypted with public access fully blocked and access requires explicit IAM permission — this is the same trade-off Terraform makes for any secret-bearing resource, and it is why the entry layer is in code rather than hand-run CLI. The application secrets (`DB_PASSWORD`, Stripe keys, …) never take this path — they are fetched from Parameter Store on the host at deploy time and never rendered by Terraform.

### 6.4 TLS (CloudFront entry pattern)

The dev environment's entry-layer rework ([dev-cloudfront-deployment.md](../dev-cloudfront-deployment.md)) is adopted for prod unchanged in shape:

- **User-facing TLS:** CloudFront's built-in `*.cloudfront.net` certificate — no custom domain, no ACM certificate to request or rotate, `ViewerProtocolPolicy: redirect-to-https`.
- **Origin leg:** plain HTTP `:80`, protected by the prefix-list security group + the `X-Origin-Secret` header + the default-403 listener. `OriginProtocolPolicy: http-only`.
- **Origin secret:** `/blocbuy/prod/ORIGIN_SECRET` (SecureString) — generated once (`openssl rand -hex 32`), consumed by this stack's listener rule and the CloudFront distribution's custom origin header. Rotating it means a new parameter value, a Terraform apply (listener rule), and a CloudFront distribution update, done together.
- **Upgrade path (later, optional):** once the team has a domain, follow dev-cloudfront-deployment.md §12 — ACM certificate in `us-east-1` attached to CloudFront, and optionally an ACM certificate on the ALB (`acm_certificate_arn` in tfvars) to upgrade the origin leg to HTTPS. No architectural rework, only a protocol change.

### 6.5 EC2 instance, instance profile, and ECR pull inline policy

```hcl
resource "aws_iam_role" "ec2_runtime" {
  name = "BlocBuyEc2RuntimeProd"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
  managed_policy_arns = ["arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"]
  inline_policy { # ECR pull scoped to exactly the prod repository — resource reference, no placeholder
    name = "ecr-pull-prod-repository"
    policy = jsonencode({
      Version = "2012-10-17"
      Statement = [{
        Effect   = "Allow"
        Action   = ["ecr:GetAuthorizationToken", "ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer"]
        Resource = aws_ecr_repository.backend.arn
      }]
    })
  }
}

resource "aws_iam_instance_profile" "ec2_runtime" {
  name = "BlocBuy-prod"
  role = aws_iam_role.ec2_runtime.name
}

resource "aws_instance" "backend" {
  ami                    = data.aws_ami.ubuntu_latest.id # pin to a specific AMI in prod.tfvars
  instance_type          = var.instance_type
  iam_instance_profile   = aws_iam_instance_profile.ec2_runtime.name
  vpc_security_group_ids = [aws_security_group.backend.id]
  user_data = templatefile("${path.module}/templates/user-data.sh.tftpl", {
    ecr_registry = "${var.aws_account_id}.dkr.ecr.${var.aws_region}.amazonaws.com"
  })
  metadata_options {
    http_tokens = "required" # IMDSv2 only
  }
  root_block_device {
    volume_size = 30
    encrypted   = true
  }
  tags = { Name = "blocbuy-prod-backend", ManagedBy = "terraform" }
}
```

### 6.6 Host bootstrap: user-data replaces the prerequisite checklist

`templates/user-data.sh.tftpl` makes the "host has Docker, Compose, AWS CLI v2" property part of the machine's definition. The dev bootstrap hit a dead deployment because this was a checklist line a human missed; in prod it is code that runs at first boot. Note its limit: `terraform apply` only delivers the script — a user-data failure does **not** fail the apply. First-release verification (runbook §5) confirms via SSM that cloud-init finished and the bootstrap completion line is in the log before continuing.

```bash
#!/usr/bin/env bash
set -Eeuo pipefail

# ---- prerequisites (the dev preflight list, made machine-enforced) ----
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y unzip curl ca-certificates gnupg docker.io docker-compose-v2

cd /tmp
curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o awscliv2.zip
unzip -q awscliv2.zip && ./aws/install

systemctl enable --now docker

# ---- host-local PostgreSQL + Redis (blocbuy-infra project; pipeline never touches it) ----
install -d -m 0750 /opt/blocbuy/infra /opt/blocbuy/backend/bin /opt/blocbuy/backend/state /etc/blocbuy
cat > /opt/blocbuy/infra/compose.yml <<'EOF'
name: blocbuy-infra
services:
  postgres:
    image: postgres:18
    restart: unless-stopped
    network_mode: host   # binds 127.0.0.1:5432 only (postgis not needed; matches dev)
    environment:
      POSTGRES_DB: Blocbuy
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
  redis:
    image: redis:8.10
    restart: unless-stopped
    network_mode: host
    command: redis-server --requirepass ${REDIS_PASSWORD}
EOF
chmod 0640 /opt/blocbuy/infra/compose.yml
# Secrets are NOT templated here: see section 6.7 — they arrive via SSM Parameter Store.
```

Two separate secret paths, neither through Terraform-rendered values (the template carries no secret literals): at first boot the instance pulls `POSTGRES_USER`/`POSTGRES_PASSWORD`/`REDIS_PASSWORD` from SSM into `/etc/blocbuy/infra.env` (0600) for the local data services; at deploy time `bin/fetch-env.sh` pulls the backend parameters into `/etc/blocbuy/backend.env` (0600). User-data (readable on the instance) creates neither backend file. Sole Terraform-state exception: `/blocbuy/prod/ORIGIN_SECRET`, read at plan time into the ALB listener rule (§6.3).

### 6.7 Secrets: SSM Parameter Store (upgrade from the dev hand-edited file)

Dev keeps secrets in a hand-edited `/etc/blocbuy/backend.env`; every prod change to it would be an untracked, unreviewed root shell command. Production moves the file's contents to SSM Parameter Store (SecureString) and lets the host render it:

- `aws ssm put-parameter --name /blocbuy/prod/DB_USERNAME --type SecureString ...` for each key in [`backend.env.example`](../../../deployment/backend/backend.env.example) — one-time, from an admin workstation; values never enter the repository or Terraform state. Seeding happens **before the first plan**: the user-data reads the first-boot keys at instance creation, and the stack reads `ORIGIN_SECRET` at plan time;
- the instance role gains `ssm:GetParameters` scoped to path `/blocbuy/prod/*`;
- a small `bin/fetch-env.sh` (added to the deployment bundle alongside the existing scripts, subject to the same bundle allowlist review) renders `/etc/blocbuy/backend.env` with `root:root 0600` before each deploy and preflight run.

`deploy.sh`, `preflight.sh`, `rollback.sh`, and `compose.yml` are reused unchanged. The prod stack only changes **where the env file comes from**, not how it is consumed or validated.

### 6.8 Outputs: the only bridge to GitHub

```hcl
output "instance_id"          { value = aws_instance.backend.id }
output "ecr_push_role_arn"    { value = aws_iam_role.github_ecr_push.arn }
output "deploy_role_arn"      { value = aws_iam_role.github_deploy.arn }
output "backend_url"          { value = "http://${aws_lb.backend.dns_name}" }
```

## 7. Pipeline extension

The dev `ci-backend.yml` gains a tag-path publish job (`publish-ecr-prod`) and a push-only deploy job (`deploy-existing-ec2-prod`), plus a release trigger. The deploy job mirrors the dev job exactly (validate step, bundle allowlist step, OIDC assume, SSM send, invocation polling). The release path differs from the dev path in four ways:

1. **Trigger:** a release tag, not a branch push.

   ```yaml
   on:
     push:
       branches: [dev]
       tags: ['v*']
   ```

Both prod jobs gate on `github.event_name == 'push' && startsWith(github.ref, 'refs/tags/v')`. The existing dev deploy job's condition (`refs/heads/dev`) is untouched. Optionally add `workflow_dispatch` with an input tag for break-fix releases.

2. **Prod publish is its own environment-scoped job:** `publish-ecr-prod` receives the built image from the `docker` job as a run artifact (never rebuilt), assumes the ECR push role, and publishes by digest. It carries `environment: backend-prod-publish` because the ECR push trust is the stable `environment:` subject (section 6.1) — a tag-name `ref` subject could never be exact-matched. The environment is restricted to `v*` tags and has no reviewers: publishing to ECR is not a production mutation, and the human gate stays on the deploy.

3. **Approval gate:** `environment: backend-prod` with required reviewers configured in GitHub (section 8). The deploy pauses until a reviewer approves — this is the single biggest dev/prod difference in the delivery flow.

4. **Health check:** `deploy.sh` already polls `http://127.0.0.1:8589/api/actuator/health` on the host and rolls back automatically on failure — in dev this was sufficient. The dev workflow's *additional* public-URL check existed only because the runner had to prove reachability through the security group, which is what forced `0.0.0.0/0` open. In prod the runner does **not** need a public path: keep the SSM-invocation status check (which includes `deploy.sh` stdout, hence the local health result), and optionally verify the CloudFront domain (`BACKEND_URL_PROD`, runbook section 6.5) from the runner — the same URL users hit.

Concurrency: its own group `deploy-existing-ec2-backend-prod`, `cancel-in-progress: false`, shared with the prod rollback workflow so a rollback and a deploy can never interleave on the host.

The `rollback-backend.yml` workflow is extended the same way: a prod target selection with the `backend-prod` environment (reviewers required) and the `_PROD` variables.

## 8. GitHub configuration (one scripted step, not a checklist)

Create the `backend-prod` environment in Settings with:

- required reviewers (at least two team members for prod);
- deployment branches/tags: `v*` tags only.

Also create `backend-prod-publish` (the publish job's environment — its OIDC subject is what the ECR push role trusts, section 6.1) with:

- no required reviewers (the human gate stays on `backend-prod`);
- deployment branches/tags: `v*` tags only.

Then set every variable from Terraform outputs — the values are derived, never retyped:

```bash
terraform -chdir=infra/terraform/backend-app-stack output -json > /tmp/tf-outputs.json

gh variable set AWS_ACCOUNT_ID  --body 402178232802
gh variable set AWS_REGION      --body ap-southeast-1
gh variable set ECR_REPOSITORY_PROD --body prod/blocbuy
gh variable set AWS_ECR_PUSH_ROLE_ARN_PROD \
  --body "$(jq -r .ecr_push_role_arn /tmp/tf-outputs.json)"

# environment backend-prod
gh variable set AWS_DEPLOY_ROLE_ARN_PROD --env backend-prod \
  --body "$(jq -r .deploy_role_arn /tmp/tf-outputs.json)"
gh variable set BACKEND_EC2_INSTANCE_ID_PROD --env backend-prod \
  --body "$(jq -r .instance_id /tmp/tf-outputs.json)"
# BACKEND_URL_PROD is NOT set here: it is the CloudFront domain, which only exists after the
# distribution is created (runbook section 6.5) — never the raw ALB URL.
gh variable set ENABLE_EC2_DEPLOY_PROD --body false   # flip to true at step 9.7, after the freeze rehearsal
```

No AWS access keys ever enter GitHub — OIDC issues short-lived credentials, exactly as in dev.

## 9. First production deployment drill

Ordered, with the freeze switch held closed until the last possible moment:

1. Seed **all** secrets into Parameter Store first (section 6.7) — the stack reads `ORIGIN_SECRET` at plan time and the user-data reads the first-boot keys at instance creation.
2. From the repository root, run `terraform -chdir=infra/terraform/backend-app-stack init -backend-config="bucket=blocbuy-tfstate-402178232802" -backend-config="region=ap-southeast-1"` and `terraform -chdir=infra/terraform/backend-app-stack plan -var-file=../envs/prod.tfvars -out=prod.tfplan`; review both trust policies, 8589 ingress, the CloudFront prefix list and the default-403/secret-header rule privately. The saved plan may contain the origin secret; share only a redacted action summary.
3. After the team's review, `terraform -chdir=infra/terraform/backend-app-stack apply prod.tfplan` creates IAM, ECR, SGs, ALB and the EC2 host; verify user-data separately after boot.
4. Verify from an admin workstation:

   ```bash
   aws ssm describe-instance-information \
      --filters Key=InstanceIds,Values=$(terraform -chdir=infra/terraform/backend-app-stack output -raw instance_id) \
     --region ap-southeast-1   # PingStatus must be Online
   aws ec2 describe-security-groups ...   # confirm 8589 allows only the ALB SG
   ```

5. Set the GitHub variables from `terraform output` (section 8) — `ENABLE_EC2_DEPLOY_PROD` stays `false` for now.
6. Create the prod CloudFront distribution and set `BACKEND_URL_PROD` to its domain (runbook section 6.5).
7. Freeze rehearsal: push a throwaway tag `v0.0.0-rc1` — the gates must run while nothing publishes and nothing deploys. Only then set `ENABLE_EC2_DEPLOY_PROD=true`.
8. Cut a release tag from the release branch: `git tag v0.1.0 && git push origin v0.1.0`.
9. Quality gates re-run on the tag; `publish-ecr-prod` publishes the image to `prod/blocbuy` by digest.
10. The `backend-prod` environment pauses for reviewer approval — approve it.
11. Inspect the `deploy-existing-ec2-prod` job summary: digest, SSM command ID, status; confirm the local health check passed inside the SSM stdout.
12. Verify end-to-end through the CloudFront domain:

    ```bash
    curl --fail --silent "https://<prod-dist-domain>/api/actuator/health"
    # Direct ALB access without the secret is blocked: prefix-list SG timeout/000, or listener 403.
    curl --max-time 5 -s -o /dev/null -w '%{http_code}\n' \
      "$(terraform -chdir=infra/terraform/backend-app-stack output -raw backend_url)/api/actuator/health"
    ```

13. Record the change in the team change log (environment creation is one Terraform PR plus one apply — the PR itself is most of the audit evidence).

## 10. Bringing the dev environment under Terraform (follow-up, optional)

The **dev backend** EC2/ALB resources predate Terraform and remain hand-managed; the separate dev **frontend** edge was imported into `frontend-stack` on 2026-09-25 with a zero-change plan (#161). If the team later adopts the dev backend:

1. add `envs/dev.tfvars` describing the dev environment as it exists (roles, `dev/blocbuy`, instance security group);
2. `terraform import` each resource into state — IAM roles first, then ECR, then the security group;
3. never import-and-change in the same PR: the first apply after import must be a no-op diff.

Until then, the two environments coexist safely because prod role names, repository, SG names, and the SSM parameter path all carry explicit `prod`/`Prod` markers.

## 11. Security invariants (unchanged from dev, restated for prod)

- No long-lived AWS credentials in GitHub; OIDC only, with exact-match `sub` — never wildcards.
- ECR publish and SSM deploy remain two separate roles; prod roles are separate from dev roles.
- `ssm:SendCommand` stays scoped to `AWS-RunShellScript` on exactly the prod instance ID; SSM Run Command executes as root — treat the deploy role as privileged.
- Application secrets live only in Parameter Store and `/etc/blocbuy/backend.env` (root:root 0600); never in GitHub, Terraform state, user-data, workflow logs, or SSM command parameters. Sole exception: `/blocbuy/prod/ORIGIN_SECRET` is rendered by Terraform into the ALB listener rule and therefore exists in state — accepted trade-off documented in section 6.3 (KMS-encrypted, public-access-blocked state bucket).
- PostgreSQL/Redis bind to loopback only; 8589 accepts traffic only from the ALB security group, and the ALB itself accepts the CloudFront managed prefix list only.
- The deployment bundle allowlist (`compose.yml` + the five lifecycle scripts including `fetch-env.sh`, enforced by `security.yml`) keeps applying and must go through the same review.
- Application rollback cannot reverse Flyway migrations; every prod migration must be backward-compatible.

## 12. IaC practices checklist (for the course report)

| Practice | Where it shows up |
|---|---|
| Declarative environment | Entire stack under `infra/terraform/`, reviewed as PRs |
| Idempotent, re-runnable setup | `terraform plan/apply` replaces 60+ hand-run CLI commands |
| No out-of-band configuration drift | SG rules, IAM policies, and trust conditions only change via PR |
| Secrets out of code (and state, except `ORIGIN_SECRET`) | Parameter Store SecureString; Terraform never sees application-secret values — sole exception is `/blocbuy/prod/ORIGIN_SECRET`, read at plan time into the ALB rule and therefore in state (accepted trade-off, §6.3; KMS-encrypted, access-blocked bucket) |
| Immutable artifacts | Same digest-pinned ECR deployment as dev; tag-immutability enabled |
| Least privilege by reference | Inline policies reference resource ARNs (`aws_ecr_repository...arn`, `aws_instance...id`) instead of literals |
| Reproducibility | `envs/prod.tfvars` + pinned provider versions recreate the environment from scratch |
| Audit trail | PR review + redacted Terraform plan summary in PR comments (no raw secret-bearing plan/state) + CloudTrail + GitHub environment approvals |
| Disaster recovery | `terraform apply` rebuilds the host; ECR lifecycle retains rollback digests; state bucket is versioned |

## 13. Authoritative references

- [Terraform: AWS Provider](https://registry.terraform.io/providers/hashicorp/aws/latest/docs)
- [Terraform: S3 backend](https://developer.hashicorp.com/terraform/language/backend/s3)
- [GitHub: Using environments for deployment](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments)
- [AWS: IAM policy document data source](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/iam_policy_document)
- [AWS: SSM Parameter Store SecureString](https://docs.aws.amazon.com/systems-manager/latest/userguide/parameter-store-secure-strings.html)
- Dev delivery design: [backend-ec2-pipeline-operator-guide.md](../backend-ec2-pipeline-operator-guide.md)
- Dev first-deploy runbook: [backend-first-deploy-bootstrap.md](../backend-first-deploy-bootstrap.md)
