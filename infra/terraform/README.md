# BlocBuy Terraform

Infrastructure-as-code for the **production** backend environment: IAM (GitHub OIDC roles,
EC2 runtime role), ECR, security groups, ALB, and the EC2 instance with its first-boot
bootstrap. The application delivery pipeline is **not** Terraform-managed — Terraform
creates the environment, the pipeline only changes the running image.

Design: [backend-prod-terraform-deployment.md](../../docs/deploy/prod/backend-prod-terraform-deployment.md)
(authoritative) · Implementation plan:
[backend-prod-implementation-plan.md](../../docs/deploy/prod/backend-prod-implementation-plan.md) ·
Manual AWS/GitHub steps: [backend-prod-operations-runbook.md](../../docs/deploy/prod/backend-prod-operations-runbook.md)

## What this stack owns

| File | Contents |
|---|---|
| `backend-app-stack/github-oidc.tf` | OIDC provider lookup + `BlocBuyGitHubEcrPushProd` / `BlocBuyGitHubBackendDeployProd` roles (exact-match `sub`, no wildcards) |
| `backend-app-stack/ecr.tf` | `prod/blocbuy` ECR repository + shared lifecycle policy from `deployment/backend/` |
| `backend-app-stack/network.tf` | ALB/backend security groups, ALB, target group (health path `/api/actuator/health`), listener |
| `backend-app-stack/ec2.tf` | EC2 runtime role (SSM + ECR pull + `/blocbuy/prod/*` parameters), instance profile, instance |
| `backend-app-stack/templates/user-data.sh.tftpl` | First-boot bootstrap: docker, AWS CLI v2, loopback-only PostgreSQL/Redis Compose project |

The dev environment is **out of scope** (hand-managed until the team schedules the import —
design doc §10). `envs/dev.tfvars` is deliberately absent.

## State backend

State lives in S3 with DynamoDB locking (`blocbuy-tfstate-<account>`, `blocbuy-tflock`).
The bucket name is account-specific and passed at init time so no account identifier is
hardcoded in the stack. One-time bootstrap commands: runbook section 1.

## Usage

Never run `terraform apply` from CI — Terraform runs are human, reviewed, and one-time.

```bash
cd infra/terraform

# Initialize against the account's state backend (bucket created once, runbook §1)
terraform -chdir=backend-app-stack init \
  -backend-config bucket=blocbuy-tfstate-402178232802 \
  -backend-config region=ap-southeast-1

# Syntax + consistency check without touching the backend
terraform -chdir=backend-app-stack init -backend=false
terraform -chdir=backend-app-stack validate

# Plan (read-only) — attach the plan output to the PR for review
terraform -chdir=backend-app-stack plan -var-file=../envs/prod.tfvars -out=prod.tfplan

# Apply — after the runbook section 4 checklist (secrets seeded first!)
terraform -chdir=backend-app-stack apply prod.tfplan
```

`.terraform.lock.hcl` is committed: every workstation and re-run uses identical provider
versions.

## Before the first plan

1. Fill `envs/prod.tfvars`: pin the AMI (`ami_id`), confirm `release_branch` with the team.
2. Seed every `/blocbuy/prod/*` Parameter Store SecureString (runbook section 3) — the
   instance's user-data reads them at first boot, so they must exist **before** apply.
3. Review the rendered plan against the runbook section 4 checklist (trust policies,
   8589-from-ALB-only ingress, `/api/actuator/health` target-group path, IMDSv2).

## Outputs

`instance_id`, `ecr_push_role_arn`, `deploy_role_arn`, `backend_url`, `backend_sg_name`,
`target_group_arn` — consumed by the runbook's `gh variable set` step (section 6). No
other bridge to GitHub exists.
