# Dev frontend edge import (#161)

This stack owns the existing dev S3 bucket, bucket policy and public-access block, CloudFront OAC, SPA function, response-headers policy and distribution, plus the GitHub frontend deploy role and its inline policy. The account holder adopted nine resources into remote Terraform state on 2026-09-25 with no live changes and a zero-change post-import plan. The configuration has not yet been merged into the repository. The stack does not own the ALB, the GitHub environment or the account-wide OIDC provider.

## Prerequisites (account holder)

The account holder confirmed in #161 the S3 state bucket `blocbuy-tfstate-402178232802`, DynamoDB lock table `blocbuy-tflock`, region `ap-southeast-1` and separate key `frontend-stack/terraform.tfstate`. Use an AdministratorAccess Identity Center session for account `402178232802`; verify the account with `aws sts get-caller-identity`. Both stacks require Terraform **`~> 1.16.4`**; the account holder ran v1.16.4 on CloudShell (`x86_64`), with its archive checksum not shared. Local Docker Terraform 1.16.4 `fmt -check`, `init -backend=false` and `validate` passed; the frontend `.terraform.lock.hcl` pins AWS provider 5.100.0. CloudShell remote S3 `init -lockfile=readonly -input=false`, `fmt -check` and `validate -no-color` passed before the reviewed import-only apply. DynamoDB locking is deprecated but supported and matches the existing table until a separate backend migration is reviewed. Never commit `.terraform/`, a saved plan, state, the origin secret or AWS CLI output containing it.

`envs/frontend-dev.tfvars` mirrors the production tfvars' account/region and immutable GitHub owner/repository IDs for the exact-match OIDC role subject, followed by the existing dev OAC ID and ALB DNS. It does not include production-only AMI/ECR inputs or secrets. Recheck non-secret IDs against the live account before planning. Read the current CloudFront origin secret through an approved, private channel; set only `TF_VAR_origin_secret` in your local shell without writing it to a file or issue. The variable remains `sensitive`, but AWS provider 5.100.0 hashes the CloudFront origin as a set: retaining Terraform's sensitive mark on the header causes a false update even when the before/after origins match. With account-holder approval, `cloudfront.tf` uses `nonsensitive` **only at the provider field** to make full origin drift detection possible. The resolved value remains in remote state and saved plans, and a future plan that changes the header can print it. Treat every full plan and its log as secret-bearing; share only filtered action summaries.

```bash
aws sts get-caller-identity --query Account --output text
aws cloudfront list-origin-access-controls --query "OriginAccessControlList.Items[?Name=='blocbuy-dev-frontend-oac'].[Id,Name]" --output table
aws elbv2 describe-load-balancers --names blocbuy-dev-backend --query 'LoadBalancers[0].DNSName' --output text
aws cloudfront get-distribution-config --id E3U5W7UWDL8LRV --query 'DistributionConfig.{Origins:Origins.Items[].{Id:Id,DomainName:DomainName,OriginAccessControlId:OriginAccessControlId},ViewerCertificate:ViewerCertificate,DefaultCacheBehavior:DefaultCacheBehavior,CacheBehaviors:CacheBehaviors.Items}' --output json
```

Before import, account-holder read-only checks matched the versioned headers policy, both behaviors, non-secret origins, OAC, S3 public-access block/policy and IAM role. AWS returned an empty `XSSProtection` object, but the import and post-import plans required no policy update. The live default certificate reports `TLSv1`/`vip`; the provider normalizes `vip` out of imported state, so the HCL omits it despite the historical bootstrap JSON's `TLSv1.2_2021`. Both DEVELOPMENT and LIVE function code matched the versioned file (`git hash-object` result `af347accf5d62eaf9c6b5515ddc8621e1edcf3dc`); recheck stages before code changes, because this import did not publish a new LIVE version. The bucket policy's SourceArn matched the distribution ARN. No bucket lifecycle rule was introduced: `--delete` remains in the frontend sync and rollback redeploys a known-good artifact (ADR 0004). Do not print the ALB custom-header value into a PR or log.

## CloudShell hand-off without a Git commit

If the reviewed working tree is not pushed, package only the following non-secret inputs from the repository root in local PowerShell. This explicit list excludes `.terraform/`, saved plans, state and local credentials; `deployment/frontend/cloudfront/` must be present because the HCL reads the versioned function and policy using `path.module`.

```powershell
tar -a -cf frontend-import.zip `
  infra/terraform/frontend-stack/backend.tf `
  infra/terraform/frontend-stack/versions.tf `
  infra/terraform/frontend-stack/main.tf `
  infra/terraform/frontend-stack/variables.tf `
  infra/terraform/frontend-stack/outputs.tf `
  infra/terraform/frontend-stack/s3.tf `
  infra/terraform/frontend-stack/cloudfront.tf `
  infra/terraform/frontend-stack/iam.tf `
  infra/terraform/frontend-stack/imports.tf `
  infra/terraform/frontend-stack/.terraform.lock.hcl `
  infra/terraform/envs/frontend-dev.tfvars `
  deployment/frontend/cloudfront/spa-fallback.js `
  deployment/frontend/cloudfront/response-headers-policy.json
tar -tf frontend-import.zip
```

Upload `frontend-import.zip` using **CloudShell → Actions → Upload file**, then in CloudShell run `mkdir -p "$HOME/blocbuy-import" && unzip -q "$HOME/frontend-import.zip" -d "$HOME/blocbuy-import"`. Verify the extracted paths before running Terraform. A ZIP from a Windows checkout may contain CRLF in the function source even though the committed Git blob uses LF; normalize only the extracted function with `sed -i 's/\r$//' "$HOME/blocbuy-import/deployment/frontend/cloudfront/spa-fallback.js"` and compare its `git hash-object` with the LIVE/DEVELOPMENT hash recorded below. This was a temporary no-commit hand-off; **the original ZIP predates the final `cloudfront.tf` corrections** made in both the local working tree and CloudShell, so do not reuse that ZIP as the authoritative source. Preserve the final local configuration, provider lockfile and CloudShell state access until the human-reviewed PR lands. Clean up the secret-bearing CloudShell plan/log files when evidence has been recorded; never upload them or remote state to GitHub.

In the private CloudShell session, capture the existing origin secret directly into a shell variable without displaying or saving it to a file. Do not paste the value into a command line, issue, PR or chat:

```bash
TF_VAR_origin_secret="$(aws cloudfront get-distribution-config --id E3U5W7UWDL8LRV --query "DistributionConfig.Origins.Items[?Id=='alb-backend'].CustomHeaders.Items[0].HeaderValue | [0]" --output text)"
if [ -z "$TF_VAR_origin_secret" ] || [ "$TF_VAR_origin_secret" = None ]; then
  unset TF_VAR_origin_secret
  echo 'Origin secret not found; stop before planning' >&2
else
  export TF_VAR_origin_secret
fi
```

Do not run a plan unless the variable was exported successfully. Unset it with `unset TF_VAR_origin_secret` when finished; never copy the plan or remote state out of the authorized environment.

## Import evidence and subsequent drift checks

For subsequent drift checks, run these from the repository root (or the extracted `~/blocbuy-import` root) in an authorized shell with the **current** `TF_VAR_origin_secret` set privately; re-check the account before every run:

```bash
terraform -chdir=infra/terraform/frontend-stack init -backend-config="bucket=blocbuy-tfstate-402178232802" -backend-config="region=ap-southeast-1" -lockfile=readonly -input=false
terraform -chdir=infra/terraform/frontend-stack fmt -check
terraform -chdir=infra/terraform/frontend-stack validate
terraform -chdir=infra/terraform/frontend-stack plan -detailed-exitcode -input=false -var-file=../envs/frontend-dev.tfvars
```

**2026-09-25 acceptance (#161):** the first CloudShell plan exposed the existing origin custom-header value in a shared diff and proposed four live updates; subsequent private plans diagnosed provider normalization and the function's default-true virtual `publish` field. The account holder approved declassifying the sensitive input only at the provider field rather than ignoring all origin drift. The fourth private plan exited 0 with nine no-op imports and zero creates/updates/deletes; after explicit authorization the account holder applied **that saved plan**: `Apply complete! Resources: 9 imported, 0 added, 0 changed, 0 destroyed.` The immediate post-import `terraform plan -detailed-exitcode -input=false -no-color -var-file=../envs/frontend-dev.tfvars` exited **0** with `No changes. Your infrastructure matches the configuration.` No live resources changed; only encrypted remote state was written. Plan and state output remain secret-bearing, and rotation of the disclosed origin header is separately required. Function `publish = false` with `ignore_changes = [publish]` avoids publishing during import; future LIVE code needs a separate reviewed publish. The import was performed from an uncommitted, privately uploaded CloudShell copy; the original ZIP is stale, so keep the final local HCL and provider lockfile until a human-reviewed PR lands. The original `plan -generate-config-out` task proposed generating declarations, but the declared resources passed live zero-change verification without generated output.

Exit **0** is the drift gate (exit 2 is a change, exit 1 an error). Review full plans only inside the authorized account-holder environment and share only filtered action summaries. Do not replay the import plan or apply changed-resource plans as part of the completed import; follow a separate review for future modifications.

The account holder observed post-import exit **0** with zero changes. Summarize the nine imports, zero live actions and post-import result in the human PR, without attaching raw plan output or state. Keep the import blocks for reconstruction; Terraform skips them for resources already in state. Never run `terraform apply` from CI.

## Existing dev controls and Trivy exceptions

The existing private dev S3 bucket uses SSE-S3 (`AES256`) and public-access blocking, not a customer-managed KMS key; the live CloudFront distribution has no AWS WAF. The import deliberately made no live changes. Trivy's HIGH `AWS-0132` (S3 CMK) and `AWS-0011` (CloudFront WAF) findings are therefore narrowly annotated at the two imported resources, with reasons at the enforcement points. These are **not** equivalent controls: a CMK would need an explicit key and reviewed delivery/OAC permissions, while WAF needs its own security/cost decision. Revisit both in a separately reviewed live change instead of weakening the repository-wide HIGH/CRITICAL scan or silently changing the deployed edge during #161.
