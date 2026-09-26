# Dev frontend Terraform stack

This stack owns nine existing dev resources: the private S3 bucket, policy and public-access block; CloudFront OAC, SPA function, headers policy and distribution; and the GitHub deploy role and inline policy. They were imported into separate remote state without live changes on 2026-09-25 (PR #190). The dev backend ALB, account-wide OIDC provider and GitHub environment are outside this stack. The [dev entry-layer runbook](../../../docs/deploy/dev-cloudfront-deployment.md) records the import, rotation and operational probes.

## State and inputs

Use Terraform `~> 1.16.4` and the committed AWS 5.100.0 provider lockfile. State uses bucket `blocbuy-tfstate-402178232802`, key `frontend-stack/terraform.tfstate`, region `ap-southeast-1`, S3 encryption and the existing `blocbuy-tflock` DynamoDB table (deprecated as a Terraform backend option, still supported). `../envs/frontend-dev.tfvars` contains only non-secret account/repository IDs, OAC ID and ALB DNS. Supply the **current** `TF_VAR_origin_secret` privately; never commit it, Terraform state, saved plans or logs.

The distribution's `origin` is a provider set: marking its custom-header value sensitive forces a false whole-origin diff. The account holder approved `nonsensitive(var.origin_secret)` **only at that provider field** so origin drift remains managed. A changed-header plan can display the value; review plans in the authorized workspace and share only filtered actions. The function keeps `publish = false` with `ignore_changes = [publish]` because the provider defaults that virtual flag to true but does not read it back on import; publishing new LIVE function code needs separate review.

## Drift check (no apply)

Run from the repository root, confirming AWS account `402178232802` first. The versioned CloudFront function and policy under `deployment/frontend/cloudfront/` must accompany this stack. Capture the existing header into the shell without displaying it; stop if it is missing:

```bash
aws sts get-caller-identity --query Account --output text
set +x
TF_VAR_origin_secret="$(aws cloudfront get-distribution-config --id E3U5W7UWDL8LRV --query "DistributionConfig.Origins.Items[?Id=='alb-backend'].CustomHeaders.Items[0].HeaderValue | [0]" --output text)"
if [ -n "$TF_VAR_origin_secret" ] && [ "$TF_VAR_origin_secret" != None ]; then export TF_VAR_origin_secret; else unset TF_VAR_origin_secret; echo 'Origin secret unavailable; stop' >&2; exit 1; fi
terraform -chdir=infra/terraform/frontend-stack init -backend-config="bucket=blocbuy-tfstate-402178232802" -backend-config="region=ap-southeast-1" -lockfile=readonly -input=false
terraform -chdir=infra/terraform/frontend-stack fmt -check
terraform -chdir=infra/terraform/frontend-stack validate
umask 077
PRIVATE_LOG="$(mktemp "${TMPDIR:-/tmp}/blocbuy-frontend-plan.XXXXXX")"
terraform -chdir=infra/terraform/frontend-stack plan -detailed-exitcode -input=false -no-color -var-file=../envs/frontend-dev.tfvars > "$PRIVATE_LOG" 2>&1
echo "Plan exit code: $?"
# Review the log privately; never attach or paste it. Then: rm -- "$PRIVATE_LOG"; unset TF_VAR_origin_secret
```

Exit 0 means no drift, 2 a proposed change and 1 an error. Review any changes against the runbook before a separately authorized apply. Retain import blocks for reconstruction; Terraform skips resources already in state. Never apply Terraform from CI.

## Dated evidence and follow-ups

The import applied **9 imports, 0 added/changed/destroyed** and the post-import plan exited 0. After PR #190 merged, an account-holder rotation revoked the disclosed `X-Origin-Secret`: one reviewed CloudFront update, ALB old-value removal, API health `UP`, and a zero-change post-rotation plan. The verified CloudShell HCL used a stable distribution ARN for S3/IAM policy documents; that `main.tf`/`s3.tf`/`iam.tf` refactor remains local and must be reviewed/merged before replacing it with a fresh checkout. No secret or raw plan belongs in the PR.

The origin remains HTTP; NFR-37 requires an origin hostname, regional certificate, HTTPS cutover and another rotation. The account holder retained dev-only Trivy `AWS-0011` (no WAF) and `AWS-0132` (SSE-S3 rather than CMK) exceptions because no measured WAF need or customer-key requirement is agreed. The annotations are scoped to those imported resources; revisit WAF on measured abuse and CMK on key-ownership/data-classification requirements rather than disabling the HIGH/CRITICAL gate.
