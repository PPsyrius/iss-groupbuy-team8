# BlocBuy Terraform

Two stacks use Terraform `~> 1.16.4` and independently lock AWS provider 5.100.0. Account holders run Terraform; CI never applies it.

| Stack | Ownership | S3 state key | Status |
|---|---|---|---|
| [`backend-app-stack/`](backend-app-stack/) | Production IAM/OIDC, ECR, ALB/security groups/target group, EC2 and first-boot bootstrap | `backend-app-stack/terraform.tfstate` | Read-only 2026-09-25 plan: 21 creates, no updates/destroys; **not applied** (#48). Managed-prefix-list lookup fix remains local. |
| [`frontend-stack/`](frontend-stack/README.md) | Existing dev frontend S3/OAC, CloudFront and deploy role | `frontend-stack/terraform.tfstate` | 9 imports, no live changes, post-import plan exit 0; source merged in PR #190. Later rotation used a local stable-ARN policy-reference fix awaiting review. |

The dev ALB/EC2 and GitHub environment are outside `frontend-stack`; no dev backend `envs/dev.tfvars` exists. Both stacks use separate keys in S3 bucket `blocbuy-tfstate-402178232802` (`ap-southeast-1`, encrypted) with DynamoDB `blocbuy-tflock` locking. DynamoDB locking is deprecated in Terraform 1.16; migrate it separately. Per-stack `.terraform.lock.hcl` pins the **provider**, not the CLI. Keep `.terraform/`, state, saved plans and secrets out of Git.

## Production backend

The [production design](../../docs/deploy/prod/backend-prod-terraform-deployment.md) and [operator runbook](../../docs/deploy/prod/backend-prod-operations-runbook.md) own prerequisites and review. After the account holder verified state storage, AMI and twelve SSM parameter names, a **private read-only plan** showed 21 creates with no updates/destroys; the plan/log were removed and no prod state was written. The managed-prefix-list fix is still local. Both production environments now allow only `v*` tags, without native reviewer rules; `ENABLE_EC2_DEPLOY_PROD` remains unset. Before apply or a release tag, review/merge the HCL and tag-only manual-rollback fixes and resolve the origin-TLS decision (NFR-37).

For offline checks from a fresh checkout, use `terraform -chdir=infra/terraform/backend-app-stack init -backend=false`, then `fmt -check` and `validate` with the same `-chdir`. For account-backed plans follow runbook §4; share only filtered actions, never the secret-bearing plan.

## Dev frontend

[`frontend-stack/README.md`](frontend-stack/README.md) owns private inputs and drift checks; the [dev CloudFront runbook](../../docs/deploy/dev-cloudfront-deployment.md) holds import/rotation evidence. The disclosed value was revoked, but its replacement still crosses HTTP: NFR-37 requires HTTPS and another rotation. Dev WAF/CMK exceptions are documented deferrals.
