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

## Static CI and policy findings (2026-10-25, #622)

Decision: [infra-swe5006.yml](../../.github/workflows/infra-swe5006.yml) checks both stacks on PRs into `dev` that touch `infra/terraform/**`, the referenced CloudFront files/ECR lifecycle policy, or the workflow/report tooling and its tests. It also supports manual dispatch. Each matrix job uses Terraform 1.16.4 for `fmt -check -recursive`, `init -backend=false -input=false -lockfile=readonly` and `validate`, then Checkov 3.3.22 for Terraform-only policy checks. Initialization downloads the locked provider; it does not initialize the S3 backend. These are static source checks, not a drift check or evidence of deployed settings. There is no AWS credential setup, OIDC permission, environment, plan, apply or state read; the dev EC2/ALB stays hand-managed.

Checkov uses `--soft-fail` deliberately during policy adoption: failed policy evaluations are advisory and appear by check/resource in the job log and Actions summary. The report validator still rejects parsing errors, malformed reports and scans with no resources; tool installation/execution failures and Terraform failures fail the job. No Checkov checks are suppressed by this change, and no new required ruleset check is introduced (this workflow is path-filtered). Existing Trivy policy and the six required merge checks are unchanged. A green job therefore means the static steps completed, not that all security policies passed.

Reproduce in a clean checkout without private variable files (repeat with `STACK=backend-app-stack`):

```bash
set -e
STACK=frontend-stack
REPORT=$(mktemp)
trap 'rm -f "$REPORT"' EXIT
terraform fmt -check -recursive "infra/terraform/$STACK"
terraform -chdir="infra/terraform/$STACK" init -backend=false -input=false -lockfile=readonly
terraform -chdir="infra/terraform/$STACK" validate
CHECKOV_SKIP_MAPPING=true pipx run 'checkov==3.3.22' -d "infra/terraform/$STACK" \
  --framework terraform --download-external-modules false \
  --skip-path '\.terraform' --soft-fail --output json > "$REPORT"
python3 .github/scripts/terraform_checkov_summary.py "$REPORT"
```

Keep the raw report outside the repository or remove it after review; do not scan/export private tfvars, state or plans. CI summarizes only finding identifiers, resources and names/reasons, not source snippets or variable values. Report validation and summary publication are separate commands under explicit Bash; a failed validator cannot be hidden by a successful pipe consumer. Tooling tests: `python3 -m unittest discover -s .github/tests -p '*_test.py'`; the Terraform tests execute the actual workflow shell block with a stubbed scanner to check failure propagation as well as advisory success (Bash required; Git Bash is supported on Windows).

### Initial Checkov inventory

Local source baseline on 25 October 2026, `dev` plus #622 workflow/tooling edits, using the pinned Checkov 3.3.22 Docker image: frontend 9 resources / 57 passed / 14 failed / 0 skipped; backend 21 resources / 108 passed / 15 failed / 0 skipped; no parsing errors. Both stacks passed Terraform 1.16.4 static checks with locked AWS provider 5.100.0. The PR checks record hosted results for their tested revision. Each row below is an open finding, not an approved risk acceptance; pre-existing tfsec comments are not Checkov suppressions. Runtime changes and cost/security trade-offs require separate owner review. Preserve this dated baseline and update dispositions when a fix or explicit suppression is reviewed.

#### Frontend stack — 14 failed evaluations

| Check | Resource | Finding | Disposition |
|---|---|---|---|
| CKV_AWS_259 | `aws_cloudfront_response_headers_policy.security` | HSTS enforcement | Open: review the policy loaded from JSON; the static finding alone does not establish absent deployed HSTS. |
| CKV_AWS_310 | `aws_cloudfront_distribution.dev` | No origin failover | Open: assess an appropriate secondary origin and availability requirement. |
| CKV_AWS_374 | `aws_cloudfront_distribution.dev` | No geographic restriction | Open: decide whether geographic blocking fits the intended users. |
| CKV_AWS_174 | `aws_cloudfront_distribution.dev` | Viewer certificate minimum below TLS 1.2 | Open: assess the default CloudFront certificate constraint before a custom-domain/TLS change. |
| CKV_AWS_68 | `aws_cloudfront_distribution.dev` | No WAF | Open: existing import preserves this configuration; review security/cost separately. |
| CKV_AWS_86 | `aws_cloudfront_distribution.dev` | No access logging | Open: choose logging destination, retention and privacy policy. |
| CKV_AWS_21 | `aws_s3_bucket.frontend` | No versioning | Open: assess recovery needs and storage cost. |
| CKV_AWS_144 | `aws_s3_bucket.frontend` | No cross-region replication | Open: assess recovery requirement, cost and regional guardrails. |
| CKV2_AWS_62 | `aws_s3_bucket.frontend` | No event notifications | Open: determine whether there is an operational consumer/use case. |
| CKV2_AWS_47 | `aws_cloudfront_distribution.dev` | No WAF managed Log4j rule | Open: consider with the WAF decision; not suppressed as a duplicate. |
| CKV_AWS_18 | `aws_s3_bucket.frontend` | No bucket access logging | Open: assess audit needs and logging destination. |
| CKV2_AWS_42 | `aws_cloudfront_distribution.dev` | No custom SSL certificate | Open: decide custom hostname/certificate requirements. |
| CKV_AWS_145 | `aws_s3_bucket.frontend` | No customer-managed KMS default encryption | Open: review existing encryption/cost decision; this is not a claim of unencrypted objects. |
| CKV2_AWS_61 | `aws_s3_bucket.frontend` | No lifecycle configuration | Open: assess safe expiration rules for deployed assets. |

#### Backend application stack — 15 failed evaluations (unapplied production design)

| Check | Resource | Finding | Disposition |
|---|---|---|---|
| CKV_AWS_126 | `aws_instance.backend` | No detailed monitoring | Open: assess required metrics and cost before any apply. |
| CKV_AWS_88 | `aws_instance.backend` | Public IP | Open: review the default-VPC/no-NAT design and private endpoint alternatives. |
| CKV_AWS_135 | `aws_instance.backend` | EBS optimization not explicit | Open: verify instance-type behavior and set/document the intended configuration. |
| CKV_AWS_136 | `aws_ecr_repository.backend` | No KMS encryption | Open: assess ECR encryption-key requirement and cost. |
| CKV_AWS_382 | `aws_security_group.alb` | Unrestricted outbound traffic | Open: review target-only egress; the host bootstrap rationale does not itself justify ALB egress. |
| CKV_AWS_382 | `aws_security_group.backend` | Unrestricted outbound traffic | Open: assess package/ECR/SSM connectivity and endpoint/egress restrictions. |
| CKV_AWS_300 | `aws_s3_bucket_lifecycle_configuration.alb_access_logs` | No incomplete multipart-upload cleanup | Open: add an appropriate abort interval in a reviewed infrastructure change. |
| CKV_AWS_150 | `aws_lb.backend` | No deletion protection | Open: balance accidental deletion protection against teardown requirements. |
| CKV_AWS_21 | `aws_s3_bucket.alb_access_logs` | No versioning | Open: assess audit-log integrity and cost. |
| CKV_AWS_144 | `aws_s3_bucket.alb_access_logs` | No cross-region replication | Open: assess audit recovery requirement and regional guardrails. |
| CKV_AWS_18 | `aws_s3_bucket.alb_access_logs` | No bucket access logging | Open: assess audit needs without recursive logging to the same bucket. |
| CKV2_AWS_62 | `aws_s3_bucket.alb_access_logs` | No event notifications | Open: establish whether a log-event consumer is needed. |
| CKV_AWS_145 | `aws_s3_bucket.alb_access_logs` | No customer-managed KMS default encryption | Open: evaluate ALB log-delivery compatibility and the existing SSE-S3 choice. |
| CKV_AWS_378 | `aws_lb_target_group.backend` | HTTP target protocol | Open: assess ALB-to-host TLS separately from CloudFront-to-ALB origin TLS (NFR-37). |
| CKV2_AWS_28 | `aws_lb.backend` | Public-facing ALB has no WAF | Open: review origin access controls and a WAF security/cost decision. |

## Dev frontend

[`frontend-stack/README.md`](frontend-stack/README.md) owns private inputs and drift checks; the [dev CloudFront runbook](../../docs/deploy/dev-cloudfront-deployment.md) holds import/rotation evidence. The disclosed value was revoked, but its replacement still crosses HTTP: NFR-37 requires HTTPS and another rotation. Dev WAF/CMK exceptions are documented deferrals.
