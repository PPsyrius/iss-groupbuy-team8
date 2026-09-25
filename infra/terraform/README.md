# BlocBuy Terraform

Two independent stacks share an AWS provider constraint and Terraform `~> 1.16.4` (1.16.4 or a later 1.16 patch). **`backend-app-stack/`** describes the production backend; **`frontend-stack/`** owns the already-live dev frontend edge after its 2026-09-25 state import and zero-change plan (#161). The frontend configuration is still unmerged and needs a human PR before it is durably available to other operators. Terraform is run by an authorized human, never applied from CI. Delivery pipelines change application artifacts, not Terraform infrastructure.

| Stack | Owns | Remote state key | Current status |
|---|---|---|---|
| `backend-app-stack/` | Production OIDC roles, ECR, ALB/security groups/target group, EC2 and first-boot bootstrap | `backend-app-stack/terraform.tfstate` | Code exists; production landing steps remain in [#48](https://github.com/PPsyrius/iss-groupbuy-team8/issues/48) |
| `frontend-stack/` | Existing dev frontend S3 bucket/policy/public-access block, CloudFront OAC/function/headers policy/distribution and deploy role/inline policy | `frontend-stack/terraform.tfstate` | Terraform-managed in remote state: 9 imported, 0 live changes, post-import plan exit 0 (2026-09-25); source PR not merged |

The frontend stack does not own the dev backend ALB, account-wide OIDC provider or GitHub environment. The production backend stack does not own the dev frontend. No dev backend `envs/dev.tfvars` exists.

## Remote state and versions

Both stacks use the account holder's `blocbuy-tfstate-402178232802` bucket in `ap-southeast-1`, with `encrypt = true` and the existing DynamoDB `blocbuy-tflock` table; the keys above keep their states separate. The account holder confirmed the frontend backend settings in #161. DynamoDB locking is deprecated in Terraform 1.16 but still supported here; a migration to S3 lockfiles is a separate reviewed change. Backend config stores the stack-specific key, lock table and encryption; pass the account-specific bucket and region at `init`. `.terraform.lock.hcl` locks the AWS provider version per stack, **not** the Terraform CLI; offline Terraform 1.16.4 init/validate passed for both, pinned to AWS 5.100.0. The backend lockfile now records both Windows and Linux checksums. Never commit `.terraform/`, `*.tfstate`, `*.tfplan` or sensitive tfvars.

## Production backend stack

Read the [production Terraform design](../../docs/deploy/prod/backend-prod-terraform-deployment.md), [implementation plan](../../docs/deploy/prod/backend-prod-implementation-plan.md) and [operator runbook](../../docs/deploy/prod/backend-prod-operations-runbook.md) before planning or applying. The production stack also includes `backend-app-stack/templates/user-data.sh.tftpl`, which boots Docker, AWS CLI and loopback-only PostgreSQL/Redis. Its `github-oidc.tf` looks up the existing GitHub provider and defines exact-subject release/deploy roles; `network.tf` gates the ALB behind CloudFront's prefix list and `X-Origin-Secret` while limiting backend port 8589 to the ALB security group.

From the repository root, after the production runbook's prerequisites and secret seeding (§§0–3), use its §4 checklist to review the plan before applying:

```bash
terraform -chdir=infra/terraform/backend-app-stack init -backend-config="bucket=blocbuy-tfstate-402178232802" -backend-config="region=ap-southeast-1"
terraform -chdir=infra/terraform/backend-app-stack fmt -check
terraform -chdir=infra/terraform/backend-app-stack validate
terraform -chdir=infra/terraform/backend-app-stack plan -var-file=../envs/prod.tfvars -out=prod.tfplan
```

Before planning, pin `ami_id` and confirm `release_branch` in `envs/prod.tfvars`; seed `/blocbuy/prod/*` SSM SecureStrings, including `ORIGIN_SECRET` read by the ALB listener at plan time. Review the local saved plan against the runbook's trust, security-group, target-health and IMDSv2 checklist before any human `terraform apply` of that plan. A saved plan can contain the resolved origin secret: keep it local and ignored; summarize the reviewed actions in the PR without attaching raw plan/state output. The backend outputs include the EC2 instance ID, release/deploy role ARNs, ALB DNS (`backend_url`, **not** `BACKEND_URL_PROD`), security-group name and target-group ARN for the runbook's GitHub hand-off. No production plan or apply is claimed here.

## Dev frontend import

Use [`frontend-stack/README.md`](frontend-stack/README.md) for the nine imported resources, confirmed non-secret IDs, private `TF_VAR_origin_secret` input and future plan procedure. The [dev entry-layer runbook](../../docs/deploy/dev-cloudfront-deployment.md) records the account-holder review, import-only apply and zero-change acceptance evidence. Terraform state adoption does not merge source code: until a human lands the PR, keep the exact configuration and provider lockfile available, and do not create parallel edits from a different snapshot. Coordinate rotation of the origin secret disclosed by an earlier shared plan separately; never attach raw plans or state to the PR.
